package kr.drivelog.app

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
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

    val previewFile = preview
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
    if (error != null) Copy(error.orEmpty(), 24)
    if (busy) Copy("서버에서 불러오는 중이에요.", 24)
    state.markMessage?.let { Copy(it, 24) }
    if (state.pending != null) Action("표시 다시 보내기", onClick = state::retry)

    if (preview != null) {
        val file = checkNotNull(preview)
        key(file) {
            AndroidView(
                factory = { VideoView(it).apply {
                    setVideoURI(Uri.fromFile(file))
                    setOnErrorListener { _, _, _ ->
                        error = "이 기기에서 영상 미리보기를 열 수 없어요."
                        preview = null
                        true
                    }
                    start()
                } },
                modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth().height(440.dp)
                    .background(Color.Black),
                onRelease = { it.stopPlayback() },
            )
        }
        Action("재생 닫기") { preview = null }
        return
    }

    val saved = selectedSaved
    val moment = selectedMark
    when {
        saved != null -> {
            Copy(saved.title, 32, strong = true)
            Copy("${saved.durationMs / 1000}초 · 보관함", 24)
            Action("미리보기", primary = true, enabled = !busy) {
                launchRequest { preview = api.downloadSaved(saved.tripId, context.cacheDir) }
            }
            Action("휴대폰 저장 QR", enabled = !busy) {
                launchRequest { qr = api.handoff(saved.tripId) }
            }
            qr?.let { ticket ->
                val bitmap = remember(ticket) { ticket.decodeQr()?.asImageBitmap() }
                if (bitmap != null) {
                    Image(bitmap, "휴대폰 저장 QR", Modifier.size(240.dp))
                    Copy("휴대폰으로 스캔해 MP4를 저장하세요. 한 번만 사용할 수 있어요.", 24)
                }
            }
            Action("보관함 목록", enabled = !busy) { selectedSaved = null; qr = null }
        }
        moment != null && trip != null -> {
            Copy(moment.title ?: "남긴 순간", 32, strong = true)
            MomentMetadata(moment)
            if (moment.canPlay) {
                Action("미리보기", primary = true, enabled = !busy) {
                    launchRequest { preview = api.downloadMoment(trip.id, moment.id, context.cacheDir) }
                }
            }
            Action("타임라인으로", enabled = !busy) { selectedMark = null }
        }
        trip != null && timeline != null && library == null -> {
            Copy("드라이브 타임라인", 32, strong = true)
            Copy("${formatTime(timeline.startedAt.toEpochMilli(), true)} 시작 · ${tripLabel(timeline.phase)}", 24)
            Action("타임라인 새로고침", enabled = !busy) {
                launchRequest { state.select(trip, api.timeline(trip.id)) }
            }
            if (timeline.moments.isEmpty()) Copy("아직 남긴 순간이 없어요.", 24)
            timeline.moments.forEach { item ->
                HorizontalDivider()
                MomentMetadata(item)
                Action(item.title ?: "순간 상세", enabled = !busy) { selectedMark = item }
            }
        }
    }
    if (saved == null && moment == null) {
        if (library != null) {
            Copy("보관함", 32, strong = true)
            if (library.orEmpty().isEmpty()) Copy("저장한 클립이 없어요.", 24)
            library.orEmpty().forEach { item ->
                Action(item.title, Modifier.fillMaxWidth(), enabled = !busy) { selectedSaved = item }
            }
            Action("운행 목록") { library = null }
        } else {
            Copy("내 운행", 32, strong = true)
            if (trips?.isEmpty() == true) Copy("이 계정에 연결된 운행이 없어요.", 24)
            trips.orEmpty().forEachIndexed { index, item ->
                val label = if (item.id == trip?.id) "선택됨" else "운행 ${index + 1}"
                Action("$label · ${tripLabel(item.phase)}", Modifier.fillMaxWidth(), enabled = !busy) {
                    launchRequest { state.select(item, api.timeline(item.id)) }
                }
            }
            Action("운행 목록 새로고침", enabled = !busy) { launchRequest { trips = api.trips() } }
            Action("보관함 열기", enabled = !busy) { launchRequest { library = api.library() } }
        }
        Action("연결 해제", enabled = !busy, onClick = state::disconnect)
    }
}

@Composable
private fun ConnectionForm(state: ConnectedDriveState) {
    val scope = rememberCoroutineScope()
    var origin by remember { mutableStateOf(if (BuildConfig.DEBUG) DriveLogOrigin else "") }
    var credential by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Copy("서버 연결", 40, strong = true)
    Copy(if (BuildConfig.DEBUG) "팀 서버 로그인 토큰 또는 테스트 계정의 개인 토큰을 사용하세요."
        else "팀 서버 주소와 로그인 세션 토큰을 입력하세요.", 24)
    OutlinedTextField(origin, { origin = it }, label = { Copy("HTTPS 서버 주소", 20) },
        singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp))
    OutlinedTextField(credential, { credential = it }, label = { Copy("세션 토큰", 20) },
        visualTransformation = PasswordVisualTransformation(), singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp))
    if (status != null) Copy(status.orEmpty(), 24)
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
    Action("내 운행 연결", primary = true, enabled = origin.isNotBlank() && credential.isNotBlank() && !busy) {
        val api = runCatching { DriveLogApi(origin, credential.trim()) }.getOrNull()
        if (api == null || !BuildConfig.DEBUG && origin.trim().trimEnd('/') == DriveLogOrigin) {
            status = "팀 서버의 HTTPS 주소를 확인하세요."
        } else {
            busy = true
            scope.launch {
                try {
                    credential = ""
                    state.connect(api)
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
    Copy("${formatTime(item.at.toEpochMilli(), true)} · ${if (item.kind == "TAP") "동승자 표시" else "음성 요청"}", 24)
    Copy("작성자 ${item.author.takeLast(8)}", 20)
    item.place?.let { Copy(it, 20) }
    Copy(when (item.state) {
        "MARKED" -> "표시됨 · 초안 대기"
        "CLIP_READY" -> "완성"
        "SHARING_DISABLED" -> "공유 동의 대기"
        "NO_CLIP" -> "사용 가능한 클립 없음"
        "PROCESSING", "RUNNING" -> "생성 중"
        "FAILED" -> "실패"
        else -> "처리 상태 확인 중"
    }, 20)
}

private fun tripLabel(phase: String) = when (phase) {
    "DRIVING" -> "주행 세션"
    "PENDING" -> "동의 대기"
    "UPLOAD" -> "원본 대기"
    "READY" -> "편집 준비"
    "SAVED" -> "보관 완료"
    "DISCARDING" -> "삭제 중"
    "DISCARDED" -> "폐기됨"
    else -> "상태 확인 중"
}
