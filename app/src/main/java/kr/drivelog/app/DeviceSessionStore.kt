package kr.drivelog.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal data class DeviceSession(val origin: String, val accountId: String, val token: String)

/** Opt-in device storage. The encryption key never leaves Android Keystore. */
internal class DeviceSessionStore(
    context: Context,
    preferencesName: String = "device-session",
    private val alias: String = "drivelog-personal-session-v1",
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(session: DeviceSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val plaintext = JSONObject().put("origin", session.origin).put("accountId", session.accountId).put("token", session.token)
            .toString().toByteArray(Charsets.UTF_8)
        try {
            val ciphertext = cipher.doFinal(plaintext)
            check(preferences.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP)).commit())
        } finally { plaintext.fill(0) }
    }
    fun load(): DeviceSession? {
        val encrypted = preferences.getString("ciphertext", null) ?: return null
        return runCatching {
            val iv = Base64.decode(preferences.getString("iv", null) ?: error("Missing IV"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            val plaintext = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            try {
                val data = JSONObject(String(plaintext, Charsets.UTF_8))
                DeviceSession(data.getString("origin"), data.getString("accountId"), data.getString("token"))
            } finally { plaintext.fill(0) }
        }.getOrElse { clear(); null }
    }
    fun clear() { preferences.edit().clear().apply() }
}
