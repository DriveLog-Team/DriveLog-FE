package kr.drivelog.app

import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val Ground = Color(0xFF141714)
private val Panel = Color(0xFF202620)
private val TextMain = Color(0xFFF4F6EF)
private val TextMuted = Color(0xFFBDC6B9)
private val Rule = Color(0xFF424C41)
private val Lime = Color(0xFFD6F56A)
private val Red = Color(0xFFFF9791)
private val Shape = RoundedCornerShape(12.dp)
private val RoadPhotos = compositionLocalOf<MutableMap<String, ImageBitmap>> {
    error("DriveLog photo cache is missing")
}

@Composable
internal fun DriveLogApp(state: DriveLogState) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = state.moments.firstOrNull { it.id == detailId }
    val parked = state.access == Access.PARKED

    BackHandler(parked && (detail != null || settings) && state.configured) {
        detailId = null
        settings = false
    }
    LaunchedEffect(parked) {
        if (!parked) {
            detailId = null
            settings = false
        }
    }
    LaunchedEffect(state.feedback) {
        if (state.feedback != null) {
            delay(3200)
            state.clearFeedback()
        }
    }
    LaunchedEffect(state.moments) {
        val next = state.moments.filter { it.status == MomentStatus.PROCESSING }
            .minOfOrNull { it.readyAt }
        if (next != null) {
            delay((next - System.currentTimeMillis()).coerceAtLeast(0))
            state.refreshResults()
        }
    }

    val photos = remember { mutableMapOf<String, ImageBitmap>() }
    val assets = LocalContext.current.assets
    val type = remember(assets) { FontFamily(Typeface.createFromAsset(assets, "fonts/regular.ttf")) }
    CompositionLocalProvider(RoadPhotos provides photos) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Lime, onPrimary = Ground, background = Ground, onBackground = TextMain,
                surface = Panel, onSurface = TextMain, onSurfaceVariant = TextMuted, outline = Rule,
                error = Red,
            ),
        ) {
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = type)) {
                Box(Modifier.fillMaxSize().background(Ground).safeDrawingPadding().imePadding(),
                    contentAlignment = Alignment.TopCenter) {
                    if (!parked) {
                        RestrictedScreen(state)
                    } else {
                        Column(
                            Modifier.widthIn(max = 1240.dp).fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 28.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            val subpage = settings || detail != null
                            TopBar(
                                back = if (subpage) ({ settings = false; detailId = null }) else null,
                                settings = if (!subpage && state.configured) ({ settings = true }) else null,
                            )
                            when {
                                !state.configured || settings -> SettingsScreen(state) { settings = false }
                                detail != null -> MomentDetails(detail, state::rename)
                                else -> ParkedHome(state) { detailId = it }
                            }
                            if (!subpage && state.configured && state.feedback != null) {
                                Copy(state.feedback.orEmpty(), 24, Lime,
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(back: (() -> Unit)?, settings: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (back != null) Action("뒤로", onClick = back)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(27.dp)) {
                val w = size.width
                val h = size.height
                drawLine(Lime, Offset(w * .05f, h * .83f), Offset(w * .46f, h * .17f), w * .14f)
                drawLine(Lime, Offset(w * .47f, h * .83f), Offset(w * .89f, h * .17f), w * .14f)
            }
            Copy("DriveLog", 26, strong = true, maxLines = 1)
        }
        if (settings != null) Action("설정", onClick = settings)
    }
}

@Composable
private fun ParkedHome(state: DriveLogState, onOpen: (String) -> Unit) {
    val now = LocalDate.now()
    val (today, earlier) = state.moments.sortedBy { it.requestedAt }.partition {
        Instant.ofEpochMilli(it.requestedAt).atZone(ZoneId.systemDefault()).toLocalDate() == now
    }
    val active = state.session
    val activeCount = active?.let { session -> state.moments.count { it.sessionId == session.id } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Copy(now.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), 20, TextMuted)
        Copy("오늘 남긴 순간", 44, strong = true)
        if (today.isNotEmpty()) {
            Copy("오늘 ${today.size}개 · 클립 초안 ${today.count { it.status == MomentStatus.SAMPLE }}개", 24, TextMuted)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 840.dp
        val sessionContent: @Composable () -> Unit = {
            SessionPanel(state, active, activeCount)
        }
        val timeline: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Copy("타임라인", 32, strong = true)
                if (today.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Copy("아직 남긴 순간이 없어요", 24)
                        Copy("드라이브에서 표시한 장면이 여기에 모여요.", 20, TextMuted)
                    }
                }
                today.forEach { item -> MomentRow(item) { onOpen(item.id) } }
                if (earlier.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Copy("지난 순간", 32, strong = true)
                    earlier.asReversed().forEach { item -> MomentRow(item, showDate = true) { onOpen(item.id) } }
                }
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(.85f)) { sessionContent() }
                Column(Modifier.weight(1.15f)) { timeline() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                sessionContent()
                timeline()
            }
        }
    }
}

@Composable
private fun SessionPanel(state: DriveLogState, active: Session?, activeCount: Int?) {
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(Shape).background(Panel),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        RoadPhoto("sunset-road.jpg", Modifier.fillMaxWidth().height(158.dp))
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val title = when {
                active?.paused == true -> "일시 중지"
                active != null -> "시연 세션 진행 중"
                else -> "주차 중"
            }
            Status(title, if (active != null && !active.paused) Red else TextMuted, 32)
            Copy(
                when {
                    active != null -> "${formatTime(active.startedAt)} 시작 · ${activeCount ?: 0}개 표시"
                    BuildConfig.DEBUG -> "시연 모드 · 차량 원본에 연결되지 않았어요."
                    else -> "차량 기록 연결을 사용할 수 없어요."
                }, 20, TextMuted,
            )
            if (active != null) {
                Spacer(Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Action("세션 종료", Modifier.fillMaxWidth(), primary = true) { confirmEnd = true }
                    if (active.paused && state.preferences.consent) {
                        Action("시연 계속", Modifier.fillMaxWidth(), onClick = state::resumeSession)
                    } else if (active.paused) {
                        Copy("설정에서 탑승자 동의를 다시 확인하세요.", 24, TextMuted)
                    } else if (!active.paused) {
                        Action("기록 사용 중지", Modifier.fillMaxWidth(), onClick = state::pauseSession)
                    }
                }
            }
        }
    }
    if (confirmEnd) {
        Confirm(
            title = "드라이브를 종료할까요?",
            message = "표시한 순간은 타임라인에서 확인할 수 있어요.",
            action = "세션 종료",
            dismiss = { confirmEnd = false },
        ) {
            confirmEnd = false
            state.finishSession()
        }
    }
}

@Composable
private fun RestrictedScreen(state: DriveLogState) {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            Modifier.align(Alignment.BottomEnd).widthIn(max = 430.dp).clip(Shape)
                .background(Panel).padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val title = when {
                state.platformAccess == Access.UNKNOWN -> "기록 상태 확인 중"
                state.session?.paused == true -> "일시 중지"
                state.session != null -> "시연 중"
                else -> "주행 중"
            }
            Status(title, if (state.session?.paused == false) Red else TextMuted, 32)
            Copy(state.feedback ?: "주차 후 확인할 수 있어요.", 24, TextMuted)
        }
    }
}

@Composable
private fun SettingsScreen(state: DriveLogState, done: () -> Unit) {
    val current = state.preferences
    var automatic by rememberSaveable { mutableStateOf(current.automatic) }
    var location by rememberSaveable { mutableStateOf(current.location) }
    var analysis by rememberSaveable { mutableStateOf(current.analysis) }
    var consent by rememberSaveable { mutableStateOf(current.consent) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Copy(if (state.configured) "기록 설정" else "드라이브를 시작하기 전에", 40, strong = true)
    Copy("시연에서는 표시한 순간과 설정을 이 기기에 보관해요. 실제 음성이나 영상은 수집하지 않아요.", 24, TextMuted)
    ToggleRow("탑승자 동의 확인", "함께 타는 사람 모두에게 기록과 분석 목적을 설명하고 동의를 확인하세요.", consent) { consent = it }
    ToggleRow("자동 기록", "시연 주행이 시작되면 세션을 열어요.", automatic) { automatic = it }
    ToggleRow("위치 연결", "위치 연결 선택을 저장해요. 이 버전에서는 위치를 받지 않아요.", location) { location = it }
    ToggleRow("AI 분석", "세션 종료 후 시연 클립 초안을 만들어요.", analysis) { analysis = it }
    if (state.configured) {
        Copy("보관한 순간은 세션이 끝난 뒤 이 화면에서 삭제할 수 있어요.", 20, TextMuted)
    }
    Action(if (state.configured) "설정 저장" else "저장하고 시작", primary = true) {
        state.savePreferences(Preferences(automatic, location, analysis, consent))
        done()
    }
    if (state.configured && state.session == null && state.moments.isNotEmpty()) {
        Action("보관한 순간 삭제") { confirmDelete = true }
    }
    if (confirmDelete) {
        Confirm("보관한 순간을 삭제할까요?", "삭제한 순간은 다시 열 수 없어요.",
            "삭제", { confirmDelete = false }) {
            state.clearHistory()
            confirmDelete = false
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 610.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Copy(title, 26, strong = true)
                        Copy(subtitle, 20, TextMuted)
                    }
                    Action(if (enabled) "$title 켬" else "$title 끔", primary = enabled) { change(!enabled) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Copy(title, 26, strong = true)
                    Copy(subtitle, 20, TextMuted)
                    Action(if (enabled) "$title 켬" else "$title 끔", primary = enabled) { change(!enabled) }
                }
            }
        }
        HorizontalDivider(color = Rule)
    }
}

@Composable
private fun MomentRow(item: Moment, showDate: Boolean = false, open: () -> Unit) {
    OutlinedButton(
        onClick = open,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        shape = Shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
        contentPadding = PaddingValues(16.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showImage = maxWidth >= 360.dp
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (showImage) {
                    RoadPhoto(item.image, Modifier.size(100.dp, 68.dp).clip(Shape))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Copy(item.title, 24, strong = true, maxLines = 2)
                    Copy("${formatTime(item.requestedAt, showDate)} · ${item.author}", 20, TextMuted)
                    Copy("${item.source.label} · 시연", 20, TextMuted)
                    Copy(item.status.label, 20, if (item.status == MomentStatus.SAMPLE) Lime else TextMuted)
                }
            }
        }
    }
}

@Composable
private fun MomentDetails(item: Moment, rename: (String, String) -> Unit) {
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var title by rememberSaveable(item.id, item.title) { mutableStateOf(item.title) }
    Copy(item.title, 40, strong = true)
    RoadPhoto(item.image, Modifier.widthIn(max = 650.dp).fillMaxWidth().height(220.dp).clip(Shape))
    Copy(item.status.label, 24, Lime, strong = true)
    Copy("${formatTime(item.requestedAt, true)} · ${item.author}", 24, TextMuted)
    Copy("${item.source.label} · 시연", 20, TextMuted)
    if (item.instruction.isNotBlank()) {
        val label = if (item.source == MomentSource.PASSENGER) "메모" else "편집 요청"
        Copy("$label · ${item.instruction}", 24)
    }
    if (item.status == MomentStatus.SAMPLE || item.status == MomentStatus.PROCESSING) {
        Copy("탐색 범위 ${formatOffset(item.startSeconds)}–${formatOffset(item.endSeconds)} · ${item.endSeconds - item.startSeconds}초", 24)
    }
    Copy(
        if (item.status == MomentStatus.SAMPLE) "시연용 썸네일과 구간이에요. 재생할 영상은 없어요."
        else "연결된 원본 영상이 없어요.",
        20, TextMuted,
    )
    if (editing) {
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(40) },
            label = { Copy("제목", 20, TextMuted) }, singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 80.dp),
        )
        Action("제목 저장", primary = true, enabled = title.isNotBlank()) {
            rename(item.id, title)
            editing = false
        }
    } else {
        Action("제목 편집") { editing = true }
    }
}

@Composable
private fun Confirm(title: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.widthIn(max = 490.dp).padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            shape = Shape,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Copy(title, 32, strong = true)
                Copy(message, 24, TextMuted)
                Action(action, Modifier.fillMaxWidth(), primary = true, onClick = confirm)
                Action("취소", Modifier.fillMaxWidth(), onClick = dismiss)
            }
        }
    }
}

@Composable
private fun Action(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val area = modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)
    if (primary) {
        Button(onClick, area, enabled = enabled, shape = Shape) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
        }
    } else {
        OutlinedButton(onClick, area, enabled = enabled, shape = Shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, Rule)) {
            Text(label, color = TextMain, fontSize = 24.sp, lineHeight = 30.sp)
        }
    }
}

@Composable
private fun Status(text: String, color: Color, size: Int) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Copy(text, size, strong = true)
    }
}

@Composable
private fun Copy(
    text: String,
    size: Int,
    color: Color = TextMain,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = (size * 1.3).sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RoadPhoto(file: String, modifier: Modifier) {
    val assets = LocalContext.current.assets
    val cache = RoadPhotos.current
    val bitmap = remember(file, cache) {
        cache.getOrPut(file) {
            assets.open("images/$file").use {
                requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = 2
                })).asImageBitmap()
            }
        }
    }
    Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

private fun formatTime(time: Long, withDate: Boolean = false): String =
    DateTimeFormatter.ofPattern(if (withDate) "M월 d일 HH:mm" else "HH:mm", Locale.KOREAN)
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

private fun formatOffset(seconds: Long): String =
    String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
