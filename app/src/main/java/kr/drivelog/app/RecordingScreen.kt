package kr.drivelog.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun RecordingScreen(state: DriveLogState, connected: ConnectedDriveState) {
    val canRequest = state.canRequestDemoShorts
    val serverTrip = connected.hasActiveTrip
    val serverPending = serverTrip && connected.pending != null
    val title = when {
        state.platformAccess == Access.UNKNOWN -> "기록 상태 확인 중"
        canRequest -> "녹음 시연 중"
        state.session?.paused == true -> "기록 일시 중지"
        else -> "주행 중"
    }
    val message = when {
        canRequest && serverTrip -> connected.markMessage ?: "연결된 운행에 방금 장면을 표시해요."
        canRequest -> state.feedback ?: "버튼을 누르면 방금 장면을 표시해요."
        else -> "기록은 주차 후 확인할 수 있어요."
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val titleSize = if (maxWidth < 600.dp) 40 else 56
        val verticalPadding = if (maxHeight < 440.dp) 16.dp else 32.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                horizontal = 24.dp,
                vertical = verticalPadding,
            ).heightIn(min = (maxHeight - verticalPadding * 2).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Copy(
                title,
                titleSize,
                strong = true,
                textAlign = TextAlign.Center,
            )
            if (canRequest) {
                RecordingWaveform()
                Copy("시연 모드 · 실제 음성 수집 없음", 20, TextMuted, textAlign = TextAlign.Center)
                Action(
                    "방금 장면 쇼츠 요청",
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = 96.dp),
                    primary = true,
                    enabled = !serverPending,
                    labelSize = 28,
                ) {
                    if (state.canRequestDemoShorts) {
                        if (connected.hasActiveTrip) connected.requestShorts() else state.requestDemoShorts()
                    }
                }
            }
            Copy(
                message,
                24,
                TextMuted,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecordingWaveform() {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "recording waveform")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 2300, easing = LinearEasing)),
        label = "wave phase",
    )
    Canvas(Modifier.widthIn(max = 340.dp).fillMaxWidth().height(76.dp)) {
        val bars = 21
        for (index in 0 until bars) {
            val position = (index + 0.5f) / bars
            val envelope = sin(position * PI).toFloat()
            val pulse = (sin(phase.value - index * 0.72f) + 1f) / 2f
            val height = size.height * (0.08f + envelope * (0.24f + pulse * 0.46f))
            val x = size.width * position
            drawLine(
                color,
                start = Offset(x, center.y - height / 2),
                end = Offset(x, center.y + height / 2),
                strokeWidth = minOf(4.dp.toPx(), size.width / bars * 0.45f),
                cap = StrokeCap.Round,
            )
        }
    }
}
