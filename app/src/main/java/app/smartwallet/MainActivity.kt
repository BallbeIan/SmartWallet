package app.smartwallet

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import app.smartwallet.ui.*

// FragmentActivity (a ComponentActivity subclass) is what the biometric prompt needs.
class MainActivity : FragmentActivity() {
    private fun applySystemBars(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart(this)
        // Keep app contents out of the recent-apps thumbnail while a lock is set (Android 13+).
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!AppLock.enabled(this))
    }

    // Due scheduled payments are added whenever the app comes to the front, so nothing is missed overnight.
    override fun onResume() {
        super.onResume()
        androidx.lifecycle.ViewModelProvider(this)[WalletViewModel::class.java].processDue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.load(this)
        applySystemBars(AppTheme.isDark)
        // Debug only: `adb shell am start ... --ez demo true` loads sample data without touching the screen.
        app.smartwallet.data.Money.load(this)
        AppLock.onCreate(this)
        Reminders.schedule(this)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("remind", false)) Reminders.runNow(this, force = true)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) {
            androidx.lifecycle.ViewModelProvider(this)[WalletViewModel::class.java].loadDemoData()
        }
        // While locked, the app itself is not even composed, so nothing behind the lock screen can be read or tapped.
        setContent {
            // Status and navigation bar icons need to flip with the theme.
            val dark = AppTheme.isDark
            SideEffect { applySystemBars(dark) }
            WalletTheme { Surface { if (AppLock.locked) LockScreen(this@MainActivity) else WalletApp() } }
        }
    }
}

private data class Tab(val label: String, val outlined: ImageVector, val filled: ImageVector)

/** A flat, slightly translucent bar with a hairline on top: icon over label, the chosen tab in the accent colour. */
@Composable
private fun TabBar(tabs: List<Tab>, selected: Int?, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f)) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 7.dp, bottom = 3.dp)) {
                tabs.forEachIndexed { i, t ->
                    val on = selected == i
                    val tint = if (on) Green else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }.padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(if (on) t.filled else t.outlined, t.label, tint = tint, modifier = Modifier.size(26.dp))
                        Text(t.label, color = tint, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun WalletApp(vm: WalletViewModel = viewModel()) {
    val tabs = listOf(
        Tab("Home", Icons.Outlined.Home, Icons.Rounded.Home),
        Tab("History", Icons.Outlined.Schedule, Icons.Rounded.Schedule),
        Tab("Budgets", Icons.Outlined.Savings, Icons.Rounded.Savings),
        Tab("Reports", Icons.Outlined.PieChart, Icons.Rounded.PieChart),
        Tab("Accounts", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    )
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var overlay by rememberSaveable { mutableIntStateOf(0) } // 0 none, 1 settings, 2 scheduled payments
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    // A backup file the user picked is confirmed here, so it works whichever screen is showing.
    val pendingImport by vm.pendingImport.collectAsState()
    pendingImport?.let { info -> ImportDialog(info, { parts -> vm.importBackup(info, parts) }) { vm.pendingImport.value = null } }
    BackHandler(overlay != 0 || tab != TAB_HOME) { if (overlay != 0) overlay = 0 else tab = TAB_HOME }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { TabBar(tabs, if (overlay == 0) tab else null) { tab = it; overlay = 0 } },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (overlay == 1) {
                SettingsScreen(vm) { overlay = 0 }
            } else if (overlay == 2) {
                Column {
                    BackButton("Home") { overlay = 0 }
                    ScheduledScreen(vm)
                }
            } else when (tab) {
                TAB_HOME -> HomeScreen(vm, go = { tab = it }, openSettings = { overlay = 1 }, openScheduled = { overlay = 2 })
                TAB_HISTORY -> TransactionsScreen(vm)
                TAB_BUDGETS -> BudgetsScreen(vm)
                TAB_REPORTS -> ReportsScreen(vm)
                else -> AccountsScreen(vm)
            }
        }
    }
}
