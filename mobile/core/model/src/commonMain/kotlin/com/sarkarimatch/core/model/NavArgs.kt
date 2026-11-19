package com.sarkarimatch.core.model

import kotlinx.serialization.Serializable

/** S13/S15/S16/S47 are shared between onboarding and settings; only the CTA differs (navigation.md §2). */
@Serializable
enum class SetupMode { ONBOARDING, SETTINGS }

/** Where the paywall was opened from (billing.md §8). */
@Serializable
enum class PaywallSource { APPLIED_MOMENT, LOCKED_STAGE, PROFILE, TRIAL_ENDING_PUSH, FEED_BANNER, DEEP_LINK }

/** Field edited on S27 (reuses the S04–S09 step bodies). */
@Serializable
enum class ProfileField { EXAMS, QUALIFICATION, DOB, CATEGORY, STATE, MORE }

@Serializable
enum class ArchiveTab { NotInterested, Expired, Applied }
