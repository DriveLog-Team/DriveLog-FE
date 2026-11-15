package kr.drivelog.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteTripTest {
    private val adult = "22222222-2222-4222-8222-222222222222"
    private val child = "33333333-3333-4333-8333-333333333333"
    private val target = ConsentTarget(child, "GUARDIAN", adult)
    private val trip = RemoteTrip("trip-11111111-1111-4111-8111-111111111111", "PENDING", adult,
        listOf(RemoteSeat(adult, false, null), RemoteSeat(child, true, adult)), emptyList())

    @Test fun childRequiresBothPassengerAndDesignatedGuardian() {
        assertEquals(setOf(ConsentTarget(adult, "PASSENGER", adult),
            ConsentTarget(child, "PASSENGER", child), target), trip.consentTargets.toSet())
        assertEquals(2, trip.consentTargets.count { it.actor == adult })
        assertEquals(1, trip.consentTargets.count { it.actor == child })
    }

    @Test fun locationRequiresEveryReceiptIncludingGuardian() {
        val receipts = trip.consentTargets.map { ConsentReceipt(it, true, true, true, false) }
        assertTrue(trip.copy(receipts = receipts).allContextConsented)
        assertFalse(trip.copy(receipts = receipts.filterNot { it.target == target }).allContextConsented)
        assertFalse(trip.copy(receipts = receipts.map { if (it.target == target) it.copy(context = false) else it }).allContextConsented)
        assertFalse(trip.copy(receipts = receipts.map { it.copy(transcript = false) }).allContextConsented)
    }

    @Test fun guardianOnlyAccountIsNotTreatedAsPassenger() {
        val guardianOnly = trip.copy(occupants = listOf(RemoteSeat(child, true, adult)))
        assertFalse(guardianOnly.isOccupant(adult))
        assertTrue(guardianOnly.consentTargets.any { it.actor == adult })
    }

    @Test fun parsesProductionTripAndRetainsReceiptActor() {
        val parsed = JSONObject("""{"tripId":"${trip.id}","phase":"PENDING","owner":"$adult",
            "occupants":[{"occupant":"$child","under14":true,"guardian":"$adult"}],
            "receipts":[{"occupant":"$child","role":"GUARDIAN","actor":"$adult",
            "extraction":true,"geminiTranscript":true,"useTripContext":false,"shareMarkedClips":true}]}""").remoteTrip()
        assertEquals(target, parsed.receipts.single().target)
        assertTrue(parsed.receipts.single().sharing)
        assertFalse(parsed.allContextConsented)
    }
}
