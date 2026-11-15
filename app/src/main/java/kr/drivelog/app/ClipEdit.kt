package kr.drivelog.app

import java.math.BigDecimal

internal data class ClipEdit(val id: String, val title: String?, val startMs: Long?, val endMs: Long?, val captions: List<String>?)

internal fun secondsToMs(value: String): Long? = runCatching {
    BigDecimal(value.trim()).movePointRight(3).longValueExact()
}.getOrNull()
internal fun millisecondsToSeconds(value: Long): String = BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString()

internal fun clipEdit(
    id: String, originalTitle: String, originalStart: Long, originalEnd: Long, originalCaptions: List<String>,
    title: String, start: String, end: String, captions: List<String>,
): ClipEdit? {
    val startMs = secondsToMs(start) ?: return null
    val endMs = secondsToMs(end) ?: return null
    if (title.isBlank() || title.length > 80 || startMs < originalStart || endMs > originalEnd || startMs >= endMs) return null
    if (captions.size != originalCaptions.size || captions.any { it.length > 200 }) return null
    val draft = ClipEdit(id, title.takeIf { it != originalTitle }, startMs.takeIf { it != originalStart },
        endMs.takeIf { it != originalEnd }, captions.takeIf { it != originalCaptions })
    return draft.takeIf { it.title != null || it.startMs != null || it.endMs != null || it.captions != null }
}
