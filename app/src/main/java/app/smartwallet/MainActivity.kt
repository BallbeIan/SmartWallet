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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import app.smartwallet.ui.*

// FragmentActivity (a ComponentActivity subclass) is what the biometric prompt needs.
class MainActivity : FragmentActivity() {
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
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        // Debug only: `adb shell am start ... --ez demo true` loads sample data without touching the screen.
        app.smartwallet.data.Money.load(this)
        AppLock.onCreate(this)
        Reminders.schedule(this)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("remind", false)) Reminders.runNow(this, force = true)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) {
            androidx.lifecycle.ViewModelProvider(this)[WalletViewModel::class.java].loadDemoData()
        }
        // While locked, the app itself is not even composed, so nothing behind the lock screen can be read or tapped.
        setContent { WalletTheme { Surface { if (AppLock.locked) LockScreen(this@MainActivity) else WalletApp() } } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun WalletApp(vm: WalletViewModel = viewModel()) {
    val tabs = listOf(
        Tab("Home", Icons.Filled.Dashboard),
        Tab("History", Icons.AutoMirrored.Filled.ReceiptLong),
        Tab("Budgets", Icons.Filled.Savings),
        Tab("Reports", Icons.Filled.PieChart),
        Tab("Accounts", Icons.Filled.AccountBalanceWallet),
    )
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var overlay by rememberSaveable { mutableIntStateOf(0) } // 0 none, 1 settings, 2 scheduled payments
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    BackHandler(overlay != 0 || tab != TAB_HOME) { if (overlay != 0) overlay = 0 else tab = TAB_HOME }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        tab == i && overlay == 0, { tab = i; overlay = 0 }, { Icon(t.icon, t.label) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (overlay == 1) {
                SettingsScreen(vm) { overlay = 0 }
            } else if (overlay == 2) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ overlay = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Text("Scheduled payments", style = MaterialTheme.typography.titleLarge)
                    }
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
