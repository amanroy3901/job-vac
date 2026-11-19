package com.sarkarimatch.core.network

import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.Platform
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SmApiTest {
    private val config = AppConfig(Platform.ANDROID, "https://api.example.test/", "0.1.0", 7, httpLogging = false)

    @Test
    fun catalogHeadIsParsedAndSendsAppHeaders() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://api.example.test/v1/catalog/head", request.url.toString())
            assertEquals("7", request.headers["X-App-Version"])
            assertEquals("hi", request.headers[HttpHeaders.AcceptLanguage])
            assertEquals("\"e1\"", request.headers[HttpHeaders.IfNoneMatch])
            respond(
                content = """{"seq": 42, "server_time": "2026-11-19T10:00:00Z", "batches": [40, 41], "unknown": true}""",
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"e2\"")),
            )
        }
        val api = SmApi(createHttpClient(engine, config) { "hi" })
        val result = assertIs<ApiResult.Ok<CatalogHeadDto>>(api.getCatalogHead(etag = "\"e1\""))
        assertEquals(42, result.value.seq)
        assertEquals(listOf(40L, 41L), result.value.batches)
        assertEquals("\"e2\"", result.etag)
    }

    @Test
    fun notModifiedAndErrors() = runTest {
        var status = HttpStatusCode.NotModified
        val api = SmApi(createHttpClient(MockEngine { respond("""{"title":"x"}""", status) }, config) { "en" })
        assertIs<ApiResult.NotModified>(api.getAppConfig(etag = "\"e\""))
        status = HttpStatusCode.ServiceUnavailable
        assertEquals(503, assertIs<ApiResult.HttpError>(api.getAppConfig()).status)
    }
}
