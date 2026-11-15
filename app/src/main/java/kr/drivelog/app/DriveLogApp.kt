package kr.drivelog.app

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

internal val Ground = Color(0xFFFFF8E9)
internal val Panel = Color(0xFFFFFDF7)
internal val TextMain = Color(0xFF202A38)
internal val TextMuted = Color(0xFF505D6F)
internal val Rule = Color(0xFFDAD4C7)
private val Blue = Color(0xFF2F5EA8)
private val BluePressed = Color(0xFF244C8B)
private val BlueTint = Color(0xFFE6EEF9)
private val Apricot = Color(0xFFFFBC98)
private val Red = Color(0xFFA52C38)
internal val Shape = RoundedCornerShape(18.dp)
private val JournalFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }
private val NumbersFont = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }
private val RoadPhotos = compositionLocalOf<MutableMap<String, ImageBitmap>> {
    error("DriveLog photo cache is missing")
}

@Composable
internal fun DriveLogApp(state: DriveLogState, connected: ConnectedDriveState) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val detail = state.moments.firstOrNull { it.id == detailId }
    val parked = state.access == Access.PARKED

    BackHandler(parked && (detail != null || settings || server) && state.configured) {
        detailId = null
        settings = false
        server = false
    }
    LaunchedEffect(parked) {
        if (!parked) {
            detailId = null
            settings = false
            server = false
            notice = null
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3000)
            notice = null
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
    val bodyFont = remember(assets) {
        FontFamily(
            Font("fonts/regular.ttf", assets, FontWeight.Normal),
            Font("fonts/bold.ttf", assets, FontWeight.Bold),
        )
    }
    val journalFont = remember(assets) {
        FontFamily(Font("fonts/nanum_myeongjo_bold.ttf", assets, FontWeight.Bold))
    }
    val numbersFont = remember(assets) {
        FontFamily(Font("fonts/numbers.ttf", assets, FontWeight.Bold))
    }
    CompositionLocalProvider(
        RoadPhotos provides photos,
        JournalFont provides journalFont,
        NumbersFont provides numbersFont,
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Blue, onPrimary = Color.White, primaryContainer = BlueTint, onPrimaryContainer = TextMain,
                secondary = Blue, onSecondary = Color.White, secondaryContainer = BlueTint, onSecondaryContainer = TextMain,
                tertiary = TextMain, tertiaryContainer = Apricot, onTertiaryContainer = TextMain,
                background = Ground, onBackground = TextMain, surface = Panel, onSurface = TextMain,
                surfaceVariant = BlueTint, onSurfaceVariant = TextMuted, surfaceTint = Blue,
                outline = TextMuted, outlineVariant = Rule, error = Red,
            ),
        ) {
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = bodyFont)) {
                Box(Modifier.fillMaxSize().background(Ground).safeDrawingPadding().imePadding(),
                    contentAlignment = Alignment.TopCenter) {
                    if (!parked) {
                        RecordingScreen(state, connected)
                    } else {
                        val subpage = settings || server
                        Column(
                            Modifier.widthIn(max = 1360.dp).fillMaxSize()
                                .padding(horizontal = 28.dp),
                        ) {
                            TopBar(
                                back = if (subpage) ({ settings = false; server = false }) else null,
                                settings = if (!subpage && state.configured) ({ settings = true }) else null,
                                server = if (!subpage && state.configured) ({ server = true }) else null,
                                connected = connected.api != null,
                                parkedHome = !subpage && state.configured,
                            )
                            HorizontalDivider(color = Rule)
                            key(settings, server, state.configured) {
                                Column(
                                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                                        .padding(top = 24.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    when {
                                        !state.configured || settings -> SettingsScreen(state) {
                                            settings = false
                                            notice = "설정을 저장했어요."
                                        }
                                        server -> ConnectedDriveScreen(connected)
                                        else -> ParkedHome(state) { detailId = it }
                                    }
                                }
                            }
                        }
                        if (detail != null && !subpage) {
                            MomentDetails(detail, { detailId = null }) { id, title ->
                                state.rename(id, title)
                                detailId = null
                                notice = "제목을 저장했어요."
                            }
                        }
                        val message = notice ?: state.feedback
                        if (message != null) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                                    .widthIn(max = 520.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                color = Blue,
                                shape = Shape,
                            ) {
                                Copy(message, 24, Color.White, Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    back: (() -> Unit)?,
    settings: (() -> Unit)?,
    server: (() -> Unit)?,
    connected: Boolean,
    parkedHome: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 650.dp
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (back != null) Action("뒤로", onClick = back)
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painterResource(R.drawable.ic_drivelog_mark),
                    contentDescription = if (compact && server != null) "DriveLog" else null,
                    modifier = Modifier.size(36.dp),
                )
                if (!compact || server == null) {
                    Copy("DriveLog", 32, strong = true, maxLines = 1, fontFamily = NumbersFont.current)
                }
            }
            if (parkedHome && !compact) Status("주차 중", Blue, 20)
            if (server != null) Action(if (connected) "내 운행" else "운행 연결", onClick = server)
            if (settings != null) Action("설정", onClick = settings)
        }
    }
}

@Composable
private fun ParkedHome(state: DriveLogState, onOpen: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now(zone)
    val (today, earlier) = state.moments.sortedBy { it.requestedAt }.partition {
        Instant.ofEpochMilli(it.requestedAt).atZone(zone).toLocalDate() == now
    }
    var history by rememberSaveable { mutableStateOf(false) }
    val moments = if (history) earlier.asReversed() else today
    val hasToday = today.isNotEmpty()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 650.dp
        val titleSize = if (compact) 42 else 56
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 32.dp)) {
            if (compact) Status("주차 중", Blue, 20)
            if (state.session != null) SessionPanel(state)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!history) {
                    Copy(now.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), 22, TextMuted)
                }
                Copy(
                    if (history) "지난 기록" else "오늘 남긴 순간",
                    titleSize,
                    strong = true,
                    fontFamily = JournalFont.current,
                )
                if (!history && hasToday) {
                    Row(
                        Modifier.semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Copy("${today.size}", 72, Blue, strong = true, fontFamily = NumbersFont.current)
                        Copy("개", 32, Blue, strong = true)
                    }
                }
            }

            if (hasToday && earlier.isNotEmpty()) {
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    TimelineTab("오늘", !history, Modifier.weight(1f)) { history = false }
                    TimelineTab("지난 기록", history, Modifier.weight(1f)) { history = true }
                }
            } else if (history) {
                Action("오늘로 돌아가기") { history = false }
            }

            if (moments.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(Shape).background(Panel)) {
                    moments.forEachIndexed { index, item ->
                        key(item.id) {
                            MomentRow(item, showDate = history, showDivider = index < moments.lastIndex) {
                                onOpen(item.id)
                            }
                        }
                    }
                }
            } else if (history) {
                Copy("지난 기록이 없어요.", 28, TextMuted)
            } else {
                Copy("아직 남긴 순간이 없어요.", 28, TextMuted)
                val latest = earlier.lastOrNull()
                if (latest != null) {
                    val drive = earlier.filter { it.sessionId == latest.sessionId }
                    val date = Instant.ofEpochMilli(latest.requestedAt).atZone(zone).toLocalDate()
                    RecentDriveCard(latest, date, drive.size) { history = true }
                } else {
                    Copy("드라이브에서 표시한 순간이 여기에 모여요.", 24, TextMuted)
                }
            }
        }
    }
}

@Composable
private fun RecentDriveCard(latest: Moment, date: LocalDate, count: Int, openHistory: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Shape,
        color = Panel,
        border = BorderStroke(1.dp, Rule),
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val dateLabel = date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))
            Copy("최근 드라이브 · $dateLabel · 순간 ${count}개", 22, TextMuted)
            Copy(latest.title, 38, strong = true, fontFamily = JournalFont.current)
            Action(
                "지난 기록 보기",
                Modifier.padding(top = 6.dp).widthIn(max = 440.dp).fillMaxWidth().heightIn(min = 96.dp),
                primary = true,
                labelSize = 30,
                onClick = openHistory,
            )
        }
    }
}

@Composable
private fun TimelineTab(label: String, selected: Boolean, modifier: Modifier, select: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(if (selected) Blue else Panel, tween(150), label = "timeline tab")
    Surface(
        modifier = modifier,
        color = fill,
        shape = Shape,
        border = when {
            focused -> BorderStroke(2.dp, if (selected) TextMain else Blue)
            !selected -> BorderStroke(1.dp, Rule)
            else -> null
        },
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).selectable(
                selected, interactionSource = interaction, indication = ripple(),
                role = Role.Tab, onClick = select,
            ).padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Copy(label, 24, if (selected) Color.White else TextMain, strong = selected)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionPanel(state: DriveLogState) {
    val active = state.session ?: return
    var confirmEnd by rememberSaveable(active.id) { mutableStateOf(false) }
    val count = state.moments.count { it.sessionId == active.id }
    FlowRow(
        Modifier.fillMaxWidth().clip(Shape)
            .background(if (active.paused) Apricot else BlueTint).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Status(if (active.paused) "기록 일시 중지" else "시연 기록 중", if (active.paused) TextMain else Red, 32)
            Copy(
                "${formatTime(active.startedAt)} 시작 · ${count}개 표시",
                22,
                if (active.paused) TextMain else TextMuted,
            )
            if (active.paused && !state.preferences.consent) {
                Copy("설정에서 탑승자 동의를 다시 확인하세요.", 24, TextMain)
            }
        }
        if (!active.paused) {
            Action("일시 중지", Modifier.align(Alignment.CenterVertically), onClick = state::pauseSession)
        } else if (state.preferences.consent) {
            Action("시연 계속", Modifier.align(Alignment.CenterVertically), primary = true, onClick = state::resumeSession)
        }
        Action("세션 종료", Modifier.align(Alignment.CenterVertically), onClick = { confirmEnd = true })
    }
    if (confirmEnd) {
        Confirm("드라이브를 종료할까요?", "표시한 순간은 타임라인에 남아요.", "세션 종료",
            dismiss = { confirmEnd = false }) {
            confirmEnd = false
            state.finishSession()
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
    val fill by animateColorAsState(if (enabled) BlueTint else Panel, tween(150), label = "setting")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(Shape).background(fill)
            .toggleable(enabled, role = Role.Switch, onValueChange = change)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Copy(title, 26, strong = true)
            Copy(subtitle, 20, TextMuted)
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun MomentRow(item: Moment, showDate: Boolean, showDivider: Boolean, open: () -> Unit) {
    LogRow(
        title = item.title,
        meta = "${formatTime(item.requestedAt, showDate)} · ${item.author}",
        status = "${item.source.label} · ${item.status.label}",
        highlighted = item.status == MomentStatus.SAMPLE,
        showDivider = showDivider,
        route = if (item.status == MomentStatus.SAMPLE) DriveRoute() else null,
        onClick = open,
    )
}

@Composable
internal fun LogRow(
    title: String,
    meta: String,
    status: String,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    route: DriveRoute? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val active = interaction.collectIsPressedAsState().value || interaction.collectIsFocusedAsState().value
    val fill by animateColorAsState(
        when {
            active -> BlueTint
            route != null -> Panel
            else -> Color.Transparent
        },
        tween(120),
        label = "entry",
    )
    Column {
        BoxWithConstraints(
            Modifier.fillMaxWidth().heightIn(min = 104.dp).clip(Shape).background(fill)
                .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled,
                    onClickLabel = if (route != null) "상세 보기" else null,
                    role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val routeOnRight = maxWidth >= 560.dp
            Row(
                Modifier.fillMaxWidth().align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Copy(meta, 20, TextMuted)
                    Copy(title, 26, strong = true, maxLines = 2)
                    Copy(status, 20, if (highlighted) Blue else TextMuted)
                    if (route != null && !routeOnRight) {
                        Box(Modifier.padding(top = 8.dp)) { RouteLabels(route) }
                    }
                }
                if (route != null && routeOnRight) {
                    RouteLabels(route, Modifier.weight(.72f))
                }
                Canvas(Modifier.size(24.dp)) {
                    val start = Offset(size.width * .12f, center.y)
                    val tip = Offset(size.width * .85f, center.y)
                    val wing = size.width * .38f
                    val color = if (route != null) Blue else TextMuted
                    val stroke = 2.dp.toPx()
                    if (route != null) drawLine(color, start, tip, stroke, StrokeCap.Round)
                    drawLine(color, Offset(tip.x - wing, tip.y - wing), tip, stroke, StrokeCap.Round)
                    drawLine(color, tip, Offset(tip.x - wing, tip.y + wing), stroke, StrokeCap.Round)
                }
            }
        }
        if (showDivider) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Rule)
    }
}

@Composable
private fun RouteLabels(route: DriveRoute, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (route.departure == null && route.destination == null) {
            Copy("경로 정보 없음", 20, TextMuted)
        } else {
            Copy("출발지 · ${route.departure ?: "미확인"}", 20, TextMuted)
            Copy("목적지 · ${route.destination ?: "미확인"}", 20, TextMuted)
        }
    }
}

@Composable
private fun MomentDetails(item: Moment, dismiss: () -> Unit, rename: (String, String) -> Unit) {
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var title by rememberSaveable(item.id, item.title) { mutableStateOf(item.title) }
    DriveDialog(dismiss = dismiss, wide = true) {
        Copy(item.title, 38, strong = true)
        if (item.status == MomentStatus.SAMPLE) {
            RoadPhoto(item.image, Modifier.fillMaxWidth().height(210.dp).clip(Shape))
        }
        Copy("${formatTime(item.requestedAt, true)} · ${item.author} · ${item.source.label}", 24, TextMuted)
        Status(item.status.label, if (item.status == MomentStatus.SAMPLE) Blue else TextMuted, 28)
        if (item.instruction.isNotBlank()) {
            val label = if (item.source == MomentSource.PASSENGER) "메모" else "편집 요청"
            Copy("$label · ${item.instruction}", 24)
        }
        if (item.status == MomentStatus.SAMPLE || item.status == MomentStatus.PROCESSING) {
            Copy("탐색 구간 ${formatOffset(item.startSeconds)}–${formatOffset(item.endSeconds)} · ${item.endSeconds - item.startSeconds}초", 24)
        }
        if (item.status == MomentStatus.SAMPLE) Copy("시연용 썸네일과 구간이에요.", 20, TextMuted)
        if (item.status == MomentStatus.WAITING) Copy("연결된 원본 영상이 없어요.", 20, TextMuted)
        if (editing) {
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(40) },
                label = { Copy("제목", 20, TextMuted) }, singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            )
            DialogActions("편집 취소", { editing = false; title = item.title }, "제목 저장", title.isNotBlank()) {
                rename(item.id, title)
                editing = false
            }
        } else {
            DialogActions("닫기", dismiss, "제목 편집") { editing = true }
        }
    }
}

@Composable
internal fun DriveDialog(dismiss: () -> Unit, wide: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var visible by remember { mutableStateOf(false) }
        val onDismiss by rememberUpdatedState(dismiss)
        LaunchedEffect(Unit) { visible = true }
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            })
            AnimatedVisibility(
                visible,
                enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 18 },
            ) {
                Surface(
                    Modifier.widthIn(max = if (wide) 780.dp else 570.dp).fillMaxWidth(),
                    shape = Shape,
                    color = Panel,
                    border = BorderStroke(1.dp, Rule),
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(26.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DialogActions(
    cancel: String, dismiss: () -> Unit, action: String, enabled: Boolean = true, confirm: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 440.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Action(cancel, Modifier.weight(1f), onClick = dismiss)
                Action(action, Modifier.weight(1f), primary = true, enabled = enabled, onClick = confirm)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(action, Modifier.fillMaxWidth(), primary = true, enabled = enabled, onClick = confirm)
                Action(cancel, Modifier.fillMaxWidth(), onClick = dismiss)
            }
        }
    }
}

@Composable
internal fun Confirm(title: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    DriveDialog(dismiss) {
        Copy(title, 34, strong = true)
        Copy(message, 24, TextMuted)
        DialogActions("취소", dismiss, action, confirm = confirm)
    }
}

@Composable
internal fun Action(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    labelSize: Int = 24,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        when {
            primary && (pressed || focused) -> BluePressed
            primary -> Blue
            pressed || focused -> BlueTint
            else -> Panel
        },
        tween(120),
        label = "button",
    )
    val font = LocalTextStyle.current.fontFamily
    Button(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp),
        enabled = enabled,
        shape = Shape,
        interactionSource = interaction,
        border = if (focused) BorderStroke(2.dp, TextMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = if (primary) Color.White else TextMain,
            disabledContainerColor = Panel,
            disabledContentColor = TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            fontFamily = font,
            fontSize = labelSize.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (labelSize * 1.25).sp,
        )
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
internal fun Copy(
    text: String,
    size: Int,
    color: Color = TextMain,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = (size * 1.3).sp,
        fontFamily = fontFamily, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
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

internal fun formatTime(time: Long, withDate: Boolean = false): String =
    DateTimeFormatter.ofPattern(if (withDate) "M월 d일 HH:mm" else "HH:mm", Locale.KOREAN)
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

private fun formatOffset(seconds: Long): String =
    String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
