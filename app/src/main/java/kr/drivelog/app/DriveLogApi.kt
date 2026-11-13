package kr.drivelog.app

import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

internal const val DriveLogOrigin = "https://35.216.33.209"

internal data class RemoteTrip(val id: String, val phase: String)
internal data class RemoteMoment(
    val id: String,
    val author: String,
    val at: Instant,
    val kind: String,
    val place: String?,
    val state: String,
    val title: String?,
    val canPlay: Boolean,
)
internal data class RemoteTimeline(
    val startedAt: Instant,
    val phase: String,
    val moments: List<RemoteMoment>,
)
internal data class SavedClip(val tripId: String, val title: String, val durationMs: Long)
internal data class Handoff(val png: ByteArray, val expiresAt: Instant)

internal class ApiError(val code: Int) : IOException()

internal class DriveLogApi(origin: String, private val token: String) {
    private val endpoint = URI(origin.trim()).also {
        require(it.scheme == "https" && it.host != null && it.userInfo == null)
        require(it.path in listOf("", "/") && it.query == null && it.fragment == null)
    }.toString().trimEnd('/')

    suspend fun status(): String = objectRequest("/api/status", authenticated = false).getString("status")

    suspend fun trips(): List<RemoteTrip> = arrayRequest("/api/private-trips").map {
        RemoteTrip(it.uuid("tripId"), it.getString("phase"))
    }

    suspend fun timeline(tripId: String): RemoteTimeline {
        val data = objectRequest(tripPath(tripId) + "/timeline")
        val started = Instant.parse(data.getString("startedAt"))
        val entries = data.getJSONArray("entries").objects().map { entry ->
            val mark = entry.getJSONObject("mark")
            val id = mark.uuid("id")
            val state = entry.getString("state")
            RemoteMoment(id, mark.getString("author"), started.plusMillis(mark.getLong("atMs")),
                mark.getString("kind"), entry.optionalText("place"), state, entry.optionalText("title"),
                state == "CLIP_READY" && entry.optionalText("clipUrl") != null)
        }
        return RemoteTimeline(started, data.getString("phase"), entries.sortedBy { it.at })
    }

    suspend fun mark(tripId: String, markId: String, atMs: Long, command: String?) {
        val body = JSONObject().put("atMs", atMs)
        val route = if (command == null) {
            body.put("kind", "TAP")
            "marks"
        } else {
            body.put("command", command)
            "voice-marks"
        }
        objectRequest("${tripPath(tripId)}/$route/${UUID.fromString(markId)}", "PUT", body)
    }

    suspend fun library(): List<SavedClip> = arrayRequest("/api/library").map {
        SavedClip(it.uuid("tripId"), it.getString("title"), it.getLong("durationMs"))
    }

    suspend fun handoff(tripId: String): Handoff {
        val data = objectRequest("${libraryPath(tripId)}/handoffs", "POST")
        val prefix = "data:image/png;base64,"
        val image = data.getString("qrImage")
        require(image.startsWith(prefix) && image.length <= 350_000)
        return Handoff(Base64.decode(image.removePrefix(prefix), Base64.DEFAULT),
            Instant.parse(data.getString("expiresAt")))
    }

    suspend fun downloadMoment(tripId: String, markId: String, cache: File): File =
        download("${tripPath(tripId)}/marks/${UUID.fromString(markId)}/clip", cache)

    suspend fun downloadSaved(tripId: String, cache: File): File =
        download("${libraryPath(tripId)}/clip", cache)

    private suspend fun download(path: String, cache: File): File {
        var temporary: File? = null
        try {
            return request(path, accept = "video/mp4") { connection ->
                require(connection.contentType?.substringBefore(';') == "video/mp4")
                File.createTempFile("drivelog-preview-", ".mp4", cache).also { file ->
                    temporary = file
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                total += count
                                if (total > 128L * 1024 * 1024) throw IOException("Clip exceeds preview limit")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            temporary?.delete()
            throw error
        }
    }

    private suspend fun objectRequest(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        authenticated: Boolean = true,
    ): JSONObject = request(path, method, body, authenticated) {
        JSONObject(it.inputStream.bufferedReader().use { reader -> reader.readText() })
    }

    private suspend fun arrayRequest(path: String): List<JSONObject> = request(path) {
        JSONArray(it.inputStream.bufferedReader().use { reader -> reader.readText() }).objects()
    }

    private suspend fun <T> request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        authenticated: Boolean = true,
        accept: String = "application/json",
        read: suspend (HttpsURLConnection) -> T,
    ): T = withContext(Dispatchers.IO) {
        val connection = URI(endpoint + path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", accept)
            if (authenticated) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw ApiError(code)
            read(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun tripPath(id: String) = "/api/private-trips/${UUID.fromString(id)}"
    private fun libraryPath(id: String) = "/api/library/${UUID.fromString(id)}"
}

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun JSONObject.uuid(key: String) = UUID.fromString(getString(key)).toString()
private fun JSONObject.optionalText(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun Handoff.decodeQr() = BitmapFactory.decodeByteArray(png, 0, png.size)

internal fun Exception.serverMessage(): String = when ((this as? ApiError)?.code) {
    401 -> "로그인 세션이 만료됐거나 인증이 필요해요."
    403 -> "접근 권한 또는 탑승자 동의를 확인하세요."
    404 -> "이 계정에서 해당 기록을 열 수 없어요."
    409 -> "현재 운행 상태에서 요청할 수 없어요."
    410 -> "만료되었거나 폐기된 기록이에요."
    422 -> "서버에서 이 음성 요청을 인식하지 못했어요."
    429 -> "서버가 바빠요. 잠시 후 다시 시도하세요."
    else -> "서버 연결 또는 응답을 확인할 수 없어요."
}
