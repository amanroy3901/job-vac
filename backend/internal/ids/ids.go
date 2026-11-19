// Package ids generates prefixed ULIDs ("u_", "n_", "p_", "f_" ...) as used across the schema and API.
package ids

import (
	"crypto/rand"
	"encoding/base32"
	"strings"
	"time"

	"github.com/oklog/ulid/v2"
)

// New returns prefix + "_" + ULID, e.g. New("u") = "u_01JD3X5ZQ7...".
func New(prefix string) string {
	return prefix + "_" + ulid.MustNew(ulid.Timestamp(time.Now()), rand.Reader).String()
}

// Token returns n random bytes encoded as unpadded base32 (URL safe, case-insensitive), e.g. family invite tokens.
func Token(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return strings.ToLower(base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(b))
}

// ReferralCode returns an 8-character code matching ^[A-Z0-9]{6,10}$ without ambiguous characters.
func ReferralCode() string {
	const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	b := make([]byte, 8)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	for i := range b {
		b[i] = alphabet[int(b[i])%len(alphabet)]
	}
	return string(b)
}

// MatchIDForPost derives the device-side match id (api/README.md §1a): "m_" + post id without "p_".
func MatchIDForPost(postID string) string { return "m_" + strings.TrimPrefix(postID, "p_") }
