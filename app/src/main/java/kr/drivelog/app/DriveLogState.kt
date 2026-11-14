package kr.drivelog.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class Access { UNKNOWN, RESTRICTED, PARKED }

internal data class Preferences(
    val automatic: Boolean = false,
    val location: Boolean = false,
    val analysis: Boolean = false,
    val consent: Boolean = false,
)

internal data class Session(val id: String, val startedAt: Long, val paused: Boolean = false)

internal enum class MomentSource(val label: String) {
    VOICE("음성 요청"),
    PASSENGER("동승자 표시"),
    MANUAL("수동 쇼츠 요청"),
}

internal enum class MomentStatus(val label: String) {
    MARKED("표시됨"),
    PROCESSING("생성 중"),
    SAMPLE("완성 · 시연"),
    WAITING("원본 대기"),
    FAILED("실패"),
}

internal data class Moment(
    val id: String,
    val sessionId: String,
    val requestedAt: Long,
    val elapsedSeconds: Long,
    val instruction: String,
    val title: String,
    val status: MomentStatus = MomentStatus.MARKED,
    val readyAt: Long = 0,
    val source: MomentSource = MomentSource.VOICE,
    val author: String = "운전자",
) {
    val startSeconds: Long get() = (elapsedSeconds - 60).coerceAtLeast(0)
    val endSeconds: Long get() = elapsedSeconds + 10
    val image: String get() = when (elapsedSeconds % 3) {
        0L -> "coastal-road.jpg"
        1L -> "driving-road.jpg"
        else -> "ocean-road.jpg"
    }
}

internal class DriveLogState(application: Application) : AndroidViewModel(application) {
    private val store = application.getSharedPreferences("drive_log", 0)
    private val saved = JSONObject(store.getString("state", "{}") ?: "{}")
    var preferences by mutableStateOf(
        Preferences(saved.optBoolean("auto"), saved.optBoolean("location"),
            saved.optBoolean("analysis"), saved.optBoolean("consent"))
    )
        private set
    var configured by mutableStateOf(saved.optBoolean("configured"))
        private set
    var session by mutableStateOf(saved.optJSONObject("session")?.let {
        Session(it.getString("id"), it.getLong("startedAt"), it.optBoolean("paused"))
    })
        private set
    var moments by mutableStateOf(readMoments())
        private set
    var platformAccess by mutableStateOf(Access.UNKNOWN)
        private set
    private var simulatedDriving by mutableStateOf(false)
    var feedback by mutableStateOf<String?>(null)
        private set

    init {
        if (BuildConfig.DEBUG && !saved.optBoolean("demoSeeded")) {
            moments = moments + demoMoments()
            persist()
        }
    }

    val access: Access
        get() = if (simulatedDriving) Access.RESTRICTED else platformAccess

    val canRequestDemoShorts: Boolean
        get() = BuildConfig.DEBUG && simulatedDriving && platformAccess == Access.PARKED &&
            preferences.consent && session?.paused == false

    fun requestDemoShorts() {
        if (!canRequestDemoShorts) return
        mark("방금 장면으로 쇼츠 만들어줘", source = MomentSource.MANUAL)
    }

    fun updatePlatform(value: Access) {
        platformAccess = value
    }

    fun savePreferences(value: Preferences) {
        if (access != Access.PARKED) return
        preferences = value
        configured = true
        if (!value.consent) pauseSession()
        persist()
    }

    fun simulateDrive() {
        simulatedDriving = true
        if (configured && preferences.automatic && preferences.consent && session == null) {
            session = Session(UUID.randomUUID().toString(), System.currentTimeMillis())
            persist()
        }
    }

    fun simulateParking() {
        simulatedDriving = false
    }

    fun pauseSession() {
        if (access != Access.PARKED) return
        val active = session ?: return
        session = active.copy(paused = true)
        persist()
    }

    fun occupantsChanged() {
        preferences = preferences.copy(consent = false)
        session = session?.copy(paused = true)
        persist()
    }

    fun resumeSession() {
        if (access != Access.PARKED || !preferences.consent) return
        val active = session ?: return
        session = active.copy(paused = false)
        persist()
    }

    fun mark(instruction: String, requestId: String? = null, source: MomentSource = MomentSource.VOICE) {
        if (!BuildConfig.DEBUG) return
        val active = session
        if (active == null || active.paused || !preferences.consent) {
            feedback = "진행 중인 기록이 없어요."
            return
        }
        val id = requestId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        if (moments.any { it.id == id }) return
        val now = System.currentTimeMillis()
        moments = listOf(
            Moment(id, active.id, now, ((now - active.startedAt) / 1000).coerceAtLeast(0),
                instruction.take(300),
                when (source) {
                    MomentSource.PASSENGER -> "동승자가 남긴 순간"
                    MomentSource.MANUAL -> "수동으로 남긴 순간"
                    MomentSource.VOICE -> "주행 중 남긴 순간"
                },
                source = source,
                author = if (source == MomentSource.PASSENGER) "동승자" else "운전자")
        ) + moments
        feedback = if (source == MomentSource.MANUAL) "쇼츠로 만들 장면을 남겼어요." else "이 장면을 표시했어요."
        persist()
    }

    fun finishSession() {
        if (access != Access.PARKED) return
        val active = session ?: return
        val readyAt = System.currentTimeMillis() + 2500
        moments = moments.map {
            if (it.sessionId != active.id || it.status != MomentStatus.MARKED) it
            else if (BuildConfig.DEBUG && preferences.analysis && preferences.consent) {
                it.copy(status = MomentStatus.PROCESSING, readyAt = readyAt)
            } else {
                it.copy(status = MomentStatus.WAITING)
            }
        }
        session = null
        feedback = null
        persist()
    }

    fun refreshResults() {
        val now = System.currentTimeMillis()
        if (moments.none { it.status == MomentStatus.PROCESSING && it.readyAt <= now }) return
        moments = moments.map {
            if (it.status == MomentStatus.PROCESSING && it.readyAt <= now) {
                it.copy(status = MomentStatus.SAMPLE, readyAt = 0)
            } else it
        }
        persist()
    }

    fun rename(id: String, title: String) {
        if (access != Access.PARKED || title.isBlank()) return
        moments = moments.map { if (it.id == id) it.copy(title = title.trim().take(40)) else it }
        persist()
    }

    fun clearHistory() {
        if (access != Access.PARKED || session != null) return
        moments = emptyList()
        persist()
    }

    fun clearFeedback() {
        feedback = null
    }

    private fun persist() {
        val data = JSONObject()
            .put("demoSeeded", BuildConfig.DEBUG || saved.optBoolean("demoSeeded"))
            .put("configured", configured)
            .put("auto", preferences.automatic)
            .put("location", preferences.location)
            .put("analysis", preferences.analysis)
            .put("consent", preferences.consent)
            .put("session", session?.let {
                JSONObject().put("id", it.id).put("startedAt", it.startedAt).put("paused", it.paused)
            })
            .put("moments", JSONArray().apply {
                moments.forEach {
                    put(JSONObject().put("id", it.id).put("sessionId", it.sessionId)
                        .put("requestedAt", it.requestedAt).put("elapsedSeconds", it.elapsedSeconds)
                        .put("instruction", it.instruction).put("title", it.title)
                        .put("status", it.status.name).put("readyAt", it.readyAt)
                        .put("source", it.source.name).put("author", it.author))
                }
            })
        store.edit().putString("state", data.toString()).apply()
    }

    private fun readMoments(): List<Moment> {
        val array = saved.optJSONArray("moments") ?: return emptyList()
        return List(array.length()) { index ->
            val entry = array.getJSONObject(index)
            Moment(entry.getString("id"), entry.getString("sessionId"),
                entry.getLong("requestedAt"), entry.getLong("elapsedSeconds"),
                entry.getString("instruction"), entry.getString("title"),
                MomentStatus.valueOf(entry.getString("status")), entry.optLong("readyAt"),
                MomentSource.valueOf(entry.optString("source", MomentSource.VOICE.name)),
                entry.optString("author", "운전자"))
        }
    }

    private fun demoMoments(): List<Moment> {
        val date = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        // Keep samples under today's tab even when the demo starts just after midnight.
        val first = maxOf(start.toEpochMilli(), System.currentTimeMillis() - 54 * 60_000L)
        val spacing = ((System.currentTimeMillis() - first) / 4).coerceAtLeast(0)
        val titles = listOf("터널 끝에서 처음 본 바다", "휴게소에서 고른 간식", "길을 잘못 들어 다 같이 웃은 순간", "해 질 무렵, 마지막 해안도로")
        val notes = listOf("바다가 보이는 순간부터", "커피 사러 들른 휴게소", "길을 잘못 든 얘기부터 웃음이 끝날 때까지", "노을이 가장 잘 보이는 구간")
        return titles.mapIndexedNotNull { index, title ->
            val id = "built-in-demo-$index"
            if (moments.any { it.id == id }) return@mapIndexedNotNull null
            val passenger = index % 2 == 1
            Moment(
                id = id,
                sessionId = "built-in-demo",
                requestedAt = first + spacing * index,
                elapsedSeconds = 60L + index * 181,
                instruction = notes[index],
                title = title,
                status = MomentStatus.SAMPLE,
                source = if (passenger) MomentSource.PASSENGER else MomentSource.VOICE,
                author = if (passenger) "동승자" else "운전자",
            )
        }
    }
}
