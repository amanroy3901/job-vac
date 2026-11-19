package appstore

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// TestSigner mimics Apple's signing PKI for tests and local development: a throwaway root, an intermediate with
// the Apple intermediate marker OID and a leaf with the leaf marker OID. Verifiers trust it only when its Roots()
// pool is configured explicitly; production always uses AppleRoots().
type TestSigner struct {
	root, inter, leaf *x509.Certificate
	leafKey           *ecdsa.PrivateKey
}

// NewTestSigner builds a fresh chain valid from now-1y to now+1y.
func NewTestSigner() (*TestSigner, error) {
	now := time.Now()
	mk := func(cn string, parent *x509.Certificate, parentKey *ecdsa.PrivateKey, ca bool, ext *pkixExt) (*x509.Certificate, *ecdsa.PrivateKey, error) {
		k, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		if err != nil {
			return nil, nil, err
		}
		sn, _ := rand.Int(rand.Reader, big.NewInt(1<<62))
		tpl := &x509.Certificate{SerialNumber: sn, Subject: pkix.Name{CommonName: cn},
			NotBefore: now.AddDate(-1, 0, 0), NotAfter: now.AddDate(1, 0, 0), BasicConstraintsValid: true, IsCA: ca,
			KeyUsage: x509.KeyUsageDigitalSignature}
		if ca {
			tpl.KeyUsage |= x509.KeyUsageCertSign
		}
		if ext != nil {
			tpl.ExtraExtensions = []pkix.Extension{{Id: ext.oid, Value: []byte{0x05, 0x00}}}
		}
		if parent == nil {
			parent, parentKey = tpl, k
		}
		der, err := x509.CreateCertificate(rand.Reader, tpl, parent, &k.PublicKey, parentKey)
		if err != nil {
			return nil, nil, err
		}
		c, err := x509.ParseCertificate(der)
		return c, k, err
	}
	root, rootKey, err := mk("Test Root CA - G3", nil, nil, true, nil)
	if err != nil {
		return nil, err
	}
	inter, interKey, err := mk("Test WWDR CA", root, rootKey, true, &pkixExt{oidAppleIntermediate})
	if err != nil {
		return nil, err
	}
	leaf, leafKey, err := mk("Test StoreKit Signing", inter, interKey, false, &pkixExt{oidAppleLeaf})
	if err != nil {
		return nil, err
	}
	return &TestSigner{root: root, inter: inter, leaf: leaf, leafKey: leafKey}, nil
}

type pkixExt struct{ oid []int }

// Roots returns a pool trusting only the test root.
func (s *TestSigner) Roots() *x509.CertPool {
	p := x509.NewCertPool()
	p.AddCert(s.root)
	return p
}

// Sign returns a compact JWS of payload (any JSON-marshallable value) with the test x5c chain.
func (s *TestSigner) Sign(payload any) string {
	b, err := json.Marshal(payload)
	if err != nil {
		panic(err)
	}
	var claims jwt.MapClaims
	if err := json.Unmarshal(b, &claims); err != nil {
		panic(err)
	}
	t := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
	t.Header["x5c"] = []string{base64.StdEncoding.EncodeToString(s.leaf.Raw),
		base64.StdEncoding.EncodeToString(s.inter.Raw), base64.StdEncoding.EncodeToString(s.root.Raw)}
	out, err := t.SignedString(s.leafKey)
	if err != nil {
		panic(err)
	}
	return out
}
