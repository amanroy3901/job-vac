package com.sarkarimatch.core.network

import com.sarkarimatch.core.auth.IdTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Result of one API call: the body plus its ETag, `304 Not Modified`, or an HTTP / transport failure. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T, val etag: String?) : ApiResult<T>
    data object NotModified : ApiResult<Nothing>
    data class HttpError(val status: Int, val body: String) : ApiResult<Nothing> {
        val problem: ProblemDto? get() = runCatching { SmJson.decodeFromString(ProblemDto.serializer(), body) }.getOrNull()
    }
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Ok<T>)?.value

/** Thrown by account calls when no bearer token can be obtained (guest without an account provider). */
class NoAccountException : IllegalStateException("no account token available")

/**
 * Ktor client for docs/api/openapi.yaml (data-layer.md §2, §8), shared by Android, iOS and the desktop dev target.
 * Public calls (catalog, config) carry no Authorization; account calls take the bearer from [tokens].
 * Base URL, `X-App-Version` and `Accept-Language` come from [createHttpClient].
 */
class SmApi(private val client: HttpClient, private val tokens: IdTokenProvider? = null) {
    // ---- public
    suspend fun getCatalogHead(etag: String? = null): ApiResult<CatalogHeadDto> =
        call { client.get("v1/catalog/head") { etag?.let { header(HttpHeaders.IfNoneMatch, it) } } }

    suspend fun getCatalogSnapshot(seq: Long, locales: String): ApiResult<CatalogFileDto> =
        call { client.get("v1/catalog/snapshots/$seq") { parameter("locales", locales) } }

    suspend fun getCatalogBatch(seq: Long, locales: String): ApiResult<CatalogFileDto> =
        call { client.get("v1/catalog/batches/$seq") { parameter("locales", locales) } }

    suspend fun getAppConfig(etag: String? = null): ApiResult<AppConfigDto> =
        call { client.get("v1/config") { etag?.let { header(HttpHeaders.IfNoneMatch, it) } } }

    // ---- account (Authorization + Idempotency-Key on writes)
    suspend fun startSession(request: SessionRequestDto): ApiResult<SessionResponseDto> = authed { token ->
        client.post("v1/session") {
            bearer(token)
            idempotent()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getEntitlement(): ApiResult<EntitlementDto> = authed { token -> client.get("v1/me/entitlement") { bearer(token) } }

    /** `POST /v1/me/trial` (B02 trial CTA). 409 when the trial was already used. */
    suspend fun startTrial(): ApiResult<EntitlementDto> = authed { token ->
        client.post("v1/me/trial") {
            bearer(token)
            idempotent()
        }
    }

    private fun HttpRequestBuilder.bearer(token: String) = header(HttpHeaders.Authorization, "Bearer $token")

    @OptIn(ExperimentalUuidApi::class)
    private fun HttpRequestBuilder.idempotent(key: String = Uuid.random().toString()) = header("Idempotency-Key", key)

    private suspend inline fun <reified T> authed(crossinline block: suspend (String) -> HttpResponse): ApiResult<T> {
        val token = tokens?.idToken() ?: return ApiResult.NetworkError(NoAccountException())
        return call { block(token) }
    }

    @Suppress("TooGenericExceptionCaught") // transport errors differ per engine (OkHttp / Darwin)
    private suspend inline fun <reified T> call(block: () -> HttpResponse): ApiResult<T> = try {
        val response = block()
        when {
            response.status == HttpStatusCode.NotModified -> ApiResult.NotModified
            response.status.isSuccess() -> ApiResult.Ok(response.body<T>(), response.headers[HttpHeaders.ETag])
            else -> ApiResult.HttpError(response.status.value, response.bodyAsText())
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.NetworkError(e)
    }
}
