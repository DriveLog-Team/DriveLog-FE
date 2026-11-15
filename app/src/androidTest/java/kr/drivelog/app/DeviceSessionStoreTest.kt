package kr.drivelog.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSessionStoreTest {
    @Test fun encryptsRoundTripsAndClearsUsingRealAndroidKeystore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-session-${UUID.randomUUID()}"
        val store = DeviceSessionStore(context, namespace, namespace)
        val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        val session = DeviceSession("https://35.216.33.209", UUID.randomUUID().toString(), "instrumentation-only-token")
        try {
            assertNull(store.load())
            store.save(session)
            assertEquals(session, store.load())
            assertFalse(preferences.all.toString().contains(session.token))
            store.save(session.copy(token = "replacement-test-token"))
            assertEquals("replacement-test-token", store.load()?.token)
            store.clear()
            assertNull(store.load())
        } finally {
            preferences.edit().clear().commit()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(namespace) }
        }
    }
}
