package kr.drivelog.app

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ConnectedDriveScreen(state: ConnectedDriveState) {
    val api = state.api
    if (api == null) {
        ConnectionForm(state)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trips by remember(api) { mutableStateOf<List<RemoteTrip>?>(null) }
    var library by remember(api) { mutableStateOf<List<SavedClip>?>(null) }
    var error by remember(api) { mutableStateOf<String?>(null) }
    var busy by remember(api) { mutableStateOf(false) }
    var selectedMark by remember(api) { mutableStateOf<RemoteMoment?>(null) }
    var selectedSaved by remember(api) { mutableStateOf<SavedClip?>(null) }
    var preview by remember(api) { mutableStateOf<File?>(null) }
    var disconnect by remember(api) { mutableStateOf(false) }
    var qr by remember(api) { mutableStateOf<Handoff?>(null) }
    val trip = state.trip
    val timeline = state.timeline

    fun launchRequest(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.serverMessage()
            } finally {
                busy = false
            }
        }
    }

    suspend fun refreshTrip(value: RemoteTrip) {
        state.select(value, state.timeline?.takeIf { state.trip?.id == value.id }?.copy(phase = value.phase))
        if (value.isOccupant(state.accountId) && value.phase !in listOf("DISCARDING", "DISCARDED")) {
            state.select(value, api.timeline(value.id))
        } else state.select(value, null)
        trips = api.trips()
    }

    val previewFile = preview
    LaunchedEffect(api, previewFile, selectedSaved, selectedMark, trip?.id) {
        if (previewFile == null) return@LaunchedEffect
        try {
            while (true) {
                delay(5000)
                val savedClip = selectedSaved
                val markedClip = selectedMark
                when {
                    savedClip != null -> {
                        val current = api.library()
                        val exists = current.any {
                            it.tripId == savedClip.tripId && it.markId == savedClip.markId && it.clipUrl == savedClip.clipUrl
                        }
                        library = current
                        if (!exists) {
                            preview = null
                            selectedSaved = null
                            error = "공유 동의 또는 보관 상태가 바뀌어 재생을 닫았어요."
                            break
                        }
                    }
                    markedClip != null && trip != null -> {
                        val current = api.timeline(trip.id)
                        state.select(trip.copy(phase = current.phase), current)
                        if (current.moments.none { it.id == markedClip.id && it.canPlay }) {
                            preview = null
                            selectedMark = null
                            error = "공유 동의 또는 클립 상태가 바뀌어 재생을 닫았어요."
                            break
                        }
                    }
                    trip != null -> {
                        val current = api.trip(trip.id).remoteTrip()
                        if (current.phase in listOf("DISCARDING", "DISCARDED")) {
                            preview = null
                            state.select(current, null)
                            break
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            preview = null
            error = failure.serverMessage()
        }
    }
    DisposableEffect(previewFile) {
        onDispose { previewFile?.delete() }
    }
    DisposableEffect(context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                preview = null
                qr = null
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(qr) {
        qr?.let {
            delay((it.expiresAt.toEpochMilli() - Instant.now().toEpochMilli()).coerceAtLeast(0))
            qr = null
        }
    }
    LaunchedEffect(api) {
        launchRequest { trips = api.trips() }
    }

    Copy("연결된 드라이브", 40, strong = true)
    AnimatedVisibility(busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    error?.let { ServerNotice(it) }
    state.markMessage?.let { ServerNotice(it) }
    if (state.pending != null) Action("표시 다시 보내기", onClick = state::retry)

    if (previewFile != null) {
        DriveDialog(dismiss = { preview = null }, wide = true) {
            Copy("클립 미리보기", 32, strong = true)
            key(previewFile) {
                AndroidView(
                    factory = { VideoView(it).apply {
                        setVideoURI(Uri.fromFile(previewFile))
                        setOnErrorListener { _, _, _ ->
                            error = "이 기기에서 영상 미리보기를 열 수 없어요."
                            preview = null
                            true
                        }
                        start()
                    } },
                    modifier = Modifier.fillMaxWidth().height(350.dp).background(Color.Black),
                    onRelease = { it.stopPlayback() },
                )
            }
            Action("재생 닫기") { preview = null }
        }
    }

    val saved = selectedSaved
    val moment = selectedMark
    when {
        saved != null -> {
            Copy(saved.title, 32, strong = true)
            Copy("${saved.durationMs / 1000}초 · 보관함", 24)
            Action("미리보기", primary = true, enabled = !busy) {
                launchRequest { preview = api.downloadSaved(saved, context.cacheDir) }
            }
            if (saved.markId == null) Action("휴대폰 저장 QR", enabled = !busy) {
                launchRequest { qr = api.handoff(saved.tripId) }
            }
            qr?.let { ticket ->
                DriveDialog(dismiss = { qr = null }) {
                    Copy("휴대폰에서 클립 저장", 32, strong = true)
                    val bitmap = remember(ticket) { ticket.decodeQr()?.asImageBitmap() }
                    if (bitmap != null) {
                        Image(bitmap, "휴대폰 저장 QR", Modifier.size(240.dp))
                        Copy("스캔해 MP4를 저장하세요. 한 번만 사용할 수 있어요.", 24, TextMuted)
                    } else {
                        Copy("QR을 표시할 수 없어요.", 24)
                    }
                    Action("QR 회수", enabled = !busy) {
                        launchRequest { api.revokeHandoff(ticket.id); qr = null }
                    }
                    Action("닫기") { qr = null }
                }
            }
            Action("보관함 목록", enabled = !busy) { selectedSaved = null; qr = null }
        }
        moment != null && trip != null -> {
            Copy(moment.title ?: "남긴 순간", 32, strong = true)
            MomentMetadata(moment)
            if (moment.canPlay) {
                Action("미리보기", primary = true, enabled = !busy) {
                    launchRequest { preview = api.downloadMoment(trip.id, moment, context.cacheDir) }
                }
            }
            if (moment.canPlay && trip.isOccupant(state.accountId) && trip.phase !in listOf("SAVED", "DISCARDED", "DISCARDING")) {
                Action("내 앨범에 이 표시 선택", enabled = !busy) {
                    launchRequest {
                        api.pick(trip.id, moment.id)
                        refreshTrip(api.trip(trip.id).remoteTrip())
                        selectedMark = null
                    }
                }
            }
            Action("타임라인으로", enabled = !busy) { selectedMark = null }
        }
        trip != null && library == null -> {
            Copy("드라이브 타임라인", 32, strong = true)
            Copy(tripLabel(trip.phase), 24)
            timeline?.let { Copy("${formatTime(it.startedAt.toEpochMilli(), true)} 시작", 24) }
            Action("타임라인 새로고침", enabled = !busy) {
                launchRequest { refreshTrip(api.trip(trip.id).remoteTrip()) }
            }
            RemoteTripControls(api, trip, state.accountId, busy, ::launchRequest,
                updated = ::refreshTrip, discarded = { library = null; selectedMark = null; preview = null })
            if (trip.owner == state.accountId && trip.phase !in listOf("DISCARDED", "DISCARDING"))
            RemoteRecommendations(api, trip.id, trip.phase, busy, ::launchRequest,
                preview = { preview = it }, saved = {
                    refreshTrip(api.trip(trip.id).remoteTrip())
                    library = api.library()
                })
            if (timeline?.moments?.isEmpty() == true) Copy("아직 남긴 순간이 없어요.", 24)
            val route = timeline?.route
            timeline?.moments.orEmpty().forEach { item ->
                LogRow(
                    item.title ?: "남긴 순간",
                    "${formatTime(item.at.toEpochMilli(), true)} · ${markSource(item)}",
                    momentLabel(item.state),
                    highlighted = item.canPlay,
                    enabled = !busy,
                    route = if (item.canPlay) route else null,
                ) { selectedMark = item }
            }
        }
    }
    if (saved == null && moment == null) {
        if (library != null) {
            Copy("보관함", 32, strong = true)
            if (library.orEmpty().isEmpty()) Copy("저장한 클립이 없어요.", 24)
            library.orEmpty().forEach { item ->
                LogRow(
                    title = item.title,
                    meta = "${item.durationMs / 1000}초",
                    status = "보관 완료",
                    enabled = !busy,
                    route = item.route,
                ) {
                    selectedSaved = item
                }
            }
            Action("운행 목록") { library = null }
        } else {
            Copy("내 운행", 32, strong = true)
            if (trips?.isEmpty() == true) Copy("이 계정에 연결된 운행이 없어요.", 24)
            trips.orEmpty().forEachIndexed { index, item ->
                val selected = item.id == trip?.id
                LogRow("운행 ${index + 1}", tripLabel(item.phase),
                    if (selected) "선택됨" else "타임라인 열기", highlighted = selected, enabled = !busy) {
                    launchRequest { refreshTrip(api.trip(item.id).remoteTrip()) }
                }
            }
            Action("운행 목록 새로고침", enabled = !busy) { launchRequest { trips = api.trips() } }
            Action("보관함 열기", enabled = !busy) { launchRequest { library = api.library() } }
        }
        Action("연결 해제", enabled = !busy) { disconnect = true }
    }
    if (disconnect) {
        Confirm("서버 연결을 해제할까요?", "다시 연결하려면 세션 토큰을 입력해야 해요.", "연결 해제",
            dismiss = { disconnect = false }) {
            disconnect = false
            state.disconnect()
        }
    }
}

@Composable
private fun ConnectionForm(state: ConnectedDriveState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var origin by remember { mutableStateOf(DriveLogOrigin) }
    var account by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Copy("서버 연결", 40, strong = true)
    Copy("발급받은 개인 토큰을 입력하세요. 팀 서버 토큰은 차량에 입력하지 마세요.", 24)
    OutlinedTextField(origin, { origin = it }, label = { Copy("HTTPS 서버 주소", 20) },
        singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp))
    OutlinedTextField(account, { account = it }, label = { Copy("발급받은 accountId", 20) },
        singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp))
    val validAccount = runCatching { java.util.UUID.fromString(account.trim()).toString() }.getOrNull()
    OutlinedTextField(credential, { credential = it }, label = { Copy("세션 토큰", 20) },
        visualTransformation = PasswordVisualTransformation(), singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp))
    AnimatedVisibility(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    status?.let { ServerNotice(it) }
    Action("음성 표시 연동", enabled = !busy) {
        context.startActivity(Intent(context, DriveMomentsActivity::class.java))
    }
    Action("서버 상태 확인", enabled = origin.isNotBlank() && !busy) {
        busy = true
        scope.launch {
            try {
                val value = DriveLogApi(origin, "").status()
                status = if (value == "ready") "서버가 준비됐어요." else "서버가 아직 준비되지 않았어요."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.serverMessage()
            } finally {
                busy = false
            }
        }
    }
    Action("내 운행 연결", primary = true, enabled = origin.isNotBlank() && credential.isNotBlank() && validAccount != null && !busy) {
        val api = runCatching { DriveLogApi(origin, credential.trim()) }.getOrNull()
        if (api == null) {
            status = "팀 서버의 HTTPS 주소를 확인하세요."
        } else {
            busy = true
            scope.launch {
                try {
                    api.trips()
                    credential = ""
                    state.connect(api, requireNotNull(validAccount))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status = error.serverMessage()
                } finally {
                    busy = false
                }
            }
        }
    }
}

@Composable
private fun MomentMetadata(item: RemoteMoment) {
    Copy("${formatTime(item.at.toEpochMilli(), true)} · ${markSource(item)}", 24)
    Copy("작성자 ${item.author}", 20)
    item.place?.let { Copy(it, 20) }
    Copy(momentLabel(item.state), 20)
}

@Composable
private fun ServerNotice(message: String) {
    Surface(color = Panel, shape = Shape) {
        Copy(message, 24, TextMain, Modifier.fillMaxWidth().padding(18.dp))
    }
}

private fun markSource(item: RemoteMoment) = if (item.kind == "TAP") "동승자 표시" else "음성 요청"

private fun momentLabel(state: String) = when (state) {
        "MARKED" -> "표시됨 · 초안 대기"
        "CLIP_READY" -> "완성"
        "SHARING_DISABLED" -> "공유 동의 대기"
        "NO_CLIP" -> "사용 가능한 클립 없음"
        "PROCESSING", "RUNNING" -> "생성 중"
        "FAILED" -> "실패"
        else -> "처리 상태 확인 중"
}

private fun tripLabel(phase: String) = when (phase) {
    "DRIVING" -> "주행 세션"
    "PENDING" -> "동의 대기"
    "UPLOAD" -> "원본 대기"
    "READY" -> "편집 준비"
    "SAVING" -> "보관 중"
    "SAVED" -> "보관 완료"
    "DISCARDING" -> "삭제 중"
    "DISCARDED" -> "폐기됨"
    else -> "상태 확인 중"
}
