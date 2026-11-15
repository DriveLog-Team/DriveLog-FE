package kr.drivelog.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.json.JSONObject

@Composable
internal fun RemoteClipEditor(card: JSONObject, busy: Boolean, submit: (ClipEdit) -> Unit) {
    val originalTitle = card.getString("title")
    val originalStart = card.getLong("startMs")
    val originalEnd = card.getLong("endMs")
    val cues = card.getJSONArray("cues")
    val originalCaptions = List(cues.length()) { cues.getJSONObject(it).getString("text") }
    var title by remember { mutableStateOf(originalTitle) }
    var start by remember { mutableStateOf(millisecondsToSeconds(originalStart)) }
    var end by remember { mutableStateOf(millisecondsToSeconds(originalEnd)) }
    var captions by remember { mutableStateOf(originalCaptions) }
    Copy("영상 편집", 28, strong = true)
    OutlinedTextField(title, { title = it }, label = { Copy("제목 (최대 80자)", 20) },
        enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Copy("운행 시작을 0초로 표시해요. ${millisecondsToSeconds(originalStart)}–${millisecondsToSeconds(originalEnd)}초 안에서 자를 수 있어요.", 20)
    OutlinedTextField(start, { start = it }, enabled = !busy, label = { Copy("시작 (초)", 20) }, singleLine = true)
    OutlinedTextField(end, { end = it }, enabled = !busy, label = { Copy("끝 (초)", 20) }, singleLine = true)
    captions.forEachIndexed { index, caption ->
        OutlinedTextField(caption, { value -> captions = captions.mapIndexed { i, text -> if (i == index) value else text } },
            enabled = !busy, label = { Copy("자막 ${index + 1} (최대 200자)", 20) }, modifier = Modifier.fillMaxWidth())
    }
    val draft = clipEdit(card.getString("id"), originalTitle, originalStart, originalEnd, originalCaptions,
        title, start, end, captions)
    Action("수정한 내용으로 편집", enabled = !busy && draft != null) { draft?.let(submit) }
}
