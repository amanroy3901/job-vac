// Package seal encrypts small secrets at rest (Play purchase tokens, transient self-test FCM tokens) with
// AES-256-GCM. In prod the key is a Cloud KMS-wrapped DEK (security-privacy.md §3); TODO: KMS envelope.
package seal

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"fmt"
)

// Sealer encrypts and decrypts with one key.
type Sealer struct{ aead cipher.AEAD }

// New builds a Sealer from a base64 32-byte key. An empty key derives a fixed dev key (never in prod; config
// validation enforces SEAL_KEY_B64 there).
func New(keyB64 string) (*Sealer, error) {
	var key []byte
	if keyB64 == "" {
		k := sha256.Sum256([]byte("sarkari-match-dev-only-seal-key"))
		key = k[:]
	} else {
		var err error
		if key, err = base64.StdEncoding.DecodeString(keyB64); err != nil {
			return nil, fmt.Errorf("SEAL_KEY_B64: %w", err)
		}
	}
	if len(key) != 32 {
		return nil, errors.New("seal key must be 32 bytes")
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return &Sealer{aead: aead}, nil
}

// Seal returns nonce‖ciphertext‖tag.
func (s *Sealer) Seal(plain []byte) []byte {
	nonce := make([]byte, s.aead.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		panic(err)
	}
	return s.aead.Seal(nonce, nonce, plain, nil)
}

// Open reverses Seal.
func (s *Sealer) Open(sealed []byte) ([]byte, error) {
	n := s.aead.NonceSize()
	if len(sealed) < n {
		return nil, errors.New("sealed value too short")
	}
	return s.aead.Open(nil, sealed[:n], sealed[n:], nil)
}

// Hash is the lookup hash used for token columns (sha256).
func Hash(v string) []byte { h := sha256.Sum256([]byte(v)); return h[:] }
