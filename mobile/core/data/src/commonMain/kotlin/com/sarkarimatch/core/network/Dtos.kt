package com.sarkarimatch.core.network

import com.sarkarimatch.core.model.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// DTOs for docs/api/openapi.yaml (only the fields the app reads; unknown keys are ignored by SmJson).

@Serializable
data class CatalogFileRefDto(
    val seq: Long,
    val url: String? = null,
    val sha256: Map<String, String> = emptyMap(),
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    val reason: String? = null,
)

/** `CatalogHead`. */
@Serializable
data class CatalogHeadDto(
    val seq: Long,
    @SerialName("server_time") val serverTime: String,
    val snapshot: CatalogFileRefDto? = null,
    val batches: List<CatalogFileRefDto> = emptyList(),
    @SerialName("evaluator_min_version_any") val evaluatorMinVersionAny: Int = 1,
)

/** `CatalogSnapshot` and `CatalogBatch` (a snapshot has no `removed_notice_ids`). */
@Serializable
data class CatalogFileDto(
    val seq: Long,
    val locales: List<String> = emptyList(),
    val notices: List<NoticeDto> = emptyList(),
    @SerialName("removed_notice_ids") val removedNoticeIds: List<String> = emptyList(),
)

@Serializable
data class KeyDateDto(val kind: String, val date: String, val time: String? = null)

@Serializable
data class LinkDto(val kind: String, val url: String, val label: LocalizedText? = null)

/** `Notice` (catalog form). Stored whole as JSON in Room; the posts are split out with their rules. */
@Serializable
data class NoticeDto(
    val id: String,
    val version: Int = 1,
    @SerialName("catalog_seq") val catalogSeq: Long = 0,
    val title: LocalizedText,
    @SerialName("short_title") val shortTitle: LocalizedText? = null,
    val org: LocalizedText,
    @SerialName("exam_type") val examType: String? = null,
    val scope: String? = null,
    val state: String? = null,
    val status: String,
    @SerialName("advt_no") val advtNo: String? = null,
    @SerialName("key_dates") val keyDates: List<KeyDateDto> = emptyList(),
    val links: List<LinkDto> = emptyList(),
    @SerialName("new_match_paused") val newMatchPaused: Boolean = false,
    val posts: List<PostDto> = emptyList(),
) {
    val applyEnd: KeyDateDto? get() = keyDates.firstOrNull { it.kind == "apply_end" }
}

@Serializable
data class PostDto(
    val id: String,
    val name: LocalizedText,
    @SerialName("short_name") val shortName: LocalizedText? = null,
    @SerialName("exam_tags") val examTags: List<String> = emptyList(),
    @SerialName("pay_level") val payLevel: String? = null,
    @SerialName("vacancies_total") val vacanciesTotal: Int? = null,
    @SerialName("rule_version") val ruleVersion: Int = 1,
    val status: String = "active",
    val rule: JsonObject? = null,
)

/** `AppConfig` (subset). */
@Serializable
data class AppConfigDto(
    @SerialName("min_supported_version") val minSupportedVersion: Int? = null,
    @SerialName("latest_version") val latestVersion: Int? = null,
    val update: String = "none",
    @SerialName("privacy_notice_version") val privacyNoticeVersion: String? = null,
    @SerialName("reference_version") val referenceVersion: String? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val disclaimer: LocalizedText? = null,
    val ads: JsonElement? = null,
)

@Serializable
data class DeviceInputDto(
    val platform: String,
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("app_version") val appVersion: Int,
    val locale: String,
)

@Serializable
data class SessionRequestDto(
    @SerialName("installation_id") val installationId: String,
    val locale: String,
    @SerialName("privacy_notice_version") val privacyNoticeVersion: String? = null,
    val device: DeviceInputDto? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val locale: String? = null,
    @SerialName("auth_provider") val authProvider: String,
    @SerialName("google_email") val googleEmail: String? = null,
    @SerialName("billing_account_ref") val billingAccountRef: String? = null,
)

@Serializable
data class SessionResponseDto(
    val user: UserDto,
    @SerialName("is_new_user") val isNewUser: Boolean,
    val entitlement: EntitlementDto,
    @SerialName("has_backup") val hasBackup: Boolean = false,
)

/** `Entitlement`: the only source of truth for Pro (never local purchase state). */
@Serializable
data class EntitlementDto(
    val plan: String,
    val status: String,
    val source: String? = null,
    val platform: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("auto_renewing") val autoRenewing: Boolean = false,
    @SerialName("trial_available") val trialAvailable: Boolean = false,
    @SerialName("ads_enabled") val adsEnabled: Boolean = true,
    val features: Map<String, Boolean> = emptyMap(),
    val token: String? = null,
) {
    val isPro: Boolean get() = plan == "pro" && status in setOf("active", "grace")

    companion object {
        /** Guests (no account) are free with the trial still on offer. */
        val GUEST = EntitlementDto(plan = "free", status = "none", trialAvailable = true)
    }
}

/** RFC 9457 problem (subset). */
@Serializable
data class ProblemDto(
    val status: Int = 0,
    val code: String? = null,
    val detail: String? = null,
    val message: LocalizedText? = null,
    val retryable: Boolean = false,
)
