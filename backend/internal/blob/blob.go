// Package blob is the pluggable object store for public catalog files: a local directory (dev, tests) or a GCS
// bucket served by Firebase Hosting/CDN (prod).
package blob

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

// ErrNotFound is returned by Get for a missing object.
var ErrNotFound = errors.New("blob not found")

// Meta describes an object.
type Meta struct {
	ContentType  string
	CacheControl string
	ETag         string // quoted sha256 of the body
}

// Store writes and reads immutable objects by key ("catalog/batches/12/en,hi.json").
type Store interface {
	Put(ctx context.Context, key string, data []byte, m Meta) error
	Get(ctx context.Context, key string) ([]byte, Meta, error)
}

// ETag returns the strong ETag of data.
func ETag(data []byte) string {
	h := sha256.Sum256(data)
	return `"` + hex.EncodeToString(h[:]) + `"`
}

// Local stores objects under Dir with a sidecar .meta JSON.
type Local struct{ Dir string }

func (l Local) path(key string) (string, error) {
	clean := filepath.Clean("/" + key)
	if strings.Contains(key, "..") {
		return "", fmt.Errorf("bad key %q", key)
	}
	return filepath.Join(l.Dir, clean), nil
}

// Put implements Store (write to temp + rename, so readers never see a partial file).
func (l Local) Put(_ context.Context, key string, data []byte, m Meta) error {
	p, err := l.path(key)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
		return err
	}
	m.ETag = ETag(data)
	mb, _ := json.Marshal(m)
	if err := writeAtomic(p+".meta", mb); err != nil {
		return err
	}
	return writeAtomic(p, data)
}

func writeAtomic(p string, data []byte) error {
	tmp := p + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, p)
}

// Get implements Store.
func (l Local) Get(_ context.Context, key string) ([]byte, Meta, error) {
	p, err := l.path(key)
	if err != nil {
		return nil, Meta{}, err
	}
	data, err := os.ReadFile(p)
	if errors.Is(err, os.ErrNotExist) {
		return nil, Meta{}, ErrNotFound
	}
	if err != nil {
		return nil, Meta{}, err
	}
	var m Meta
	if mb, err := os.ReadFile(p + ".meta"); err == nil {
		_ = json.Unmarshal(mb, &m)
	}
	if m.ETag == "" {
		m.ETag = ETag(data)
	}
	return data, m, nil
}

// GCS stores objects in a bucket via the JSON API (no SDK dependency). Endpoint can point at an emulator.
type GCS struct {
	Bucket   string
	Endpoint string // default https://storage.googleapis.com
	Client   *http.Client
}

// NewGCS uses Application Default Credentials (the Cloud Run service account).
func NewGCS(ctx context.Context, bucket string) (*GCS, error) {
	ts, err := google.DefaultTokenSource(ctx, "https://www.googleapis.com/auth/devstorage.read_write")
	if err != nil {
		return nil, err
	}
	return &GCS{Bucket: bucket, Client: oauth2.NewClient(ctx, ts)}, nil
}

func (g *GCS) endpoint() string {
	if g.Endpoint != "" {
		return strings.TrimRight(g.Endpoint, "/")
	}
	return "https://storage.googleapis.com"
}

// Put uploads with a multipart request so Content-Type and Cache-Control land in object metadata.
func (g *GCS) Put(ctx context.Context, key string, data []byte, m Meta) error {
	var body bytes.Buffer
	mw := multipart.NewWriter(&body)
	meta := map[string]any{"name": key, "contentType": m.ContentType, "cacheControl": m.CacheControl,
		"metadata": map[string]string{"sha256": strings.Trim(ETag(data), `"`)}}
	mh := textproto.MIMEHeader{"Content-Type": {"application/json; charset=UTF-8"}}
	pw, _ := mw.CreatePart(mh)
	_ = json.NewEncoder(pw).Encode(meta)
	dh := textproto.MIMEHeader{"Content-Type": {m.ContentType}}
	pw, _ = mw.CreatePart(dh)
	_, _ = pw.Write(data)
	_ = mw.Close()
	u := fmt.Sprintf("%s/upload/storage/v1/b/%s/o?uploadType=multipart", g.endpoint(), url.PathEscape(g.Bucket))
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u, &body)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "multipart/related; boundary="+mw.Boundary())
	resp, err := g.client().Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return fmt.Errorf("gcs put %s: HTTP %d %s", key, resp.StatusCode, b)
	}
	return nil
}

// Get downloads an object.
func (g *GCS) Get(ctx context.Context, key string) ([]byte, Meta, error) {
	u := fmt.Sprintf("%s/storage/v1/b/%s/o/%s?alt=media", g.endpoint(), url.PathEscape(g.Bucket), url.PathEscape(key))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, Meta{}, err
	}
	resp, err := g.client().Do(req)
	if err != nil {
		return nil, Meta{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusNotFound {
		return nil, Meta{}, ErrNotFound
	}
	if resp.StatusCode != http.StatusOK {
		return nil, Meta{}, fmt.Errorf("gcs get %s: HTTP %d", key, resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, 64<<20))
	if err != nil {
		return nil, Meta{}, err
	}
	return data, Meta{ContentType: resp.Header.Get("Content-Type"), CacheControl: resp.Header.Get("Cache-Control"),
		ETag: ETag(data)}, nil
}

func (g *GCS) client() *http.Client {
	if g.Client != nil {
		return g.Client
	}
	return &http.Client{Timeout: 30 * time.Second}
}
