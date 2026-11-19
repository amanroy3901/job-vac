package com.sarkarimatch.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Result of one API call: the body plus its ETag, `304 Not Modified`, or an HTTP / transport failure. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T, val etag: String?) : ApiResult<T>
    data object NotModified : ApiResult<Nothing>
    data class HttpError(val status: Int, val body: String) : ApiResult<Nothing>
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}

/**
 * Ktor client for docs/api/openapi.yaml (data-layer.md §2), shared by Android and iOS. Stub: only the public
 * catalog/config calls; the rest (one function per operationId, data-layer.md §8) are added with ticket FE-03.
 * Base URL, `X-App-Version` and `Accept-Language` come from [createHttpClient].
 */
class SmApi(private val client: HttpClient) {
    suspend fun getCatalogHead(etag: String? = null): ApiResult<CatalogHeadDto> =
        call { client.get("v1/catalog/head") { etag?.let { header(HttpHeaders.IfNoneMatch, it) } } }

    /** Raw snapshot bytes (gzip JSON); parsed by CatalogSync (FE-13). */
    suspend fun getCatalogSnapshot(seq: Long, locales: String): ApiResult<ByteArray> =
        call { client.get("v1/catalog/snapshots/$seq") { parameter("locales", locales) } }

    suspend fun getCatalogBatch(seq: Long, locales: String): ApiResult<ByteArray> =
        call { client.get("v1/catalog/batches/$seq") { parameter("locales", locales) } }

    suspend fun getAppConfig(etag: String? = null): ApiResult<AppConfigDto> =
        call { client.get("v1/config") { etag?.let { header(HttpHeaders.IfNoneMatch, it) } } }

    @Suppress("TooGenericExceptionCaught") // transport errors differ per engine (OkHttp / Darwin)
    private suspend inline fun <reified T> call(block: () -> HttpResponse): ApiResult<T> = try {
        val response = block()
        when {
            response.status == HttpStatusCode.NotModified -> ApiResult.NotModified
            response.status.isSuccess() -> ApiResult.Ok(response.body<T>(), response.headers[HttpHeaders.ETag])
            else -> ApiResult.HttpError(response.status.value, response.body<String>())
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.NetworkError(e)
    }
}

@Serializable
data class CatalogHeadDto(
    val seq: Long,
    @SerialName("server_time") val serverTime: String,
    @SerialName("snapshot_seq") val snapshotSeq: Long? = null,
    val batches: List<Long> = emptyList(),
    @SerialName("evaluator_min_version_any") val evaluatorMinVersionAny: Int = 1,
)

@Serializable
data class AppConfigDto(
    @SerialName("min_supported_version") val minSupportedVersion: Int? = null,
    @SerialName("privacy_notice_version") val privacyNoticeVersion: Int? = null,
)
