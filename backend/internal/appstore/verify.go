// Package appstore verifies App Store signed data (StoreKit 2 JWS transactions and renewal info, App Store Server
// Notifications V2) and calls the App Store Server API.
//
// Verification follows Apple's own App Store Server Library (SignedDataVerifier, MIT licensed):
//   - the JWS header is ES256 with an `x5c` chain of exactly three DER certificates (leaf, intermediate, root);
//   - the chain must verify to **Apple Root CA - G3** (embedded below, never taken from the JWS), the leaf must carry
//     the extension OID 1.2.840.113635.100.6.11.1 and the intermediate OID 1.2.840.113635.100.6.2.1;
//   - certificate validity is checked at the payload's `signedDate` (Apple's library does this when online
//     revocation checks are off, as here) — so a JWS signed while the leaf was valid stays verifiable;
//   - the signature over `header.payload` is checked with the leaf's P-256 key;
//   - then bundle id, environment and, for Production notifications, appAppleId are checked.
//
// Xcode / LocalTesting environments are signed by a local Xcode certificate, not Apple's PKI: Apple's library
// decodes them without chain verification; here that is allowed only with Verifier.AllowLocalTesting (dev only,
// refused by config in prod).
//
// Sources: https://developer.apple.com/documentation/appstoreserverapi/jwsdecodedheader ,
// https://www.apple.com/certificateauthority/ (Apple PKI), @apple/app-store-server-library 3.1.0
// dist/jws_verification.js (verifyCertificateChainWithoutCaching, effectiveDate = signedDate).
package appstore

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/x509"
	_ "embed"
	"encoding/asn1"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// appleRootCAG3PEM is Apple Root CA - G3 (ECC P-384, valid 2014-04-30 → 2039-04-30),
// https://www.apple.com/certificateauthority/AppleRootCA-G3.cer converted with `openssl x509 -inform der`.
// SHA-256 fingerprint 63:34:3A:BF:B8:9A:6A:03:EB:B5:7E:9B:3F:5F:A7:BE:7C:4F:5C:75:6F:30:17:B3:A8:C4:88:C3:65:3E:91:79,
// as published in Apple's trusted-root list (https://support.apple.com/en-us/105116); a unit test pins it.
//
//go:embed AppleRootCA-G3.pem
var appleRootCAG3PEM []byte

// AppleRootCAG3SHA256 is the pinned fingerprint of the embedded root.
const AppleRootCAG3SHA256 = "63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179"

// Apple marker extensions (Apple's SignedDataVerifier checks both).
var (
	oidAppleLeaf         = asn1.ObjectIdentifier{1, 2, 840, 113635, 100, 6, 11, 1}
	oidAppleIntermediate = asn1.ObjectIdentifier{1, 2, 840, 113635, 100, 6, 2, 1}
)

// Environments as they appear in signed payloads.
const (
	EnvProduction   = "Production"
	EnvSandbox      = "Sandbox"
	EnvXcode        = "Xcode"
	EnvLocalTesting = "LocalTesting"
)

// Errors. ErrInvalid covers malformed JWS, bad chains and bad signatures.
var (
	ErrInvalid     = errors.New("appstore: invalid signed data")
	ErrWrongApp    = errors.New("appstore: bundle id or app apple id mismatch")
	ErrEnvironment = errors.New("appstore: environment not accepted")
)

// AppleRoots returns a pool with Apple Root CA - G3.
func AppleRoots() *x509.CertPool {
	p := x509.NewCertPool()
	if !p.AppendCertsFromPEM(appleRootCAG3PEM) {
		panic("appstore: embedded Apple root does not parse")
	}
	return p
}

// AppleRootPEM exposes the embedded root (tests, docs).
func AppleRootPEM() []byte { return slices.Clone(appleRootCAG3PEM) }

// Verifier verifies and decodes App Store signed data for one app.
type Verifier struct {
	Roots    *x509.CertPool // nil = AppleRoots()
	BundleID string
	// AppAppleID is the app's Apple ID (App Store Connect → App Information). Required to accept Production
	// notifications (Apple's library refuses Production without it).
	AppAppleID int64
	// Environments accepted for transactions and notifications, e.g. Production + Sandbox (App Review and
	// TestFlight purchase in Sandbox against the production build, so production servers accept Sandbox and flag
	// those purchases test_purchase).
	Environments []string
	// AllowLocalTesting decodes Xcode/LocalTesting payloads without chain verification (local development only).
	AllowLocalTesting bool
	Now               func() time.Time
}

func (v *Verifier) roots() *x509.CertPool {
	if v.Roots != nil {
		return v.Roots
	}
	return AppleRoots()
}

func (v *Verifier) envOK(env string) bool {
	if env == EnvXcode || env == EnvLocalTesting {
		return v.AllowLocalTesting
	}
	return slices.Contains(v.Environments, env)
}

// VerifyTransaction verifies a JWSTransaction (StoreKit 2 `Transaction.jwsRepresentation`, or
// signedTransactionInfo from a notification / the Server API).
func (v *Verifier) VerifyTransaction(raw string) (*Transaction, error) {
	var t Transaction
	if err := v.verify(raw, &t); err != nil {
		return nil, err
	}
	if t.BundleID != v.BundleID {
		return nil, fmt.Errorf("%w: bundleId %q", ErrWrongApp, t.BundleID)
	}
	if !v.envOK(t.Environment) {
		return nil, fmt.Errorf("%w: %q", ErrEnvironment, t.Environment)
	}
	if t.OriginalTransactionID == "" || t.TransactionID == "" || t.ProductID == "" {
		return nil, fmt.Errorf("%w: missing transaction ids", ErrInvalid)
	}
	return &t, nil
}

// VerifyRenewalInfo verifies a JWSRenewalInfo (signedRenewalInfo).
func (v *Verifier) VerifyRenewalInfo(raw string) (*RenewalInfo, error) {
	var r RenewalInfo
	if err := v.verify(raw, &r); err != nil {
		return nil, err
	}
	if !v.envOK(r.Environment) {
		return nil, fmt.Errorf("%w: %q", ErrEnvironment, r.Environment)
	}
	return &r, nil
}

// VerifyNotification verifies an App Store Server Notifications V2 `signedPayload`.
func (v *Verifier) VerifyNotification(raw string) (*Notification, error) {
	var n Notification
	if err := v.verify(raw, &n); err != nil {
		return nil, err
	}
	var bundle, env string
	var appID int64
	switch {
	case n.Data != nil:
		bundle, env, appID = n.Data.BundleID, n.Data.Environment, n.Data.AppAppleID
	case n.Summary != nil:
		bundle, env, appID = n.Summary.BundleID, n.Summary.Environment, n.Summary.AppAppleID
	default: // externalPurchaseToken etc.: nothing we act on; signature already verified
		return &n, nil
	}
	if bundle != v.BundleID || (env == EnvProduction && appID != v.AppAppleID) {
		return nil, fmt.Errorf("%w: bundleId %q appAppleId %d", ErrWrongApp, bundle, appID)
	}
	if !v.envOK(env) {
		return nil, fmt.Errorf("%w: %q", ErrEnvironment, env)
	}
	return &n, nil
}

type jwsHeader struct {
	Alg string   `json:"alg"`
	X5c []string `json:"x5c"`
}

// verify checks the chain and signature of raw and decodes its payload into dst.
func (v *Verifier) verify(raw string, dst any) error {
	parts := strings.Split(raw, ".")
	if len(parts) != 3 {
		return fmt.Errorf("%w: not a compact JWS", ErrInvalid)
	}
	hb, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return fmt.Errorf("%w: header encoding", ErrInvalid)
	}
	pb, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return fmt.Errorf("%w: payload encoding", ErrInvalid)
	}
	var meta struct {
		SignedDate  int64  `json:"signedDate"`
		Environment string `json:"environment"`
		Data        *struct {
			Environment string `json:"environment"`
		} `json:"data"`
	}
	if err := json.Unmarshal(pb, &meta); err != nil {
		return fmt.Errorf("%w: payload json", ErrInvalid)
	}
	env := meta.Environment
	if meta.Data != nil {
		env = meta.Data.Environment
	}
	if (env == EnvXcode || env == EnvLocalTesting) && v.AllowLocalTesting {
		return json.Unmarshal(pb, dst) // Apple's library skips verification for these environments too
	}
	var h jwsHeader
	if err := json.Unmarshal(hb, &h); err != nil {
		return fmt.Errorf("%w: header json", ErrInvalid)
	}
	if h.Alg != "ES256" {
		return fmt.Errorf("%w: alg %q", ErrInvalid, h.Alg)
	}
	if len(h.X5c) != 3 {
		return fmt.Errorf("%w: x5c must hold 3 certificates, got %d", ErrInvalid, len(h.X5c))
	}
	certs := make([]*x509.Certificate, 2)
	for i := range certs { // the third (root) is ignored: trust comes only from v.roots()
		der, err := base64.StdEncoding.DecodeString(h.X5c[i])
		if err != nil {
			return fmt.Errorf("%w: x5c[%d] encoding", ErrInvalid, i)
		}
		if certs[i], err = x509.ParseCertificate(der); err != nil {
			return fmt.Errorf("%w: x5c[%d]: %v", ErrInvalid, i, err)
		}
	}
	leaf, inter := certs[0], certs[1]
	if !hasExt(leaf, oidAppleLeaf) || !hasExt(inter, oidAppleIntermediate) {
		return fmt.Errorf("%w: missing Apple certificate marker extensions", ErrInvalid)
	}
	at := v.now()
	if meta.SignedDate > 0 {
		at = Millis(meta.SignedDate)
	}
	ip := x509.NewCertPool()
	ip.AddCert(inter)
	if _, err := leaf.Verify(x509.VerifyOptions{Roots: v.roots(), Intermediates: ip, CurrentTime: at,
		KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageAny}}); err != nil {
		return fmt.Errorf("%w: chain: %v", ErrInvalid, err)
	}
	if err := leaf.CheckSignatureFrom(inter); err != nil {
		return fmt.Errorf("%w: leaf not issued by x5c[1]: %v", ErrInvalid, err)
	}
	pub, ok := leaf.PublicKey.(*ecdsa.PublicKey)
	if !ok || pub.Curve != elliptic.P256() {
		return fmt.Errorf("%w: leaf key is not P-256", ErrInvalid)
	}
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return fmt.Errorf("%w: signature encoding", ErrInvalid)
	}
	if err := jwt.SigningMethodES256.Verify(parts[0]+"."+parts[1], sig, pub); err != nil {
		return fmt.Errorf("%w: signature: %v", ErrInvalid, err)
	}
	if err := json.Unmarshal(pb, dst); err != nil {
		return fmt.Errorf("%w: payload: %v", ErrInvalid, err)
	}
	return nil
}

func hasExt(c *x509.Certificate, oid asn1.ObjectIdentifier) bool {
	for _, e := range c.Extensions {
		if e.Id.Equal(oid) {
			return true
		}
	}
	return false
}

func (v *Verifier) now() time.Time {
	if v.Now != nil {
		return v.Now()
	}
	return time.Now()
}

// Millis converts Apple's epoch-millisecond timestamps (0 → zero time).
func Millis(ms int64) time.Time {
	if ms == 0 {
		return time.Time{}
	}
	return time.UnixMilli(ms).UTC()
}
