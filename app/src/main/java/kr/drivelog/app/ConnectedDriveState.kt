package kr.drivelog.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ConnectedDriveState : ViewModel() {
    private var client by mutableStateOf<DriveLogApi?>(null)
    val api: DriveLogApi? get() = client
    var trip by mutableStateOf<RemoteTrip?>(null)
        private set
    var timeline by mutableStateOf<RemoteTimeline?>(null)
        private set
    var markMessage by mutableStateOf<String?>(null)
        private set
    var pending by mutableStateOf<PendingMark?>(null)
        private set
    private var submission: Job? = null
    private val sent = mutableSetOf<Pair<String, String>>()

    val hasActiveTrip: Boolean
        get() = api != null && trip != null && timeline?.phase == "DRIVING"

    fun requestShorts() {
        if (hasActiveTrip) send(command = null)
    }

    fun connect(api: DriveLogApi) {
        disconnect()
        client = api
    }

    fun disconnect() {
        submission?.cancel()
        pending = null
        sent.clear()
        trip = null
        timeline = null
        markMessage = null
        client = null
    }

    fun select(value: RemoteTrip, data: RemoteTimeline) {
        trip = value
        timeline = data
    }

    fun send(command: String?, requestId: String? = null) {
        val selected = trip ?: return
        val data = timeline ?: return
        if (data.phase !in listOf("DRIVING", "PENDING", "UPLOAD")) return
        val id = if (requestId == null) UUID.randomUUID().toString()
            else runCatching { UUID.fromString(requestId).toString() }.getOrNull() ?: return
        val offset = Instant.now().toEpochMilli() - data.startedAt.toEpochMilli()
        if (offset < 0 || (selected.id to id) in sent || pending != null || submission?.isActive == true) return
        pending = PendingMark(selected.id, id, offset, command)
        retry()
    }

    fun retry() {
        val api = client ?: return
        val request = pending ?: return
        if (submission?.isActive == true) return
        markMessage = "순간을 보내는 중이에요."
        submission = viewModelScope.launch {
            try {
                api.mark(request.tripId, request.id, request.atMs, request.command)
                sent += request.tripId to request.id
                pending = null
                markMessage = "이 장면을 표시했어요."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val permanent = (error as? ApiError)?.code in listOf(400, 403, 404, 409, 410, 422)
                if (permanent) pending = null
                markMessage = if (permanent) error.serverMessage() else "순간 전송 대기 · ${error.serverMessage()}"
            }
        }
    }

    internal data class PendingMark(val tripId: String, val id: String, val atMs: Long, val command: String?)
}
