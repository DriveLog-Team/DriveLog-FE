package kr.drivelog.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiContractTest {
    private val origin = "https://35.216.33.209"
    private val trip = "trip-11111111-1111-4111-8111-111111111111"

    @Test fun keepsEntireServerTripIdentifier() {
        assertEquals(trip, tripIdentifier(trip))
    }

    @Test fun rejectsBareUuidAndPathInjection() {
        listOf(trip.removePrefix("trip-"), "$trip/../other", "$trip?owner=other").forEach {
            assertThrows(IllegalArgumentException::class.java) { tripIdentifier(it) }
        }
    }

    @Test fun keepsIndividualMarkedClipUrl() {
        val path = "/api/private-trips/$trip/marks/22222222-2222-4222-8222-222222222222/clip"
        assertEquals(path, personalClipPath(origin, path))
        assertEquals(path, personalClipPath(origin, origin + path))
    }

    @Test fun refusesToSendPersonalTokenToAnotherOrigin() {
        listOf("https://example.com/api/clip", "//example.com/api/clip",
            "http://35.216.33.209/api/clip", "$origin:444/api/clip",
            "https://attacker@35.216.33.209/api/clip", "$origin/api/clip#fragment").forEach {
            assertThrows(IllegalArgumentException::class.java) { personalClipPath(origin, it) }
        }
    }

    @Test fun rejectsNonApiClipPaths() {
        assertThrows(IllegalArgumentException::class.java) { personalClipPath(origin, "/other/clip") }
    }
}
