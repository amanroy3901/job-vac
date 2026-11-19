package com.sarkarimatch.core.data.account

import com.sarkarimatch.core.data.prefs.UserPreferencesRepository
import com.sarkarimatch.core.database.AccountDao
import com.sarkarimatch.core.database.AccountEntity
import com.sarkarimatch.core.database.AppConfigEntity
import com.sarkarimatch.core.database.EntitlementEntity
import com.sarkarimatch.core.model.AppLanguage
import com.sarkarimatch.core.network.ApiResult
import com.sarkarimatch.core.network.AppConfigDto
import com.sarkarimatch.core.network.EntitlementDto
import com.sarkarimatch.core.network.SessionRequestDto
import com.sarkarimatch.core.network.SmApi
import com.sarkarimatch.core.network.SmJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** Failure of an account call, with the server's problem code when there is one. */
class AccountException(message: String, val code: String? = null) : Exception(message)

/**
 * Session bootstrap (`POST /v1/session`, docs/api/README.md §3) and the entitlement (§12). Guests have no account
 * until a server feature needs one (trial, purchase, family accept): [ensureAccount] creates it lazily.
 */
class AccountRepository(
    private val api: SmApi,
    private val dao: AccountDao,
    private val prefs: UserPreferencesRepository,
    private val language: () -> AppLanguage,
    private val clock: Clock = Clock.System,
) {
    /** The cached entitlement; guests are free with the trial on offer. */
    val entitlement: Flow<EntitlementDto> = dao.observeEntitlement().map { row ->
        row?.let { SmJson.decodeFromString(EntitlementDto.serializer(), it.json) } ?: EntitlementDto.GUEST
    }

    suspend fun account(): AccountEntity? = dao.account()

    /** Returns the account, creating it with `POST /v1/session` on first use. */
    suspend fun ensureAccount(privacyNoticeVersion: String? = null): Result<AccountEntity> {
        dao.account()?.let { return Result.success(it) }
        val request = SessionRequestDto(
            installationId = prefs.installationId(),
            locale = language().tag,
            privacyNoticeVersion = privacyNoticeVersion,
        )
        return when (val r = api.startSession(request)) {
            is ApiResult.Ok -> {
                val now = clock.now().toEpochMilliseconds()
                val account = AccountEntity(
                    userId = r.value.user.id,
                    authProvider = r.value.user.authProvider,
                    billingAccountRef = r.value.user.billingAccountRef,
                    updatedAtEpochMs = now,
                )
                dao.saveAccount(account)
                saveEntitlement(r.value.entitlement)
                Result.success(account)
            }
            else -> Result.failure(r.toException("session"))
        }
    }

    /** `GET /v1/me/entitlement` (only with an account; guests keep [EntitlementDto.GUEST]). */
    suspend fun refreshEntitlement(): Result<EntitlementDto> {
        if (dao.account() == null) return Result.success(EntitlementDto.GUEST)
        return when (val r = api.getEntitlement()) {
            is ApiResult.Ok -> Result.success(r.value.also { saveEntitlement(it) })
            else -> Result.failure(r.toException("entitlement"))
        }
    }

    /** B02 trial CTA: account first (anonymous for a guest), then `POST /v1/me/trial`. */
    suspend fun startTrial(): Result<EntitlementDto> {
        ensureAccount().onFailure { return Result.failure(it) }
        return when (val r = api.startTrial()) {
            is ApiResult.Ok -> Result.success(r.value.also { saveEntitlement(it) })
            else -> Result.failure(r.toException("trial"))
        }
    }

    private suspend fun saveEntitlement(e: EntitlementDto) {
        dao.saveEntitlement(EntitlementEntity(
            json = SmJson.encodeToString(EntitlementDto.serializer(), e),
            fetchedAtEpochMs = clock.now().toEpochMilliseconds(),
        ))
    }
}

/** `GET /v1/config` with ETag, cached in Room (data-layer.md §8 ConfigRepository). */
class ConfigRepository(private val api: SmApi, private val dao: AccountDao, private val clock: Clock = Clock.System) {
    val config: Flow<AppConfigDto?> = dao.observeConfig().map { row -> row?.let { SmJson.decodeFromString(
        AppConfigDto.serializer(),
        it.json,
    ) } }

    suspend fun refresh(): Result<AppConfigDto> {
        val cached = dao.config()
        return when (val r = api.getAppConfig(cached?.etag)) {
            is ApiResult.Ok -> {
                dao.saveConfig(AppConfigEntity(
                    json = SmJson.encodeToString(AppConfigDto.serializer(), r.value),
                    etag = r.etag,
                    fetchedAtEpochMs = clock.now().toEpochMilliseconds(),
                ))
                Result.success(r.value)
            }
            ApiResult.NotModified -> Result.success(SmJson.decodeFromString(AppConfigDto.serializer(), requireNotNull(cached).json))
            else -> Result.failure(r.toException("config"))
        }
    }
}

internal fun ApiResult<*>.toException(what: String): AccountException = when (this) {
    is ApiResult.HttpError -> AccountException("$what: HTTP $status ${problem?.detail.orEmpty()}", problem?.code)
    is ApiResult.NetworkError -> AccountException("$what: ${cause.message}")
    else -> AccountException("$what: $this")
}
