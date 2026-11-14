package app.smartwallet.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import app.smartwallet.AppLock
import app.smartwallet.BuildConfig
import app.smartwallet.Reminders
import app.smartwallet.WalletViewModel
import app.smartwallet.data.Account
import app.smartwallet.data.Counts
import app.smartwallet.data.Parts
import app.smartwallet.data.isAuto
import app.smartwallet.data.MonthEnd
import app.smartwallet.data.Money
import java.time.LocalDate

private const val MAIN = 0
private const val MONTH_END = 1
private const val CATEGORIES = 2

/** One settings line: a coloured symbol, a title with an optional value, then a chevron or a control. */
@Composable
private fun Row(
    icon: ImageVector, tint: Color, title: String, subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    // Last on purpose: a trailing lambda at a call site is the tap action, not the trailing control.
    onClick: (() -> Unit)? = null,
) = ListRow(title, subtitle = subtitle, onClick = onClick, leading = { Squircle(icon, tint) }, trailing = trailing ?: if (onClick != null) ({ Chevron() }) else null)

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) = Grouped {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(title)
        content()
    }
}

@Composable
fun SettingsScreen(vm: WalletViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(MAIN) }
    BackHandler(page != MAIN) { page = MAIN }

    var exportParts by remember { mutableStateOf(Parts()) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.exportBackup(uri, exportParts) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openImport) }
    var exporting by remember { mutableStateOf(false) }
    val budgets by vm.budgets.collectAsState()
    val scheduled by vm.recurring.collectAsState()
    val allTransactions by vm.transactions.collectAsState()
    val settingsVersion by vm.settingsVersion.collectAsState()

    var remindersOn by remember(settingsVersion) { mutableStateOf(Reminders.enabled(ctx) && Reminders.hasPermission(ctx)) }
    var leadDays by remember(settingsVersion) { mutableIntStateOf(Reminders.leadDays(ctx)) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { Reminders.setEnabled(ctx, true); remindersOn = true } else vm.message.value = "Notifications are blocked for SmartWallet in system settings"
    }
    var rules by remember(settingsVersion) { mutableStateOf(MonthEnd.load(ctx)) }
    fun update(r: MonthEnd.Rules) { rules = r; MonthEnd.save(ctx, r); vm.monthEndChanged() }

    val activity = ctx as FragmentActivity
    var lockOn by remember { mutableStateOf(AppLock.enabled(ctx)) }
    var bioOn by remember { mutableStateOf(AppLock.biometrics(ctx)) }
    var timeoutMs by remember { mutableLongStateOf(AppLock.timeoutMs(ctx)) }
    var settingPin by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    var disablingLock by remember { mutableStateOf(false) }
    var pickingTimeout by remember { mutableStateOf(false) }
    val timeoutLabel = { ms: Long -> when (ms) { 0L -> "Immediately"; 60_000L -> "After 1 minute"; else -> "After ${ms / 60_000} minutes" } }
    var pickingTheme by remember { mutableStateOf(false) }
    var pickingCurrency by remember { mutableStateOf(false) }
    var pickingLead by remember { mutableStateOf(false) }
    var pickingKeep by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<app.smartwallet.data.Category?>(null) }

    val leadLabel = { d: Int -> when (d) { 0 -> "Same day"; 1 -> "1 day before"; else -> "$d days before" } }

    Column {
        BackButton(if (page == MAIN) "Home" else "Settings") { if (page == MAIN) onClose() else page = MAIN }
        LargeTitle(when (page) { MONTH_END -> "Month end"; CATEGORIES -> "Categories"; else -> "Settings" }) {
            if (page == CATEGORIES) TextButton({ addingCategory = true }) { Text("Add", color = Green, style = MaterialTheme.typography.bodyLarge) }
        }

        when (page) {
            MAIN -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                SectionHeader("General")
                Grouped {
                    val c = Money.currency()
                    Row(Icons.Rounded.Palette, Blue, "Theme", AppTheme.mode.label) { pickingTheme = true }
                    Hairline(58.dp)
                    Row(Icons.Rounded.AttachMoney, Green, "Currency", "${c.currencyCode} · ${c.symbol}${if (Money.code == null) " (device)" else ""}") { pickingCurrency = true }
                    Hairline(58.dp)
                    Row(Icons.Rounded.Notifications, Amber, "Payment reminders", if (remindersOn) leadLabel(leadDays) else "Off",
                        onClick = if (remindersOn) ({ pickingLead = true }) else null,
                        trailing = {
                            AppSwitch(remindersOn, { on ->
                                if (!on) { Reminders.setEnabled(ctx, false); remindersOn = false }
                                else if (Reminders.hasPermission(ctx)) { Reminders.setEnabled(ctx, true); remindersOn = true }
                                else { AppLock.ignoreNextStop(); askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                            })
                        })
                    Hairline(58.dp)
                    val spending = accounts.firstOrNull { it.id == rules.spending }
                    val parts = listOfNotNull(
                        accounts.firstOrNull { it.id == rules.surplusTarget }?.takeIf { rules.moveSurplus }?.let { "leftover → ${it.name}" },
                        accounts.firstOrNull { it.id == rules.deficitSource }?.takeIf { rules.coverDeficit }?.let { "overspend ← ${it.name}" },
                    )
                    Row(Icons.Rounded.CalendarMonth, Blue, "Month end",
                        if (!rules.enabled) "Off" else if (spending == null) "Choose a spending account" else "${spending.name}: ${parts.joinToString(", ").ifEmpty { "leave as is" }}",
                        trailing = { AppSwitch(rules.enabled, { on -> update(rules.copy(enabled = on)); if (on && rules.spending == null) page = MONTH_END }) }) { page = MONTH_END }
                }

                SectionHeader("Security")
                Grouped {
                    Row(Icons.Rounded.Lock, Green, "App lock", if (lockOn) "PIN${if (bioOn) " and biometrics" else ""}" else "Off",
                        trailing = { AppSwitch(lockOn, { on -> if (on) settingPin = true else disablingLock = true }) })
                    if (lockOn) {
                        Hairline(58.dp)
                        Row(Icons.Rounded.Pin, Blue, "Change PIN") { changingPin = true }
                        Hairline(58.dp)
                        val problem = biometricsProblem(activity)
                        Row(Icons.Rounded.Fingerprint, Teal, "Unlock with biometrics", if (problem == null) "Fingerprint or face" else problem,
                            trailing = {
                                AppSwitch(bioOn, { on ->
                                    if (!on) { AppLock.setBiometrics(ctx, false); bioOn = false }
                                    else showBiometricPrompt(activity, "Confirm it's you") { AppLock.setBiometrics(ctx, true); bioOn = true }
                                }, enabled = problem == null)
                            })
                        Hairline(58.dp)
                        Row(Icons.Rounded.Timer, Amber, "Lock", timeoutLabel(timeoutMs)) { pickingTimeout = true }
                    }
                }

                SectionHeader("Data")
                Grouped {
                    Row(Icons.Rounded.Category, Teal, "Categories", "${categories.size} categories") { page = CATEGORIES }
                    Hairline(58.dp)
                    Row(Icons.Rounded.FileUpload, Blue, "Export data", "Choose what to save to a file") { exporting = true }
                    Hairline(58.dp)
                    Row(Icons.Rounded.FileDownload, Blue, "Import data", "From a backup or a CSV file") { run { AppLock.ignoreNextStop(); import.launch(arrayOf("*/*")) } }
                }

                if (BuildConfig.DEBUG) {
                    SectionHeader("Developer")
                    Grouped {
                        Row(Icons.Rounded.BugReport, Amber, "Load demo data", "Sample accounts, transactions and budgets", onClick = vm::loadDemoData)
                        Hairline(58.dp)
                        Row(Icons.Rounded.DeleteForever, Expense, "Clear all data", "Removes accounts, transactions and budgets") { clearing = true }
                    }
                }
            }

            MONTH_END -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text("Choose the account you spend from. When a month ends, its balance on the last day is handled automatically.",
                    Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = muted)
                Grouped {
                    Row(Icons.Rounded.CalendarMonth, Blue, "Apply at month end", if (rules.enabled) "On" else "Off",
                        trailing = { AppSwitch(rules.enabled, { update(rules.copy(enabled = it)) }) })
                }
                if (rules.enabled) Card("Spending account") {
                    ChoiceGroup(listOf<Account?>(null) + accounts, { it?.id == rules.spending }, { it?.name ?: "None" }) { update(rules.copy(spending = it?.id)) }
                }
                if (rules.enabled && rules.spending != null) {
                    val others = accounts.filter { it.id != rules.spending }
                    Card("Money left over") {
                        ChoiceGroup(listOf(false, true), { it == rules.moveSurplus }, { if (it) "Move to another account" else "Leave it for next month" }) { update(rules.copy(moveSurplus = it)) }
                        if (rules.moveSurplus) {
                            SectionLabel("Move it to")
                            ChoiceGroup(others, { it.id == rules.surplusTarget }, { it.name }) { update(rules.copy(surplusTarget = it.id)) }
                            SectionLabel("Keep in the account")
                            Surface({ pickingKeep = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(if (rules.keepCents > 0) Money.format(rules.keepCents) else "Nothing (move everything)", Modifier.padding(14.dp))
                            }
                        }
                    }
                    Card("If I overspend") {
                        ChoiceGroup(listOf(false, true), { it == rules.coverDeficit }, { if (it) "Cover it from another account" else "Carry the negative balance over" }) { update(rules.copy(coverDeficit = it)) }
                        if (rules.coverDeficit) {
                            SectionLabel("Cover it from")
                            ChoiceGroup(others, { it.id == rules.deficitSource }, { it.name }) { update(rules.copy(deficitSource = it.id)) }
                        }
                    }
                    Text("Done as a transfer in History dated the last day of the month. Only the money above the amount you keep is moved; an overspent balance is covered back up to zero. Money that stays in the account is shown as a \"Carried over\" transfer into the new month. It applies to every month, past ones included, and is recalculated whenever you change an earlier month.",
                        Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }

            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Expense" to false, "Income" to true).forEach { (label, income) ->
                    val list = categories.filter { it.isIncome == income }
                    if (list.isNotEmpty()) {
                        SectionHeader(label)
                        Grouped {
                            list.forEachIndexed { i, c ->
                                if (i > 0) Hairline(16.dp)
                                ListRow(c.name, leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color))) },
                                    trailing = { IconButton({ deleting = c }) { Icon(Icons.Rounded.Delete, "Delete ${c.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) } })
                            }
                        }
                    }
                }
            }
        }
    }

    if (addingCategory) {
        var name by remember { mutableStateOf("") }
        var income by remember { mutableStateOf(false) }
        WalletDialog("New category", { addingCategory = false }, "Add", confirmEnabled = name.isNotBlank(),
            onConfirm = { vm.addCategory(name, income); addingCategory = false }) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, keyboardOptions = TextKeyboard,
                shape = MaterialTheme.shapes.small, colors = fieldColors())
            SectionLabel("Type")
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Expense", !income, { income = false }, Expense)
                Choice("Income", income, { income = true }, Green)
            }
        }
    }
    if (settingPin) PinDialog("Set a PIN", requireCurrent = false, onDismiss = { settingPin = false }) { _, new ->
        AppLock.setPin(ctx, new); lockOn = true; settingPin = false
    }
    if (changingPin) PinDialog("Change PIN", requireCurrent = !AppLock.recovered, onDismiss = { changingPin = false }) { _, new ->
        AppLock.setPin(ctx, new); changingPin = false
    }
    if (disablingLock) {
        if (AppLock.recovered) ConfirmDialog("Turn off app lock?", "Anyone who opens SmartWallet will see your data.", "Turn off",
            onConfirm = { AppLock.clear(ctx); lockOn = false; bioOn = false }, onDismiss = { disablingLock = false })
        else PinDialog("Turn off app lock", requireCurrent = true, askNew = false, confirmText = "Turn off", onDismiss = { disablingLock = false }) { _, _ ->
            AppLock.clear(ctx); lockOn = false; bioOn = false; disablingLock = false
        }
    }
    if (exporting) ExportDialog(
        Counts(accounts.size, categories.size, allTransactions.count { !it.isAuto() }, budgets.size, scheduled.size),
        onExport = { csv, parts ->
            AppLock.ignoreNextStop()
            if (csv) exportCsv.launch("smartwallet-transactions-${LocalDate.now()}.csv")
            else { exportParts = parts; exportJson.launch("smartwallet-backup-${LocalDate.now()}.json") }
        },
        onDismiss = { exporting = false },
    )
    if (pickingTheme) WalletDialog("Theme", { pickingTheme = false }) {
        ChoiceGroup(AppTheme.Mode.entries, { it == AppTheme.mode }, { it.label }) { AppTheme.set(ctx, it); pickingTheme = false }
    }
    if (pickingTimeout) WalletDialog("Lock", { pickingTimeout = false }) {
        ChoiceGroup(listOf(0L, 60_000L, 300_000L, 900_000L), { it == timeoutMs }, timeoutLabel) { timeoutMs = it; AppLock.setTimeoutMs(ctx, it); pickingTimeout = false }
        Text("How long SmartWallet can be in the background before it asks for your PIN again.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (pickingLead) WalletDialog("Remind me", { pickingLead = false }) {
        ChoiceGroup(listOf(0, 1, 2, 3), { it == leadDays }, leadLabel) {
            leadDays = it; Reminders.setLeadDays(ctx, it); Reminders.runNow(ctx); pickingLead = false
        }
        Text("Checked daily around 09:00.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (pickingKeep) AmountDialog("Keep in the account", rules.keepCents.takeIf { it > 0 }, label = "Amount to keep",
        hint = "This stays in the spending account at month end. Enter 0 to move everything.",
        onSave = { update(rules.copy(keepCents = it.coerceAtLeast(0))) }, onDismiss = { pickingKeep = false })
    if (pickingCurrency) CurrencyDialog(Money.code, { Money.setCurrency(ctx, it) }) { pickingCurrency = false }
    if (clearing) ConfirmDialog("Clear all data?", "Deletes every account, transaction and budget.", "Clear",
        onConfirm = vm::clearAllData, onDismiss = { clearing = false })
    deleting?.let { c ->
        ConfirmDialog("Delete ${c.name}?", "Its transactions stay but become uncategorized; its budget is removed.",
            onConfirm = { vm.deleteCategory(c) }, onDismiss = { deleting = null })
    }
}

/** PIN entry for setting, changing or confirming. Digits only, 4 to 6 long. */
@Composable
private fun PinDialog(
    title: String, requireCurrent: Boolean, askNew: Boolean = true, confirmText: String = "Save",
    onDismiss: () -> Unit, onDone: (current: String, new: String) -> Unit,
) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun digits(s: String) = s.filter { it.isDigit() }.take(6)

    @Composable
    fun Field(value: String, label: String, onChange: (String) -> Unit) = OutlinedTextField(
        value, { onChange(digits(it)); error = null }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.small, colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )

    val ready = (!requireCurrent || current.length >= 4) && (!askNew || (new.length >= 4 && again.length >= 4))
    WalletDialog(title, onDismiss, confirmText, confirmEnabled = ready, onConfirm = {
        when {
            requireCurrent && AppLock.verify(ctx, current) != AppLock.Result.OK ->
                error = if (AppLock.waitMs(ctx) > 0) "Too many tries. Wait ${(AppLock.waitMs(ctx) + 999) / 1000}s" else "Current PIN is wrong"
            askNew && new != again -> error = "The PINs don't match"
            else -> onDone(current, new)
        }
    }) {
        if (requireCurrent) Field(current, "Current PIN") { current = it }
        if (askNew) {
            Field(new, "New PIN (4 to 6 digits)") { new = it }
            Field(again, "Repeat PIN") { again = it }
            Text("There is no way to recover a forgotten PIN except your phone's screen lock, where the phone offers it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = Expense, style = MaterialTheme.typography.bodyMedium) }
    }
}
