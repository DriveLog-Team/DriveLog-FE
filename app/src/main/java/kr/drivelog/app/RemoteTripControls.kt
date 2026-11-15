package kr.drivelog.app

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun RemoteTripControls(
    api: DriveLogApi,
    trip: RemoteTrip,
    accountId: String?,
    busy: Boolean,
    request: (suspend () -> Unit) -> Unit,
    updated: suspend (RemoteTrip) -> Unit,
    discarded: suspend () -> Unit,
) {
    val owner = trip.owner == accountId
    if (trip.phase == "DRIVING" && trip.isOccupant(accountId)) {
        var name by remember(trip.id) { mutableStateOf("") }
        var named by remember(trip.id) { mutableStateOf(false) }
        OutlinedTextField(name, { name = it; named = false }, enabled = !busy,
            label = { Copy("타임라인에 표시할 이름", 20) })
        Action("이름 저장", enabled = !busy && runCatching { validDisplayName(name) }.isSuccess) {
            request { api.displayName(trip.id, name); named = true; updated(api.trip(trip.id).remoteTrip()) }
        }
        if (named) Copy("이름을 저장했어요.", 20)
    }
    if (trip.phase == "DRIVING" && owner) {
        InvitePanel(api, trip, busy, request)
    }
    if (trip.phase !in listOf("DRIVING", "DISCARDED", "DISCARDING")) {
        Copy("탑승자 동의", 28, strong = true)
        trip.consentTargets.forEach { target ->
            val receipt = trip.receipts.find { it.target == target }
            val label = if (target.role == "GUARDIAN") "보호자 응답" else "본인 응답"
            Copy("${target.occupant.takeLast(8)} · $label · ${when {
                receipt == null -> "미응답"
                receipt.extraction && receipt.transcript -> "분석 동의"
                else -> "분석 거절·철회"
            }}", 20)
            if (target.actor == accountId) key(trip.id, target) {
                ConsentPanel(api, trip, target, receipt, busy, request, updated)
            }
        }
    }
    if (trip.phase == "UPLOAD" && owner) {
        UploadPanel(api, trip, busy, request, updated)
    }
    if (owner && trip.phase !in listOf("DISCARDED", "DISCARDING", "SAVING")) {
        var confirm by remember(trip.id) { mutableStateOf(false) }
        Action("이 운행 전체 삭제", enabled = !busy) { confirm = true }
        if (confirm) Confirm("운행 전체를 삭제할까요?", "${trip.id}\n모든 탑승자의 원본·편집본·보관본이 폐기됩니다.", "운행 삭제",
            dismiss = { confirm = false }) {
            confirm = false
            request {
                updated(api.discard(trip.id).remoteTrip())
                discarded()
            }
        }
    }
}

@Composable
private fun ConsentPanel(
    api: DriveLogApi, trip: RemoteTrip, target: ConsentTarget, receipt: ConsentReceipt?, busy: Boolean,
    request: (suspend () -> Unit) -> Unit, updated: suspend (RemoteTrip) -> Unit,
) {
    var extraction by remember(receipt) { mutableStateOf(receipt?.extraction ?: false) }
    var transcript by remember(receipt) { mutableStateOf(receipt?.transcript ?: false) }
    var context by remember(receipt) { mutableStateOf(receipt?.context ?: false) }
    var sharing by remember(receipt) { mutableStateOf(receipt?.sharing ?: false) }
    var confirm by remember { mutableStateOf(false) }
    ConsentOption("영상 추출에 동의", extraction, !busy) { extraction = it }
    ConsentOption("Gemini 음성 전사에 동의", transcript, !busy) { transcript = it }
    ConsentOption("위치 활용에 동의 (선택)", context, !busy) { context = it }
    ConsentOption("다른 탑승자의 표시 클립 재생에 동의 (선택)", sharing, !busy) { sharing = it }
    Action("동의 내용 제출", enabled = !busy) { confirm = true }
    if (confirm) {
        val destructive = !extraction || !transcript || receipt?.context == true && !context
        Confirm("이 동의 내용으로 제출할까요?",
            "대상 ${target.occupant.takeLast(8)} · ${if (target.role == "GUARDIAN") "보호자" else "본인"} 응답\n" +
                "영상 추출 ${if (extraction) "동의" else "거절"}, 음성 전사 ${if (transcript) "동의" else "거절"}\n" +
                "위치 활용 ${if (context) "동의" else "미동의"}, 동승자 재생 ${if (sharing) "동의" else "미동의"}\n" +
                (if (destructive) "분석을 거절·철회하거나 이미 활용한 위치 동의를 철회하면 원본·중간물·보관본 폐기가 시작될 수 있어요."
                else "공유 동의를 철회하면 동승자 재생이 중단되고 보관함이 갱신돼요."),
            "제출", dismiss = { confirm = false }) {
            confirm = false
            request {
                val fresh = api.trip(trip.id).remoteTrip()
                if (fresh.receipts.find { it.target == target } != receipt) {
                    updated(fresh)
                    throw ApiError(409)
                }
                updated(api.consent(trip.id, target.occupant, target.role,
                    extraction, transcript, context, sharing, receipt?.at ?: "none").remoteTrip())
            }
        }
    }
}

@Composable
private fun InvitePanel(api: DriveLogApi, trip: RemoteTrip, busy: Boolean, request: (suspend () -> Unit) -> Unit) {
    var under14 by remember(trip.id) { mutableStateOf(false) }
    var guardian by remember(trip.id) { mutableStateOf<String?>(null) }
    var invitation by remember(trip.id) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(invitation) {
        invitation?.let {
            delay((Instant.parse(it.getString("expiresAt")).toEpochMilli() - Instant.now().toEpochMilli()).coerceAtLeast(0))
            invitation = null
        }
    }
    val context = LocalContext.current
    DisposableEffect(context) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) invitation = null
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    Copy("동승자 초대", 28, strong = true)
    ConsentOption("만 14세 미만 동승자", under14, !busy) { under14 = it; guardian = null }
    if (under14) {
        Copy("함께 탑승한 성인 보호자를 선택하세요.", 20)
        trip.occupants.filter { !it.under14 }.forEach { seat ->
            Action("보호자 ${seat.occupant.takeLast(8)}${if (guardian == seat.occupant) " · 선택됨" else ""}", enabled = !busy) {
                guardian = seat.occupant
            }
        }
    }
    Action("한 사람용 초대 링크 발급", enabled = !busy && (!under14 || guardian != null)) {
        request { invitation = api.invite(trip.id, under14, guardian) }
    }
    invitation?.let { invite ->
        DriveDialog(dismiss = { invitation = null }) {
            Copy("동승자 초대 링크", 28, strong = true)
            Copy("30분 동안 한 사람만 참여할 수 있어요. 주차 등록 후에는 사용할 수 없어요.", 20)
            SelectionContainer { Copy(invite.getString("url"), 20) }
            Action("닫기") { invitation = null }
        }
    }
}

@Composable
private fun UploadPanel(
    api: DriveLogApi, trip: RemoteTrip, busy: Boolean, request: (suspend () -> Unit) -> Unit,
    updated: suspend (RemoteTrip) -> Unit,
) {
    val context = LocalContext.current
    var departure by remember(trip.id) { mutableStateOf("") }
    var destination by remember(trip.id) { mutableStateOf("") }
    var events by remember(trip.id) { mutableStateOf<List<DriveEvent>>(emptyList()) }
    var message by remember(trip.id) { mutableStateOf<String?>(null) }
    Copy("원본 영상 업로드", 28, strong = true)
    Copy("전방·후방 MP4를 각각 전송하세요. 두 영상이 수락되면 추천을 만들 수 있어요. 영상당 최대 8 GiB이며 지정된 음성 소스에 오디오가 필요해요.", 20)
    if (trip.allContextConsented) {
        Copy("장소를 활용하려면 두 번째 영상 전송 전에 등록하세요.", 20)
        OutlinedTextField(departure, { departure = it }, enabled = !busy, label = { Copy("출발지", 20) })
        OutlinedTextField(destination, { destination = it }, enabled = !busy, label = { Copy("도착지", 20) })
        var eventTime by remember(trip.id) { mutableStateOf("") }
        var eventLabel by remember(trip.id) { mutableStateOf("") }
        var eventKind by remember(trip.id) { mutableStateOf("LANDMARK") }
        Copy("기억할 장소 이벤트", 24, strong = true)
        OutlinedTextField(eventTime, { eventTime = it }, enabled = !busy,
            label = { Copy("운행 시작 후 시각 (초)", 20) }, singleLine = true)
        OutlinedTextField(eventLabel, { eventLabel = it }, enabled = !busy,
            label = { Copy("장소 이름", 20) }, singleLine = true)
        listOf("LANDMARK" to "명소", "NEAR_ARRIVAL" to "도착 임박", "ARRIVAL" to "도착").forEach { (kind, label) ->
            Action(label + if (eventKind == kind) " · 선택됨" else "", enabled = !busy) { eventKind = kind }
        }
        val draft = driveEvent(eventTime, eventKind, eventLabel)
        Action("장소 이벤트 추가", enabled = !busy && draft != null) {
            draft?.let { events = (events + it).sortedBy { event -> event.atMs }; eventTime = ""; eventLabel = "" }
        }
        events.forEachIndexed { index, event ->
            Copy("${millisecondsToSeconds(event.atMs)}초 · ${event.label}", 20)
            Action("이 장소 이벤트 제외", enabled = !busy) { events = events.filterIndexed { i, _ -> i != index } }
        }
        Action("출발지·도착지 등록", enabled = !busy && departure.isNotBlank() && destination.isNotBlank()) {
            request {
                updated(api.context(trip.id, departure.trim(), destination.trim(), events).remoteTrip())
                message = "출발지와 도착지를 등록했어요."
            }
        }
    }
    listOf("front" to "전방", "rear" to "후방").forEach { (side, label) ->
        key(trip.id, side) {
            var file by remember { mutableStateOf<Uri?>(null) }
            var name by remember { mutableStateOf<String?>(null) }
            var accepted by remember { mutableStateOf(false) }
            val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    file = uri
                    accepted = false
                    name = runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull() ?: "$label MP4"
                }
            }
            Copy("$label · ${if (accepted) "전송 수락됨" else name ?: "원본 선택 대기"}", 22)
            Action("$label MP4 선택", enabled = !busy) { chooser.launch(arrayOf("video/mp4")) }
            val selected = file
            Action("$label 원본 전송", enabled = !busy && selected != null && !accepted) {
                if (selected != null) request {
                    message = "$label 원본 확인·전송 중이에요."
                    try {
                        val response = api.upload(trip.id, side) {
                            context.contentResolver.openInputStream(selected) ?: throw IOException("Cannot open video")
                        }
                        accepted = true
                        message = "$label 원본을 수락했어요."
                        updated(response.remoteTrip())
                    } catch (failure: Exception) {
                        message = "$label 전송을 완료하지 못했어요. 같은 파일을 다시 보낼 수 있어요."
                        throw failure
                    }
                }
            }
        }
    }
    message?.let { Copy(it, 20) }
}

@Composable
private fun ConsentOption(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = change, enabled = enabled)
        Copy(label, 22, modifier = Modifier.weight(1f))
    }
}
