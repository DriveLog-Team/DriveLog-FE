package kr.drivelog.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** A job ID is retained per trip on this device; credentials are never persisted here. */
@Composable
internal fun RemoteRecommendations(
    api: DriveLogApi,
    tripId: String,
    phase: String,
    busy: Boolean,
    request: (suspend () -> Unit) -> Unit,
    preview: (File) -> Unit,
    saved: suspend () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("remote-jobs", 0) }
    var jobId by remember(api, tripId) { mutableStateOf(preferences.getString(tripId, null)) }
    var status by remember(api, tripId) { mutableStateOf<JSONObject?>(null) }
    var pendingSave by remember(api, tripId) {
        mutableStateOf(preferences.getString("$tripId:save", null)?.let { stored ->
            runCatching { JSONObject(stored) }.getOrNull()
        })
    }
    var selected by remember(api, tripId) { mutableStateOf(pendingSave?.optString("moment")) }

    var qr by remember(api, tripId) { mutableStateOf<Handoff?>(null) }
    var pollingError by remember(api, tripId) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var saveMessage by remember(api, tripId) { mutableStateOf<String?>(null) }

    fun follow(id: String) {
        preferences.edit().putString(tripId, id).apply()
        jobId = id
        status = null
        selected = null
        qr = null
    }

    suspend fun save(version: String, moment: String) {
        val selection = JSONObject().put("jobId", version).put("moment", moment)
        // Persist the exact choice before sending; a lost response must not change the version on retry.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            check(preferences.edit().putString("$tripId:save", selection.toString()).commit())
        }
        pendingSave = selection
        var result = api.finalize(tripId, version, moment)
        saveMessage = "영상을 보관하는 중이에요."
        while (result.getString("phase") == "SAVING") {
            delay(2500)
            result = api.trip(tripId)
        }
        if (result.getString("phase") == "SAVED") {
            preferences.edit().remove("$tripId:save").apply()
            pendingSave = null
            saveMessage = "보관이 완료됐어요."
            saved()
        } else saveMessage = "운행 상태를 새로고침해 주세요."
    }

    LaunchedEffect(api, tripId, jobId, refresh) {
        val id = jobId ?: return@LaunchedEffect
        pollingError = null
        try {
            while (true) {
                val result = api.job(id)
                status = result
                if (result.getString("state") !in listOf("QUEUED", "RUNNING")) break
                delay(2500)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            pollingError = error.serverMessage()
        }
    }
    LaunchedEffect(qr) {
        qr?.let { delay((it.expiresAt.toEpochMilli() - Instant.now().toEpochMilli()).coerceAtLeast(0)); qr = null }
    }
    DisposableEffect(context) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) qr = null
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    if (phase != "READY" && jobId == null) return
    Copy("추천 영상", 28, strong = true)
    if (jobId == null && pendingSave == null) {
        Action("15초 추천 만들기", enabled = !busy) { request { follow(api.createJob(tripId)) } }
    }
    pollingError?.let {
        Copy(it, 20)
        Action("작업 조회 다시 시도", enabled = !busy) { refresh++ }
    }
    when (status?.optString("state")) {
        "QUEUED" -> Copy("추천 작업 대기 중이에요.", 22)
        "RUNNING" -> Copy("추천 영상을 만드는 중이에요.", 22)
        "FAILED" -> {
            Copy("추천 작업이 실패했어요.", 22)
            Action("새 추천 작업 요청", enabled = !busy && pendingSave == null && phase == "READY") {
                request { follow(api.createJob(tripId)) }
            }
        }
        "READY" -> {
            val cards = status?.optJSONObject("result")?.optJSONArray("cards")
            if (cards == null || cards.length() == 0) Copy("추천할 장면이 없어요.", 22)
            else for (index in 0 until cards.length()) {
                val card = cards.getJSONObject(index)
                val id = card.getString("id")
                LogRow(card.getString("title"), card.optString("summary"),
                    "${(card.getLong("endMs") - card.getLong("startMs")) / 1000}초",
                    highlighted = selected == id, enabled = !busy && pendingSave == null) {
                    selected = id
                    qr = null
                }
            }
        }
    }
    saveMessage?.let { Copy(it, 20) }
    pendingSave?.let { selection ->
        Copy("저장 중이던 영상 선택을 유지하고 있어요.", 20)
        Action("같은 영상 보관 다시 시도", enabled = !busy) {
            request { save(selection.getString("jobId"), selection.getString("moment")) }
        }
    }
    val id = selected
    val version = jobId
    if (id != null && version != null && status?.optString("state") == "READY") {
        Action("선택 영상 재생", enabled = !busy) {
            request { preview(api.downloadCard(version, id, context.cacheDir)) }
        }
        val cards = status?.optJSONObject("result")?.optJSONArray("cards")
        val card = cards?.let { array -> (0 until array.length()).map { array.getJSONObject(it) }.find { it.getString("id") == id } }
        if (card != null) key(version, id) {
            RemoteClipEditor(card, busy || pendingSave != null) { draft -> request { follow(api.edit(version, draft)) } }
        }
        Action("선택 영상 QR", enabled = !busy && pendingSave == null) {
            request { qr = api.cardHandoff(version, id) }
        }
        var confirm by remember { mutableStateOf(false) }
        Action("선택 영상 보관", primary = true, enabled = !busy && pendingSave == null && phase !in listOf("SAVED", "DISCARDED")) {
            confirm = true
        }
        if (confirm) Confirm("이 영상을 보관할까요?", "선택한 편집 버전과 각자 고른 표시 클립을 보관해요.", "보관",
            dismiss = { confirm = false }) {
            confirm = false
            request {
                save(version, id)
            }
        }
    }
    qr?.let { ticket ->
        DriveDialog(dismiss = { qr = null }) {
            Copy("선택 영상 휴대폰 저장", 28, strong = true)
            val bitmap = remember(ticket) { ticket.decodeQr()?.asImageBitmap() }
            if (bitmap != null) Image(bitmap, "선택 영상 QR", Modifier.size(240.dp))
            Copy("5분 동안 한 번 사용할 수 있어요.", 20)
            Action("QR 회수", enabled = !busy) { request { api.revokeHandoff(ticket.id); qr = null } }
            Action("닫기") { qr = null }
        }
    }
}
