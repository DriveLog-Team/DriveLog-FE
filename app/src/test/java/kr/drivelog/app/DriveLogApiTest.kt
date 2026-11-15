package kr.drivelog.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriveLogApiTest {
    private val trip = "trip-11111111-1111-4111-8111-111111111111"
    private val account = "22222222-2222-4222-8222-222222222222"
    private val child = "33333333-3333-4333-8333-333333333333"
    private val job = "44444444-4444-4444-8444-444444444444"
    private val result = """{"tripId":"$trip","phase":"UPLOAD","owner":"$account","occupants":[],"receipts":[]}"""

    @Test fun uploadSendsRawBytesWithActualLengthHashAndPersonalAuth() = runBlocking {
        lateinit var connection: FakeConnection
        val api = DriveLogApi("https://35.216.33.209", "test-personal-token") { uri ->
            FakeConnection(uri, result).also { connection = it }
        }
        var opens = 0
        api.upload(trip, "front") { opens++; ByteArrayInputStream("abc".toByteArray()) }
        assertEquals("/api/private-trips/$trip/media/front", connection.url.path)
        assertEquals("PUT", connection.requestMethod)
        assertEquals("Bearer test-personal-token", connection.getRequestProperty("Authorization"))
        assertEquals("video/mp4", connection.getRequestProperty("Content-Type"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            connection.getRequestProperty("X-Content-SHA256"))
        assertEquals(3L, connection.length)
        assertArrayEquals("abc".toByteArray(), connection.output.toByteArray())
        assertEquals(2, opens)
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }

    @Test fun guardianConsentTargetsChildAndIncludesEveryDecision() = runBlocking {
        lateinit var connection: FakeConnection
        val api = DriveLogApi("https://35.216.33.209", "token") { FakeConnection(it, result).also { c -> connection = c } }
        api.consent(trip, child, "GUARDIAN", true, true, false, false)
        assertEquals("/api/private-trips/$trip/consents/$child/GUARDIAN", connection.url.path)
        val body = JSONObject(connection.output.toString("UTF-8"))
        assertTrue(body.getBoolean("extraction"))
        assertTrue(body.getBoolean("geminiTranscript"))
        assertFalse(body.getBoolean("useTripContext"))
        assertFalse(body.getBoolean("shareMarkedClips"))
    }

    @Test fun editReturnsNewVersionAndSendsOrderedCaptionArray() = runBlocking {
        lateinit var connection: FakeConnection
        val api = DriveLogApi("https://35.216.33.209", "token") {
            FakeConnection(it, """{"jobId":"$job"}""").also { c -> connection = c }
        }
        assertEquals(job, api.edit(job, ClipEdit("moment-001", null, 40000, 45000, listOf("첫 줄", "둘째 줄"))))
        val body = JSONObject(connection.output.toString("UTF-8"))
        assertFalse(body.has("title"))
        assertEquals(40000, body.getLong("startMs"))
        assertEquals("첫 줄", body.getJSONArray("captions").getString(0))
        assertEquals("둘째 줄", body.getJSONArray("captions").getString(1))
    }

    @Test fun propagatesHttpStatusAndDisconnectsOnFailure() {
        lateinit var connection: FakeConnection
        val api = DriveLogApi("https://35.216.33.209", "token") { FakeConnection(it, "", 403).also { c -> connection = c } }
        val error = assertThrows(ApiError::class.java) { runBlocking { api.trip(trip) } }
        assertEquals(403, error.code)
        assertTrue(connection.disconnected)
    }

    private class FakeConnection(uri: URI, private val response: String, private val code: Int = 200) : HttpsURLConnection(uri.toURL()) {
        val output = ByteArrayOutputStream()
        var length = -1L
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray())
        override fun getOutputStream() = output
        override fun getResponseCode() = code
        override fun setFixedLengthStreamingMode(contentLength: Long) {
            super.setFixedLengthStreamingMode(contentLength)
            length = contentLength
        }
    }
}
