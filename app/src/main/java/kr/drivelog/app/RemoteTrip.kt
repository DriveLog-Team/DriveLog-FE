package kr.drivelog.app

import org.json.JSONObject

internal data class RemoteSeat(val occupant: String, val under14: Boolean, val guardian: String?)
internal data class ConsentTarget(val occupant: String, val role: String, val actor: String)
internal data class ConsentReceipt(
    val target: ConsentTarget,
    val extraction: Boolean,
    val transcript: Boolean,
    val context: Boolean,
    val sharing: Boolean,
    val at: String? = null,
)
internal data class RemoteTrip(
    val id: String,
    val phase: String,
    val owner: String,
    val occupants: List<RemoteSeat>,
    val receipts: List<ConsentReceipt>,
) {
    val consentTargets: List<ConsentTarget>
        get() = occupants.flatMap { seat ->
            listOf(ConsentTarget(seat.occupant, "PASSENGER", seat.occupant)) +
                if (seat.under14 && seat.guardian != null)
                    listOf(ConsentTarget(seat.occupant, "GUARDIAN", seat.guardian)) else emptyList()
        }
    val allContextConsented: Boolean
        get() = consentTargets.isNotEmpty() && consentTargets.all { target ->
            receipts.any { it.target == target && it.extraction && it.transcript && it.context }
        }
    fun isOccupant(accountId: String?) = occupants.any { it.occupant == accountId }
}

internal fun JSONObject.remoteTrip(): RemoteTrip {
    val seats = getJSONArray("occupants")
    val decisions = getJSONArray("receipts")
    return RemoteTrip(getString("tripId"), getString("phase"), getString("owner"),
        List(seats.length()) { index ->
            val seat = seats.getJSONObject(index)
            RemoteSeat(seat.getString("occupant"), seat.getBoolean("under14"),
                if (seat.isNull("guardian")) null else seat.getString("guardian"))
        }, List(decisions.length()) { index ->
            val receipt = decisions.getJSONObject(index)
            ConsentReceipt(ConsentTarget(receipt.getString("occupant"), receipt.getString("role"), receipt.getString("actor")),
                receipt.getBoolean("extraction"), receipt.getBoolean("geminiTranscript"),
                receipt.optBoolean("useTripContext"), receipt.optBoolean("shareMarkedClips"), receipt.optString("at").takeIf { it.isNotBlank() })
        })
}
