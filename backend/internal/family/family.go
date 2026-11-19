// Package family relays family alerts (reminders-and-delivery.md §5): the aspirant (Pro) creates an invite link,
// the member's own app accepts it, and the aspirant's app publishes an end-to-end-encrypted watch-list (key in the
// invite URL fragment, never sent to us). The server stores link ids, states and the latest ciphertext only.
package family

import (
	"context"
	"encoding/base64"
	"slices"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/db"
	"github.com/amanroy3901/job-vac/backend/internal/ids"
	"github.com/amanroy3901/job-vac/backend/internal/problem"
	"github.com/amanroy3901/job-vac/backend/internal/push"
	"github.com/amanroy3901/job-vac/backend/internal/seal"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Limits (security-privacy.md §2).
const (
	MaxLinks     = 2
	InviteTTL    = 7 * 24 * time.Hour
	MaxWatchlist = 32768
)

// Service implements family links.
type Service struct {
	Pool          *pgxpool.Pool
	PublicBaseURL string
	Now           func() time.Time
}

func (s *Service) now() time.Time {
	if s.Now != nil {
		return s.Now()
	}
	return time.Now()
}

// Link is the API FamilyLink (owner side).
type Link struct {
	ID              string     `json:"id"`
	State           string     `json:"state"`
	Relation        *string    `json:"relation"`
	InviteURL       *string    `json:"invite_url"`
	InviteExpiresAt *time.Time `json:"invite_expires_at"`
	AcceptedAt      *time.Time `json:"accepted_at"`
	CreatedAt       time.Time  `json:"created_at"`
}

// Following is the API FamilyFollowing (member side).
type Following struct {
	LinkID           string     `json:"link_id"`
	State            string     `json:"state"`
	WatchlistVersion *int64     `json:"watchlist_version"`
	AcceptedAt       *time.Time `json:"accepted_at"`
}

// InviteView is the API FamilyInviteView.
type InviteView struct {
	LinkID    string     `json:"link_id"`
	State     string     `json:"state"`
	ExpiresAt *time.Time `json:"expires_at"`
}

func isPro(ctx context.Context, q db.Querier, userID string) (bool, error) {
	var plan string
	err := q.QueryRow(ctx, `SELECT plan FROM user_entitlement WHERE user_id = $1`, userID).Scan(&plan)
	if db.IsNoRows(err) {
		return false, nil
	}
	return plan == "pro", err
}

// Invite creates an invite (Pro only, max 2 invited/active links). The token is returned once in invite_url.
func (s *Service) Invite(ctx context.Context, ownerID string, relation *string) (*Link, error) {
	if relation != nil && !slices.Contains([]string{"parent", "sibling", "spouse", "other"}, *relation) {
		return nil, problem.Validation("bad relation", problem.FieldError{Field: "relation", Code: "out_of_range"})
	}
	token := ids.Token(16) // 128-bit
	var l Link
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT 1 FROM app_user WHERE id = $1 FOR UPDATE`, ownerID); err != nil {
			return err
		}
		pro, err := isPro(ctx, tx, ownerID)
		if err != nil {
			return err
		}
		if !pro {
			return problem.New(403, problem.ProRequired, "")
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM family_link WHERE owner_user_id = $1 AND
			(state = 'active' OR (state = 'invited' AND invite_expires_at > now()))`, ownerID).Scan(&n); err != nil {
			return err
		}
		if n >= MaxLinks {
			return problem.New(409, problem.FamilyLimitReached, "")
		}
		exp := s.now().Add(InviteTTL)
		err = tx.QueryRow(ctx, `INSERT INTO family_link (id, owner_user_id, invite_token_hash, invite_expires_at, relation)
			VALUES ($1,$2,$3,$4,$5) RETURNING id, state, relation, invite_expires_at, accepted_at, created_at`,
			ids.New("f"), ownerID, seal.Hash(token), exp, relation).
			Scan(&l.ID, &l.State, &l.Relation, &l.InviteExpiresAt, &l.AcceptedAt, &l.CreatedAt)
		if err != nil {
			return err
		}
		return recordConsent(ctx, tx, ownerID, "family_link_owner")
	})
	if err != nil {
		return nil, err
	}
	u := s.PublicBaseURL + "/f/" + token
	l.InviteURL = &u
	return &l, nil
}

func recordConsent(ctx context.Context, q db.Querier, userID, purpose string) error {
	_, err := q.Exec(ctx, `INSERT INTO consent_log (user_id, purpose, notice_version, granted, locale)
		SELECT $1, $2, '-', true, locale FROM app_user WHERE id = $1`, userID, purpose)
	return err
}

func recordConsentVersion(ctx context.Context, q db.Querier, userID, purpose, version string) error {
	_, err := q.Exec(ctx, `INSERT INTO consent_log (user_id, purpose, notice_version, granted, locale)
		SELECT $1, $2, $3, true, locale FROM app_user WHERE id = $1`, userID, purpose, version)
	return err
}

// List returns the owner's links (expired invites reported as expired; active links paused while not Pro).
func (s *Service) List(ctx context.Context, ownerID string) ([]Link, error) {
	pro, err := isPro(ctx, s.Pool, ownerID)
	if err != nil {
		return nil, err
	}
	rows, err := s.Pool.Query(ctx, `SELECT id, state, relation, invite_expires_at, accepted_at, created_at FROM family_link
		WHERE owner_user_id = $1 AND state IN ('invited','active') ORDER BY created_at`, ownerID)
	if err != nil {
		return nil, err
	}
	links, err := pgx.CollectRows(rows, func(r pgx.CollectableRow) (Link, error) {
		var l Link
		return l, r.Scan(&l.ID, &l.State, &l.Relation, &l.InviteExpiresAt, &l.AcceptedAt, &l.CreatedAt)
	})
	if err != nil {
		return nil, err
	}
	for i := range links {
		l := &links[i]
		switch {
		case l.State == "invited" && l.InviteExpiresAt != nil && !s.now().Before(*l.InviteExpiresAt):
			l.State = "expired"
		case l.State == "active" && !pro:
			l.State = "paused"
		}
		if l.State != "invited" {
			l.InviteExpiresAt = nil
		}
	}
	return links, nil
}

func (s *Service) byToken(ctx context.Context, q db.Querier, token string, lock bool) (id, state, owner string, exp time.Time, err error) {
	sql := `SELECT id, state, owner_user_id, invite_expires_at FROM family_link WHERE invite_token_hash = $1`
	if lock {
		sql += ` FOR UPDATE`
	}
	err = q.QueryRow(ctx, sql, seal.Hash(token)).Scan(&id, &state, &owner, &exp)
	if db.IsNoRows(err) {
		err = problem.New(404, problem.NotFound, "invite")
	}
	return
}

// GetInvite is the member-side view of an invite (S35).
func (s *Service) GetInvite(ctx context.Context, token string) (*InviteView, error) {
	id, state, _, exp, err := s.byToken(ctx, s.Pool, token, false)
	if err != nil {
		return nil, err
	}
	if state == "invited" && !s.now().Before(exp) {
		return nil, problem.New(410, problem.InviteExpired, "")
	}
	v := &InviteView{LinkID: id, State: state}
	if state == "invited" {
		v.ExpiresAt = &exp
	}
	return v, nil
}

// Accept links the member (single use). The owner gets a family `accepted` push.
func (s *Service) Accept(ctx context.Context, memberID, token, consentVersion string) (*Following, error) {
	if consentVersion == "" {
		return nil, problem.Validation("consent_version is required", problem.FieldError{Field: "consent_version", Code: "required"})
	}
	var f Following
	err := db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		id, state, owner, exp, err := s.byToken(ctx, tx, token, true)
		if err != nil {
			return err
		}
		if owner == memberID {
			return problem.New(403, problem.Forbidden, "cannot follow yourself")
		}
		if state != "invited" || !s.now().Before(exp) {
			return problem.New(410, problem.InviteExpired, "invite is "+state)
		}
		err = tx.QueryRow(ctx, `UPDATE family_link SET state = 'active', member_user_id = $2, accepted_at = now()
			WHERE id = $1 RETURNING id, accepted_at`, id, memberID).Scan(&f.LinkID, &f.AcceptedAt)
		if err != nil {
			return err
		}
		f.State = "active"
		if err := recordConsentVersion(ctx, tx, memberID, "family_link_member", consentVersion); err != nil {
			return err
		}
		return push.EnqueueAccountPush(ctx, tx, push.AccountPush{UserID: owner, Type: "family", Template: "family_accepted",
			Data: map[string]string{"family_link_id": id, "family_event": "accepted"}, MsgID: "family_accepted:" + id})
	})
	if err != nil {
		return nil, err
	}
	return &f, nil
}

// Decline marks an invite declined (no account needed).
func (s *Service) Decline(ctx context.Context, token string) error {
	return db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		id, state, _, _, err := s.byToken(ctx, tx, token, true)
		if err != nil {
			return err
		}
		if state != "invited" {
			return nil
		}
		_, err = tx.Exec(ctx, `UPDATE family_link SET state = 'declined', ended_at = now() WHERE id = $1`, id)
		return err
	})
}

// end removes a link from either side and tells the other side.
func (s *Service) end(ctx context.Context, linkID, callerID, side string) error {
	return db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		var owner string
		var member *string
		var state string
		err := tx.QueryRow(ctx, `SELECT owner_user_id, member_user_id, state FROM family_link WHERE id = $1 FOR UPDATE`, linkID).Scan(&owner, &member, &state)
		if db.IsNoRows(err) || (side == "owner" && owner != callerID) || (side == "member" && (member == nil || *member != callerID)) {
			return problem.New(404, problem.NotFound, "link")
		}
		if err != nil {
			return err
		}
		if state == "removed" {
			return nil
		}
		if _, err := tx.Exec(ctx, `UPDATE family_link SET state = 'removed', ended_at = now() WHERE id = $1`, linkID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `DELETE FROM family_watchlist WHERE link_id = $1`, linkID); err != nil {
			return err
		}
		other := owner
		if side == "owner" {
			if member == nil {
				return nil
			}
			other = *member
		}
		return push.EnqueueAccountPush(ctx, tx, push.AccountPush{UserID: other, Type: "family", Template: "family_removed",
			Data: map[string]string{"family_link_id": linkID, "family_event": "removed"}, MsgID: "family_removed:" + linkID})
	})
}

// Remove: the owner ends a link (D06).
func (s *Service) Remove(ctx context.Context, ownerID, linkID string) error {
	return s.end(ctx, linkID, ownerID, "owner")
}

// StopFollowing: the member ends a link (D12).
func (s *Service) StopFollowing(ctx context.Context, memberID, linkID string) error {
	return s.end(ctx, linkID, memberID, "member")
}

// Watchlist is the API EncryptedWatchlist.
type Watchlist struct {
	Version    int64      `json:"version"`
	Ciphertext string     `json:"ciphertext"`
	UpdatedAt  *time.Time `json:"updated_at,omitempty"`
}

// PutWatchlist stores the owner's latest ciphertext (version must increase) and pushes `family sync` to the member.
func (s *Service) PutWatchlist(ctx context.Context, ownerID, linkID string, w Watchlist) error {
	ct, err := base64.StdEncoding.DecodeString(w.Ciphertext)
	if err != nil || len(ct) == 0 {
		return problem.Validation("ciphertext must be base64", problem.FieldError{Field: "ciphertext", Code: "invalid"})
	}
	if len(ct) > MaxWatchlist {
		return problem.Validation("watch-list larger than 32 KB", problem.FieldError{Field: "ciphertext", Code: "too_large"})
	}
	if w.Version < 1 {
		return problem.Validation("version must be ≥ 1", problem.FieldError{Field: "version", Code: "out_of_range"})
	}
	return db.InTx(ctx, s.Pool, func(tx pgx.Tx) error {
		var owner, state string
		var member *string
		err := tx.QueryRow(ctx, `SELECT owner_user_id, member_user_id, state FROM family_link WHERE id = $1 FOR UPDATE`, linkID).Scan(&owner, &member, &state)
		if db.IsNoRows(err) || (err == nil && owner != ownerID) {
			return problem.New(404, problem.NotFound, "link")
		}
		if err != nil {
			return err
		}
		pro, err := isPro(ctx, tx, ownerID)
		if err != nil {
			return err
		}
		if !pro {
			return problem.New(403, problem.ProRequired, "")
		}
		if state != "active" || member == nil {
			return problem.New(409, problem.Conflict, "link is "+state)
		}
		tag, err := tx.Exec(ctx, `INSERT INTO family_watchlist (link_id, version, ciphertext, updated_at) VALUES ($1,$2,$3,now())
			ON CONFLICT (link_id) DO UPDATE SET version = EXCLUDED.version, ciphertext = EXCLUDED.ciphertext, updated_at = now()
			WHERE family_watchlist.version < EXCLUDED.version`, linkID, w.Version, ct)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problem.New(409, problem.Conflict, "watch-list version must increase")
		}
		return push.EnqueueAccountPush(ctx, tx, push.AccountPush{UserID: *member, Type: "family", Template: "family_sync",
			Data:  map[string]string{"family_link_id": linkID, "family_event": "sync"},
			MsgID: "family_sync:" + linkID + ":" + time.Now().UTC().Format("150405.000")})
	})
}

// Following lists links where userID is the member.
func (s *Service) Following(ctx context.Context, memberID string) ([]Following, error) {
	rows, err := s.Pool.Query(ctx, `SELECT l.id, l.state, w.version, l.accepted_at, COALESCE(e.plan, 'free') FROM family_link l
		LEFT JOIN family_watchlist w ON w.link_id = l.id LEFT JOIN user_entitlement e ON e.user_id = l.owner_user_id
		WHERE l.member_user_id = $1 AND l.state = 'active' ORDER BY l.accepted_at`, memberID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Following, error) {
		var f Following
		var plan string
		err := r.Scan(&f.LinkID, &f.State, &f.WatchlistVersion, &f.AcceptedAt, &plan)
		if plan != "pro" {
			f.State = "paused"
		}
		return f, err
	})
}

// GetWatchlist returns the latest ciphertext for the member.
func (s *Service) GetWatchlist(ctx context.Context, memberID, linkID string) (*Watchlist, error) {
	var w Watchlist
	var ct []byte
	var at time.Time
	err := s.Pool.QueryRow(ctx, `SELECT w.version, w.ciphertext, w.updated_at FROM family_link l JOIN family_watchlist w ON w.link_id = l.id
		WHERE l.id = $1 AND l.member_user_id = $2 AND l.state = 'active'`, linkID, memberID).Scan(&w.Version, &ct, &at)
	if db.IsNoRows(err) {
		return nil, problem.New(404, problem.NotFound, "watch-list")
	}
	if err != nil {
		return nil, err
	}
	w.Ciphertext, w.UpdatedAt = base64.StdEncoding.EncodeToString(ct), &at
	return &w, nil
}
