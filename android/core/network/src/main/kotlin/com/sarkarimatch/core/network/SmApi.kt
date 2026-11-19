package com.sarkarimatch.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API (docs/api/openapi.yaml, data-layer.md §2). Stub: only the public catalog/config calls; the rest
 * (one function per operationId, data-layer.md §8) are added with ticket FE-03.
 */
interface SmApi {
    @GET("v1/catalog/head")
    suspend fun getCatalogHead(@Header("If-None-Match") etag: String? = null): Response<CatalogHeadDto>

    @GET("v1/catalog/snapshots/{seq}")
    suspend fun getCatalogSnapshot(@Path("seq") seq: Long, @Query("locales") locales: String): ResponseBody

    @GET("v1/catalog/batches/{seq}")
    suspend fun getCatalogBatch(@Path("seq") seq: Long, @Query("locales") locales: String): ResponseBody

    @GET("v1/config")
    suspend fun getAppConfig(@Header("If-None-Match") etag: String? = null): Response<AppConfigDto>
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
