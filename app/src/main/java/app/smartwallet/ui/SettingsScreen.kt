package app.smartwallet.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
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
import app.smartwallet.data.MonthEnd
import app.smartwallet.data.Money
import java.time.LocalDate

private const val MAIN = 0
private const val MONTH_END = 1
private const val CATEGORIES = 2

@Composable
private fun GroupLabel(text: String) =
    Text(text.uppercase(), Modifier.padding(start = 8.dp, top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) = Surface(
    Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
) { Column(content = content) }

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** One line in a group: badge, title, optional value line, then a chevron or a custom control. */
@Composable
private fun Row(
    icon: ImageVector, tint: Color, title: String, subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    // Last on purpose: a trailing lambda at a call site is the tap action, not the trailing control.
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, 40)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) = Group {
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

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importCsv) }

    var remindersOn by remember { mutableStateOf(Reminders.enabled(ctx) && Reminders.hasPermission(ctx)) }
    var leadDays by remember { mutableIntStateOf(Reminders.leadDays(ctx)) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { Reminders.setEnabled(ctx, true); remindersOn = true } else vm.message.value = "Notifications are blocked for SmartWallet in system settings"
    }
    var rules by remember { mutableStateOf(MonthEnd.load(ctx)) }
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
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (page == MAIN) onClose() else page = MAIN }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(when (page) { MONTH_END -> "Month end"; CATEGORIES -> "Categories"; else -> "Settings" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (page == CATEGORIES) TextButton({ addingCategory = true }) { Text("Add", color = Green) }
        }

        when (page) {
            MAIN -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel("General")
                Group {
                    val c = Money.currency()
                    Row(Icons.Filled.Palette, Blue, "Theme", AppTheme.mode.label) { pickingTheme = true }
                    Divider()
                    Row(Icons.Filled.AttachMoney, Green, "Currency", "${c.currencyCode} · ${c.symbol}${if (Money.code == null) " (device)" else ""}") { pickingCurrency = true }
                    Divider()
                    Row(Icons.Filled.Notifications, Amber, "Payment reminders", if (remindersOn) leadLabel(leadDays) else "Off",
                        onClick = if (remindersOn) ({ pickingLead = true }) else null,
                        trailing = {
                            Switch(remindersOn, { on ->
                                if (!on) { Reminders.setEnabled(ctx, false); remindersOn = false }
                                else if (Reminders.hasPermission(ctx)) { Reminders.setEnabled(ctx, true); remindersOn = true }
                                else { AppLock.ignoreNextStop(); askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                            })
                        })
                    Divider()
                    val spending = accounts.firstOrNull { it.id == rules.spending }
                    val parts = listOfNotNull(
                        accounts.firstOrNull { it.id == rules.surplusTarget }?.takeIf { rules.moveSurplus }?.let { "leftover → ${it.name}" },
                        accounts.firstOrNull { it.id == rules.deficitSource }?.takeIf { rules.coverDeficit }?.let { "overspend ← ${it.name}" },
                    )
                    Row(Icons.Filled.CalendarMonth, Blue, "Month end",
                        if (!rules.enabled) "Off" else if (spending == null) "Choose a spending account" else "${spending.name}: ${parts.joinToString(", ").ifEmpty { "leave as is" }}",
                        trailing = { Switch(rules.enabled, { on -> update(rules.copy(enabled = on)); if (on && rules.spending == null) page = MONTH_END }) }) { page = MONTH_END }
                }

                GroupLabel("Security")
                Group {
                    Row(Icons.Filled.Lock, Green, "App lock", if (lockOn) "PIN${if (bioOn) " and biometrics" else ""}" else "Off",
                        trailing = { Switch(lockOn, { on -> if (on) settingPin = true else disablingLock = true }) })
                    if (lockOn) {
                        Divider()
                        Row(Icons.Filled.Pin, Blue, "Change PIN") { changingPin = true }
                        Divider()
                        val problem = biometricsProblem(activity)
                        Row(Icons.Filled.Fingerprint, Teal, "Unlock with biometrics", if (problem == null) "Fingerprint or face" else problem,
                            trailing = {
                                Switch(bioOn, { on ->
                                    if (!on) { AppLock.setBiometrics(ctx, false); bioOn = false }
                                    else showBiometricPrompt(activity, "Confirm it's you") { AppLock.setBiometrics(ctx, true); bioOn = true }
                                }, enabled = problem == null)
                            })
                        Divider()
                        Row(Icons.Filled.Timer, Amber, "Lock", timeoutLabel(timeoutMs)) { pickingTimeout = true }
                    }
                }

                GroupLabel("Data")
                Group {
                    Row(Icons.Filled.Category, Teal, "Categories", "${categories.size} categories") { page = CATEGORIES }
                    Divider()
                    Row(Icons.Filled.FileUpload, Blue, "Export CSV", "Save all transactions to a file") { run { AppLock.ignoreNextStop(); export.launch("smartwallet-${LocalDate.now()}.csv") } }
                    Divider()
                    Row(Icons.Filled.FileDownload, Blue, "Import CSV", "date, account, category, amount, note, transfer_id") { run { AppLock.ignoreNextStop(); import.launch(arrayOf("*/*")) } }
                }

                if (BuildConfig.DEBUG) {
                    GroupLabel("Developer")
                    Group {
                        Row(Icons.Filled.BugReport, Amber, "Load demo data", "Sample accounts, transactions and budgets", onClick = vm::loadDemoData)
                        Divider()
                        Row(Icons.Filled.DeleteForever, Expense, "Clear all data", "Removes accounts, transactions and budgets") { clearing = true }
                    }
                }
            }

            MONTH_END -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text("Choose the account you spend from. When a month ends, its balance on the last day is handled automatically.",
                    Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = muted)
                Group {
                    Row(Icons.Filled.CalendarMonth, Blue, "Apply at month end", if (rules.enabled) "On" else "Off",
                        trailing = { Switch(rules.enabled, { update(rules.copy(enabled = it)) }) })
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
                            Surface({ pickingKeep = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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

            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Expense" to false, "Income" to true).forEach { (label, income) ->
                    val list = categories.filter { it.isIncome == income }
                    if (list.isNotEmpty()) {
                        GroupLabel(label)
                        Group {
                            list.forEachIndexed { i, c ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                                    Text(c.name, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.titleMedium)
                                    IconButton({ deleting = c }) { Icon(Icons.Filled.Delete, "Delete ${c.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
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
