package push

import (
	"fmt"
	"strconv"
	"time"
)

// iOS notification categories registered by the app (UNUserNotificationCenter.setNotificationCategories). The
// action identifiers are the iOS equivalents of the Android notification buttons and MUST match the app:
//
//	CategoryMatchReminder: actions ActionApplied ("भर दिया" / Applied, runs in the background, no unlock) and
//	                       ActionLater ("बाद में" / Later = snooze, background)
//	CategoryLifecycle:     no actions (tap opens the timeline)
//	CategorySelfTest:      no actions
//
// A Notification Service Extension (mutable-content) may change categoryIdentifier on the device, e.g. to drop
// the buttons on a family member's phone (a parent cannot mark Applied) or to suppress-by-replacement.
const (
	CategoryMatchReminder = "MATCH_REMINDER"
	CategoryLifecycle     = "LIFECYCLE"
	CategorySelfTest      = "SELF_TEST"
	ActionApplied         = "APPLIED"
	ActionLater           = "LATER"
)

// BuildFCM returns the FCM HTTP v1 request body ({"message": …}) for m.
//
// The `android` block is unchanged since the Android-only release: data-only, priority, ttl, collapse_key (the app
// composes every notification from its own resources). The `apns` block makes the same message work on iOS, where
// data-only (background) pushes are throttled and dropped when the app was force-quit, so user-facing pushes are
// sent as **alert** pushes that still carry no server text:
//
//   - alert pushes use `title-loc-key` / `loc-key` resolved by iOS from the app bundle's Localizable.strings in
//     the device language (keys "push.<type>[.<kind>].title|body"), `mutable-content: 1` so the Notification
//     Service Extension can rewrite title/body from the local catalog (job name, last date) and pick the category,
//     a `category` for the Applied / Later buttons, `thread-id` for grouping and `interruption-level`
//     (`time-sensitive` for last-day deadline reminders, which needs the Time Sensitive Notifications capability);
//     headers apns-push-type=alert, apns-priority 10 (high) or 5 (normal), apns-expiration from TTL,
//     apns-collapse-id from Collapse;
//   - account/family pushes that carry server-rendered fallback text (N-31, N-33, N-47, family accepted/removed)
//     use it as the alert title/body (the account's own locale; no job or profile data);
//   - `sync` (catalog), `account` template `entitlement` and `family_event=sync` are **background** pushes:
//     apns-push-type=background, apns-priority 5 (required by APNs for background), `content-available: 1`.
//
// FCM copies `data` keys into the APNs payload as custom keys, so the iOS app reads the same PushData map.
// Sources: Firebase "Send a message" / ApnsConfig (https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#apnsconfig),
// Apple "Generating a remote notification" and "Sending notification requests to APNs"
// (https://developer.apple.com/documentation/usernotifications/generating-a-remote-notification,
// https://developer.apple.com/documentation/usernotifications/sending-notification-requests-to-apns).
func BuildFCM(m Message, now time.Time) map[string]any {
	msg := map[string]any{"data": m.Data}
	if m.Token != "" {
		msg["token"] = m.Token
	} else {
		msg["topic"] = m.Topic
	}
	android := map[string]any{"priority": "NORMAL"}
	if m.High {
		android["priority"] = "HIGH"
	}
	if m.TTL > 0 {
		android["ttl"] = fmt.Sprintf("%ds", int(m.TTL.Seconds()))
	}
	if m.Collapse != "" {
		android["collapse_key"] = m.Collapse
	}
	msg["android"] = android
	msg["apns"] = APNs(m, now)
	return map[string]any{"message": msg}
}

// APNs builds the FCM `apns` block (headers + payload) for m.
func APNs(m Message, now time.Time) map[string]any {
	d := m.Data
	headers := map[string]string{}
	if m.TTL > 0 {
		headers["apns-expiration"] = strconv.FormatInt(now.Add(m.TTL).Unix(), 10)
	}
	if m.Collapse != "" && len(m.Collapse) <= 64 {
		headers["apns-collapse-id"] = m.Collapse
	}
	if isBackground(d) {
		headers["apns-push-type"] = "background"
		headers["apns-priority"] = "5"
		return map[string]any{"headers": headers, "payload": map[string]any{"aps": map[string]any{"content-available": 1}}}
	}
	headers["apns-push-type"] = "alert"
	headers["apns-priority"] = "5"
	if m.High {
		headers["apns-priority"] = "10"
	}
	aps := map[string]any{"mutable-content": 1, "sound": "default", "interruption-level": "active"}
	typ := d["type"]
	key := "push." + typ
	if k := d["kind"]; k != "" {
		key += "." + k
	}
	if d["fallback_title"] != "" {
		aps["alert"] = map[string]any{"title": d["fallback_title"], "body": d["fallback_body"]}
	} else {
		aps["alert"] = map[string]any{"title-loc-key": key + ".title", "loc-key": key + ".body"}
	}
	switch typ {
	case "reminder", "deadline":
		aps["category"] = CategoryMatchReminder
		if d["kind"] == "last_day" {
			aps["interruption-level"] = "time-sensitive"
			aps["relevance-score"] = 1.0
		}
	case "lifecycle":
		aps["category"] = CategoryLifecycle
	case "self_test":
		aps["category"] = CategorySelfTest
	}
	switch {
	case d["post_id"] != "":
		aps["thread-id"] = d["post_id"]
	case d["notice_id"] != "":
		aps["thread-id"] = d["notice_id"]
	case typ == "account" || typ == "family":
		aps["thread-id"] = typ
	}
	return map[string]any{"headers": headers, "payload": map[string]any{"aps": aps}}
}

// isBackground: messages that only tell the app to fetch/refresh (no user-visible notification).
func isBackground(d map[string]string) bool {
	switch d["type"] {
	case "sync":
		return true
	case "account":
		return d["template"] == "entitlement"
	case "family":
		return d["family_event"] == "sync"
	}
	return false
}
