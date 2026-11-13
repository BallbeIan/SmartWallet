package app.smartwallet

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional PIN lock. The PIN is never stored: only a salted PBKDF2 hash and its length. Whether the app is
 * currently locked is process-wide Compose state, so it survives screen rotation; a new process starts locked.
 */
object AppLock {
    enum class Result { OK, WRONG, WAIT }

    private const val PREFS = "applock"
    private const val ITERATIONS = 100_000

    /** True while the lock screen must be shown instead of the app. */
    var locked by mutableStateOf(false)
        private set

    /** Set after the user proved they own the phone with its screen lock; lets them choose a new PIN without the old one. */
    var recovered by mutableStateOf(false)
        private set

    private var initialized = false
    private var stoppedAt = 0L
    private var ignoreNextStop = false

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).contains("hash")
    fun pinLength(c: Context) = prefs(c).getInt("length", 4)
    fun biometrics(c: Context) = enabled(c) && prefs(c).getBoolean("biometrics", false)
    fun setBiometrics(c: Context, on: Boolean) = prefs(c).edit().putBoolean("biometrics", on).apply()
    fun timeoutMs(c: Context) = prefs(c).getLong("timeout", 60_000L)
    fun setTimeoutMs(c: Context, ms: Long) = prefs(c).edit().putLong("timeout", ms).apply()

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).encoded

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit().putString("hash", hex(hash(pin, salt))).putString("salt", hex(salt)).putInt("length", pin.length)
            .putInt("failures", 0).putLong("until", 0).apply()
        recovered = false
    }

    /** Turns the lock off completely. */
    fun clear(c: Context) {
        prefs(c).edit().clear().apply()
        locked = false
        recovered = false
    }

    fun waitMs(c: Context): Long = (prefs(c).getLong("until", 0) - System.currentTimeMillis()).coerceAtLeast(0)

    /** Checks a PIN. Five wrong tries in a row start a growing waiting time (30 s, 1 min, 2 min, ... up to 15 min). */
    fun verify(c: Context, pin: String): Result {
        if (waitMs(c) > 0) return Result.WAIT
        val p = prefs(c)
        val stored = p.getString("hash", null) ?: return Result.OK
        val ok = MessageDigest.isEqual(unhex(stored), hash(pin, unhex(p.getString("salt", "")!!)))
        if (ok) { p.edit().putInt("failures", 0).putLong("until", 0).apply(); return Result.OK }
        val failures = p.getInt("failures", 0) + 1
        val edit = p.edit().putInt("failures", failures)
        if (failures % 5 == 0) {
            val seconds = (30L shl ((failures / 5 - 1).coerceAtMost(5))).coerceAtMost(900)
            edit.putLong("until", System.currentTimeMillis() + seconds * 1000)
        }
        edit.apply()
        return if (failures % 5 == 0) Result.WAIT else Result.WRONG
    }

    // --- lifecycle, driven by MainActivity ---

    fun onCreate(c: Context) {
        if (!initialized) { initialized = true; locked = enabled(c) }
    }

    /** Call before sending the user to a system screen (file picker, permission dialog) so coming back doesn't lock the app. */
    fun ignoreNextStop() { ignoreNextStop = true }

    fun onStop() {
        if (ignoreNextStop) { ignoreNextStop = false; return }
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onStart(c: Context) {
        if (enabled(c) && !locked && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt >= timeoutMs(c)) locked = true
        stoppedAt = 0
    }

    fun unlock() { locked = false }
    fun unlockByRecovery() { recovered = true; locked = false }
}
