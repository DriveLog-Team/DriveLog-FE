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

internal data class DriveRoute(val departure: String? = null, val destination: String? = null)
internal data class RemoteMoment(
    val id: String,
    val author: String,
    val at: Instant,
    val kind: String,
    val place: String?,
    val state: String,
    val title: String?,
    val canPlay: Boolean,
    val clipUrl: String?,
)
internal data class RemoteTimeline(
    val startedAt: Instant,
    val phase: String,
    val moments: List<RemoteMoment>,
    val route: DriveRoute,
)
internal data class SavedClip(
    val tripId: String, val title: String, val durationMs: Long, val route: DriveRoute,
    val markId: String?, val clipUrl: String?, val savedAt: Instant,
)
internal data class Handoff(val id: String, val png: ByteArray, val expiresAt: Instant)

internal data class ExportProfile(val id: String, val durationMs: Long, val available: Boolean)
internal data class DriveEvent(val atMs: Long, val kind: String, val label: String)

internal fun validDisplayName(value: String): String {
    val name = value.trim()
    require(name.codePointCount(0, name.length) in 1..32 && name.none { Character.isISOControl(it) })
    return name
}

internal fun driveEvent(seconds: String, kind: String, label: String): DriveEvent? {
    val atMs = secondsToMs(seconds) ?: return null
    if (atMs !in 0 until 86_400_000L || kind !in listOf("LANDMARK", "NEAR_ARRIVAL", "ARRIVAL") || label.isBlank()) return null
    return DriveEvent(atMs, kind, label.trim())
}

internal class ApiError(val code: Int) : IOException()

internal class DriveLogApi(
    origin: String,
    private val token: String,
    private val openConnection: (URI) -> HttpsURLConnection = { it.toURL().openConnection() as HttpsURLConnection },
) {
    private val endpoint = URI(origin.trim()).also {
        require(it.scheme == "https" && it.host != null && it.userInfo == null)
        require(it.path in listOf("", "/") && it.query == null && it.fragment == null)
    }.toString().trimEnd('/')

    suspend fun status(): String = objectRequest("/api/status", authenticated = false).getString("status")

    suspend fun profiles(): List<ExportProfile> = request("/api/export-profiles", authenticated = false) { connection ->
        JSONArray(connection.inputStream.bufferedReader().use { it.readText() }).objects().map {
            ExportProfile(it.getString("id"), it.getLong("targetDurationMs"), it.getBoolean("renderingAvailable"))
        }
    }

    suspend fun integrations(): JSONObject = objectRequest("/api/integrations", authenticated = false)

    suspend fun displayName(tripId: String, value: String) {
        objectRequest("${tripPath(tripId)}/display-name", "PUT", JSONObject().put("displayName", validDisplayName(value)))
    }

    suspend fun trips(): List<RemoteTrip> = arrayRequest("/api/private-trips").map {
        it.remoteTrip()
    }

    suspend fun timeline(tripId: String): RemoteTimeline {
        val data = objectRequest(tripPath(tripId) + "/timeline")
        val started = Instant.parse(data.getString("startedAt"))
        val entries = data.getJSONArray("entries").objects().map { entry ->
            val mark = entry.getJSONObject("mark")
            val id = mark.uuid("id")
            val state = entry.getString("state")
            RemoteMoment(id, entry.optionalText("authorName") ?: mark.getString("author"), started.plusMillis(mark.getLong("atMs")),
                mark.getString("kind"), entry.optionalText("place"), state, entry.optionalText("title"),
                state == "CLIP_READY" && entry.optionalText("clipUrl") != null, entry.optionalText("clipUrl"))
        }
        return RemoteTimeline(started, data.getString("phase"), entries.sortedBy { it.at }, data.driveRoute())
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
        SavedClip(it.getString("tripId"), it.getString("title"), it.getLong("durationMs"), it.driveRoute(),
            it.optionalText("markId"), it.optionalText("clipUrl"), Instant.parse(it.getString("savedAt")))
    }

    suspend fun handoff(tripId: String): Handoff {
        val data = objectRequest("${libraryPath(tripId)}/handoffs", "POST")
        return parseHandoff(data)
    }

    private fun parseHandoff(data: JSONObject): Handoff {
        val prefix = "data:image/png;base64,"
        val image = data.getString("qrImage")
        require(image.startsWith(prefix) && image.length <= 350_000)
        return Handoff(data.uuid("id"), Base64.decode(image.removePrefix(prefix), Base64.DEFAULT),
            Instant.parse(data.getString("expiresAt")))
    }

    suspend fun trip(tripId: String): JSONObject = objectRequest(tripPath(tripId))

    suspend fun consent(tripId: String, occupant: String, role: String,
                        extraction: Boolean, geminiTranscript: Boolean,
                        useTripContext: Boolean, shareMarkedClips: Boolean, receiptVersion: String? = null): JSONObject {
        require(role in listOf("PASSENGER", "GUARDIAN"))
        return objectRequest("${tripPath(tripId)}/consents/${UUID.fromString(occupant)}/$role", "PUT",
            JSONObject().put("extraction", extraction).put("geminiTranscript", geminiTranscript)
                .put("useTripContext", useTripContext).put("shareMarkedClips", shareMarkedClips), receiptVersion = receiptVersion)
    }

    suspend fun invite(tripId: String, under14: Boolean, guardian: String?): JSONObject {
        val body = JSONObject().put("under14", under14)
        if (under14) body.put("guardian", UUID.fromString(guardian).toString())
        return objectRequest("${tripPath(tripId)}/invites", "POST", body)
    }

    suspend fun context(tripId: String, departure: String, destination: String, events: List<DriveEvent> = emptyList()): JSONObject =
        objectRequest("${tripPath(tripId)}/context", "PUT", JSONObject()
            .put("departure", departure).put("destination", destination).put("events", JSONArray(events.map { JSONObject().put("atMs", it.atMs).put("kind", it.kind).put("label", it.label) })))

    suspend fun pick(tripId: String, markId: String) {
        objectRequest("${tripPath(tripId)}/picks/${UUID.fromString(markId)}", "PUT")
    }

    suspend fun discard(tripId: String): JSONObject = objectRequest(tripPath(tripId), "DELETE")

    suspend fun upload(tripId: String, side: String, open: () -> java.io.InputStream): JSONObject =
        withContext(Dispatchers.IO) {
            require(side in listOf("front", "rear"))
            val metadata = inspectUpload(open)
            request("${tripPath(tripId)}/media/$side", "PUT", write = { connection ->
                connection.doOutput = true
                connection.readTimeout = 120_000
                connection.setRequestProperty("Content-Type", "video/mp4")
                connection.setRequestProperty("X-Content-SHA256", metadata.sha256)
                connection.setFixedLengthStreamingMode(metadata.length)
                open().use { input ->
                    connection.outputStream.use { output -> copyUpload(input, output, metadata.length) }
                }
            }) { JSONObject(it.inputStream.bufferedReader().use { reader -> reader.readText() }) }
        }

    suspend fun createJob(tripId: String, profile: String = "STORY_15", requestKey: String? = null): String {
        require(profile in listOf("STORY_15", "SHORT_30"))
        tripIdentifier(tripId)
        return objectRequest("/api/media/jobs", "POST",
            JSONObject().put("tripId", tripId).put("profile", profile), requestKey = requestKey).uuid("jobId")
    }

    suspend fun job(jobId: String): JSONObject = objectRequest(jobPath(jobId))

    suspend fun edit(jobId: String, edit: ClipEdit, requestKey: String? = null): String {
        val body = JSONObject().put("id", edit.id)
        edit.title?.let { body.put("title", it) }
        edit.startMs?.let { body.put("startMs", it) }
        edit.endMs?.let { body.put("endMs", it) }
        edit.captions?.let { body.put("captions", JSONArray(it)) }
        return objectRequest("${jobPath(jobId)}/edits", "POST", body, requestKey = requestKey).uuid("jobId")
    }

    suspend fun finalize(tripId: String, jobId: String, cardId: String): JSONObject =
        objectRequest("${tripPath(tripId)}/finalize", "POST",
            JSONObject().put("jobId", UUID.fromString(jobId).toString()).put("moment", cardId))

    suspend fun downloadCard(jobId: String, cardId: String, cache: File): File =
        download(cardPath(jobId, cardId), cache)

    suspend fun cardHandoff(jobId: String, cardId: String): Handoff =
        parseHandoff(objectRequest("${cardPath(jobId, cardId)}/handoffs", "POST"))

    private fun jobPath(id: String) = "/api/media/jobs/${UUID.fromString(id)}"
    private fun cardPath(jobId: String, cardId: String): String {
        require(cardId.matches(Regex("[A-Za-z0-9_-]+")))
        return "${jobPath(jobId)}/clips/$cardId"
    }

    suspend fun downloadMoment(tripId: String, moment: RemoteMoment, cache: File): File =
        download(moment.clipUrl?.let(::personalPath)
            ?: "${tripPath(tripId)}/marks/${UUID.fromString(moment.id)}/clip", cache)

    suspend fun downloadSaved(clip: SavedClip, cache: File): File =
        download(clip.clipUrl?.let(::personalPath) ?: run {
            require(clip.markId == null) { "Marked clip requires clipUrl" }
            "${libraryPath(clip.tripId)}/clip"
        }, cache)

    suspend fun revokeHandoff(id: String) {
        request("/api/media/handoffs/${UUID.fromString(id)}", "DELETE") { Unit }
    }

    // Never forward a personal token to a server supplied external URL.
    private fun personalPath(value: String): String = personalClipPath(endpoint, value)

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
        requestKey: String? = null,
        receiptVersion: String? = null,
    ): JSONObject = request(path, method, body, authenticated, receiptVersion = receiptVersion, requestKey = requestKey) {
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
        requestKey: String? = null,
        receiptVersion: String? = null,
        write: (suspend (HttpsURLConnection) -> Unit)? = null,
        read: suspend (HttpsURLConnection) -> T,
    ): T = withContext(Dispatchers.IO) {
        val connection = openConnection(URI(endpoint + path))
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", accept)
            if (requestKey != null) connection.setRequestProperty("Idempotency-Key", requestKey)
            if (receiptVersion != null) connection.setRequestProperty("X-DriveLog-Receipt-Version", receiptVersion)
            if (authenticated) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            write?.invoke(connection)
            val code = connection.responseCode
            if (code !in 200..299) throw ApiError(code)
            read(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun tripPath(id: String) = "/api/private-trips/${tripIdentifier(id)}"
    private fun libraryPath(id: String) = "/api/library/${tripIdentifier(id)}"


}

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun JSONObject.uuid(key: String) = UUID.fromString(getString(key)).toString()
private fun JSONObject.optionalText(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.driveRoute(): DriveRoute {
    val context = optJSONObject("driveContext")
    return DriveRoute(
        departure = (context?.opt("departure") as? String)?.trim()?.takeIf { it.isNotEmpty() },
        destination = (context?.opt("destination") as? String)?.trim()?.takeIf { it.isNotEmpty() },
    )
}

internal fun Handoff.decodeQr() = BitmapFactory.decodeByteArray(png, 0, png.size)

internal fun Exception.serverMessage(): String = when {
    this is UploadValidationError -> message ?: "업로드할 파일을 확인하세요."
    else -> when ((this as? ApiError)?.code) {
    401 -> "로그인 세션이 만료됐거나 인증이 필요해요."
    403 -> "접근 권한 또는 탑승자 동의를 확인하세요."
    404 -> "이 계정에서 해당 기록을 열 수 없어요."
    409 -> "현재 운행 상태에서 요청할 수 없어요."
    410 -> "만료되었거나 폐기된 기록이에요."
    400 -> "입력 내용과 영상 파일을 확인하세요."
    422 -> "영상 규격·오디오 또는 표시 요청의 시각·명령을 확인하세요."
    429 -> "서버가 바빠요. 잠시 후 다시 시도하세요."
    507 -> "서버 저장 공간이 부족해요. 잠시 뒤 다시 시도하세요."
    503 -> "서버 처리 중이에요. 잠시 뒤 같은 요청으로 재시도하세요."
    else -> "서버 연결 또는 응답을 확인할 수 없어요."
}

}

internal fun tripIdentifier(id: String): String {
    require(id.matches(Regex("trip-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
    return id
}

internal fun personalClipPath(endpoint: String, value: String): String {
    val url = URI(endpoint + "/").resolve(value)
    val base = URI(endpoint)
    require(url.scheme == base.scheme && url.host == base.host && url.port == base.port)
    require(url.userInfo == null && url.fragment == null && url.rawPath.startsWith("/api/"))
    return url.rawPath + (url.rawQuery?.let { "?$it" } ?: "")
}
