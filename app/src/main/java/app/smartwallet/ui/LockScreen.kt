package app.smartwallet.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.smartwallet.AppLock
import kotlinx.coroutines.delay

fun biometricsAvailable(a: FragmentActivity) = BiometricManager.from(a).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

/** Why biometrics can't be used, or null when they can. */
fun biometricsProblem(a: FragmentActivity): String? = when (BiometricManager.from(a).canAuthenticate(BIOMETRIC_WEAK)) {
    BiometricManager.BIOMETRIC_SUCCESS -> null
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Set up a fingerprint or face in your phone's settings first"
    else -> "Not available on this device"
}

fun showBiometricPrompt(a: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BIOMETRIC_WEAK).build()
    )
}

/** Recovery for a forgotten PIN: the phone's own screen lock (Android 11+ can ask for it on its own). */
fun deviceCredentialAvailable(a: FragmentActivity) =
    Build.VERSION.SDK_INT >= 30 && BiometricManager.from(a).canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

fun showDeviceCredentialPrompt(a: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock SmartWallet").setAllowedAuthenticators(DEVICE_CREDENTIAL).build())
}

@Composable
private fun Key(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun LockScreen(activity: FragmentActivity) {
    val length = remember { AppLock.pinLength(activity) }
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var waitSeconds by remember { mutableLongStateOf((AppLock.waitMs(activity) + 999) / 1000) }
    val bio = remember { AppLock.biometrics(activity) && biometricsAvailable(activity) }
    val recovery = remember { deviceCredentialAvailable(activity) }

    // Count the waiting time down, and offer biometrics straight away.
    LaunchedEffect(waitSeconds > 0) {
        while (waitSeconds > 0) { delay(1000); waitSeconds = (AppLock.waitMs(activity) + 999) / 1000 }
    }
    LaunchedEffect(Unit) { if (bio && waitSeconds == 0L) showBiometricPrompt(activity, "Unlock SmartWallet") { AppLock.unlock() } }

    fun submit(pin: String) {
        when (AppLock.verify(activity, pin)) {
            AppLock.Result.OK -> AppLock.unlock()
            AppLock.Result.WRONG -> { message = "Wrong PIN" }
            AppLock.Result.WAIT -> { message = null; waitSeconds = (AppLock.waitMs(activity) + 999) / 1000 }
        }
        entered = ""
    }
    fun press(d: String) {
        if (waitSeconds > 0 || entered.length >= length) return
        message = null
        entered += d
        if (entered.length == length) submit(entered)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Green.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = Green, modifier = Modifier.size(32.dp))
        }
        Text("SmartWallet", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
        Text(if (waitSeconds > 0) "Too many tries. Wait ${waitSeconds}s" else message ?: "Enter your PIN", Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium, color = if (waitSeconds > 0 || message != null) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Row(Modifier.padding(vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(length) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < entered.length) Green else MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { d -> Key(onClick = { press(d) }) { Text(d, style = MaterialTheme.typography.headlineMedium) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (bio) Key(onClick = { showBiometricPrompt(activity, "Unlock SmartWallet") { AppLock.unlock() } }) {
                    Icon(Icons.Filled.Fingerprint, "Use biometrics", tint = Green, modifier = Modifier.size(32.dp))
                } else Spacer(Modifier.size(76.dp))
                Key(onClick = { press("0") }) { Text("0", style = MaterialTheme.typography.headlineMedium) }
                Key(onClick = { if (entered.isNotEmpty()) entered = entered.dropLast(1) }) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (recovery) TextButton({ AppLock.ignoreNextStop(); showDeviceCredentialPrompt(activity) { AppLock.unlockByRecovery() } }, Modifier.padding(top = 16.dp)) {
            Text("Forgot PIN? Use phone screen lock", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
