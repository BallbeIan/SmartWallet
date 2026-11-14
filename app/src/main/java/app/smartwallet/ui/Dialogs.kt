package app.smartwallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.RectangleShape
import app.smartwallet.data.*
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/** Every free-text field starts with a capital letter. Numeric and PIN fields keep their own keyboards. */
internal val TextKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

// Kept as names for the call sites; they are now simply the system red and green.
val ExpenseRed: Color get() = Expense
val IncomeGreen: Color get() = Green

/** Page colours inside a sheet: iOS shows white (or dark grey) cards on a light grey (or dark) sheet. */
val SheetBg: Color get() = if (AppTheme.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
val SheetCard: Color get() = if (AppTheme.isDark) Color(0xFF2C2C2E) else Color.White

/**
 * The one form shell, shaped like an iOS sheet: a grabber, a bar with Cancel on the left, the title in the middle and
 * the confirm action on the right, then the form. [leading] (usually Delete) sits at the bottom as its own row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "Save",
    confirmEnabled: Boolean = true,
    confirmColor: Color = Green,
    dismissText: String = "Cancel",
    onConfirm: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 2.dp).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))) },
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss, Modifier.widthIn(min = 80.dp)) { Text(dismissText, color = Green, style = MaterialTheme.typography.bodyLarge) }
                Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (onConfirm != null) TextButton(onConfirm, Modifier.widthIn(min = 80.dp), enabled = confirmEnabled) {
                    Text(confirmText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                        color = if (confirmEnabled) confirmColor else MaterialTheme.colorScheme.outline)
                } else Spacer(Modifier.width(80.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            if (leading != null) Surface(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).fillMaxWidth(), shape = MaterialTheme.shapes.small, color = SheetCard) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { leading() }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) =
    Text(text.uppercase(), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Filled, borderless text fields on the sheet's white cards. */
@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SheetCard, unfocusedContainerColor = SheetCard, errorContainerColor = SheetCard,
    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, errorBorderColor = Expense,
    focusedLabelColor = Green, cursorColor = Green,
)

/** A capsule for picking accounts, categories and options: filled when chosen, soft grey otherwise. */
@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit, color: Color = Green, dot: Color? = null) {
    Surface(onClick, shape = CircleShape, color = if (selected) color else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(dot))
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceGroup(items: List<T>, isSelected: (T) -> Boolean, label: (T) -> String, color: Color = Green,
                            dot: (T) -> Color? = { null }, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Choice(label(it), isSelected(it), { onSelect(it) }, color, dot(it)) }
    }
}

/** A tappable white row inside a sheet: a value on the left, an optional icon on the right. */
@Composable
internal fun FormRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) =
    Surface(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = SheetCard) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }

/** Categories collapse to one row showing the current pick; tapping opens the full list and picking one closes it again. */
@Composable
internal fun CategoryPicker(categories: List<Category>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Category")
        FormRow({ open = !open }) {
            if (selected != null) Box(Modifier.padding(end = 10.dp).size(10.dp).clip(CircleShape).background(Color(selected.color)))
            Text(selected?.name ?: "No category", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            ChoiceGroup(categories, { it.id == selectedId }, { it.name }, Green, dot = { Color(it.color) }) {
                onSelect(if (selectedId == it.id) null else it.id); open = false
            }
        }
    }
}

/** Big centered number entry, tinted by what is being entered. */
@Composable
internal fun AmountField(value: String, onChange: (String) -> Unit, color: Color, error: Boolean) {
    TextField(
        value, onChange, Modifier.fillMaxWidth(), singleLine = true, isError = error,
        textStyle = MaterialTheme.typography.titleLarge.merge(Num).copy(textAlign = TextAlign.Center, color = color),
        placeholder = { Text("0.00", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge.merge(Num), color = MaterialTheme.colorScheme.outline) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SheetCard, unfocusedContainerColor = SheetCard, errorContainerColor = SheetCard,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
            cursorColor = color, errorCursorColor = Expense,
        ),
    )
}

internal fun currencySymbol(): String = Money.symbol()

/** An iOS-style alert: centred title and message, two buttons split by hairlines. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String = "Delete", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.width(284.dp), shape = RoundedCornerShape(18.dp), color = if (AppTheme.isDark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9)) {
            Column {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(body, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.height(IntrinsicSize.Min)) {
                    TextButton(onDismiss, Modifier.weight(1f).height(48.dp), shape = RectangleShape) { Text("Cancel", color = Green, style = MaterialTheme.typography.bodyLarge) }
                    VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton({ onConfirm(); onDismiss() }, Modifier.weight(1f).height(48.dp), shape = RectangleShape) {
                        Text(confirm, color = Expense, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun AccountDialog(initial: Account?, onSave: (Account) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var balance by remember { mutableStateOf(initial?.let { Money.plain(it.initialCents) } ?: "0.00") }
    val cents = Money.parse(balance)
    var confirmDelete by remember { mutableStateOf(false) }
    WalletDialog(
        if (initial == null) "New account" else "Edit account", onDismiss,
        confirmEnabled = name.isNotBlank() && cents != null,
        onConfirm = { onSave((initial ?: Account(name = "")).copy(name = name.trim(), initialCents = cents!!)); onDismiss() },
        leading = onDelete?.let { { TextButton({ confirmDelete = true }) { Text("Delete", color = Expense) } } },
    ) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, keyboardOptions = TextKeyboard,
            shape = MaterialTheme.shapes.small, colors = fieldColors())
        OutlinedTextField(balance, { balance = it }, Modifier.fillMaxWidth(), label = { Text("Starting balance (${currencySymbol()})") }, singleLine = true,
            isError = cents == null, shape = MaterialTheme.shapes.small, colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
    if (confirmDelete) ConfirmDialog("Delete account?", "All its transactions are deleted too.", onConfirm = { onDelete?.invoke(); onDismiss() }, onDismiss = { confirmDelete = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDialog(
    initial: Txn?,
    startKind: Int,
    accounts: List<Account>,
    categories: List<Category>,
    onSave: (Txn) -> Unit,
    onTransfer: (from: Long, to: Long, cents: Long, date: Long, note: String) -> Unit,
    onDelete: (() -> Unit)?,
    defaultAccountId: Long? = null,
    onDismiss: () -> Unit,
) {
    // kind: 0 expense, 1 income, 2 transfer
    var kind by remember { mutableIntStateOf(if (initial == null) startKind else if (initial.amountCents < 0) 0 else 1) }
    var amount by remember { mutableStateOf(initial?.let { Money.plain(abs(it.amountCents)) } ?: "") }
    var accountId by remember { mutableStateOf(initial?.accountId ?: accounts.firstOrNull { it.id == defaultAccountId }?.id ?: accounts.firstOrNull()?.id) }
    var toId by remember { mutableStateOf(accounts.firstOrNull { it.id != accountId }?.id) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var date by remember { mutableStateOf(initial?.localDate() ?: LocalDate.now()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }

    val cents = Money.parse(amount)
    val valid = cents != null && cents > 0 && accountId != null && (kind != 2 || (toId != null && toId != accountId))
    val shown = categories.filter { it.isIncome == (kind == 1) }
    val kindColor = listOf(ExpenseRed, IncomeGreen, Blue)[kind]
    val kinds = listOf("Expense", "Income", "Transfer")
    // The in-flow colour used for text on dark; the saturated one is only for filled buttons.
    val textColor = listOf(Expense, Green, Blue)[kind]

    WalletDialog(
        if (initial == null) "New transaction" else "Edit transaction", onDismiss,
        confirmEnabled = valid, confirmColor = kindColor,
        onConfirm = {
            if (kind == 2) onTransfer(accountId!!, toId!!, cents!!, date.toEpochDay(), note.trim())
            else onSave(Txn(id = initial?.id ?: 0, accountId = accountId!!, categoryId = categoryId,
                amountCents = if (kind == 0) -cents!! else cents!!, note = note.trim(), date = date.toEpochDay()))
            onDismiss()
        },
        leading = onDelete?.let { { TextButton({ onDelete(); onDismiss() }) { Text("Delete", color = Expense) } } },
    ) {
        if (initial == null) {
            // iOS segmented control: a grey track with a white thumb under the chosen option.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
                kinds.forEachIndexed { i, l ->
                    val on = kind == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) (if (AppTheme.isDark) Color(0xFF636366) else Color.White) else Color.Transparent)
                            .clickable { kind = i; categoryId = null }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(l, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Amount · ${currencySymbol()}")
            AmountField(amount, { amount = it }, textColor, amount.isNotEmpty() && cents == null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(if (kind == 2) "From" else "Account")
            ChoiceGroup(accounts, { it.id == accountId }, { it.name }, kindColor.takeIf { kind == 2 } ?: Green) { accountId = it.id }
        }
        if (kind == 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("To")
                ChoiceGroup(accounts.filter { it.id != accountId }, { it.id == toId }, { it.name }, Blue) { toId = it.id }
            }
        } else {
            CategoryPicker(shown, categoryId) { categoryId = it }
        }
        FormRow({ pickDate = true }) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = Green)
            Text(date.label(), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, keyboardOptions = TextKeyboard,
            shape = MaterialTheme.shapes.small, colors = fieldColors())
    }

    if (pickDate) WalletDatePicker(date, { date = it }) { pickDate = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDatePicker(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC midnight millis, so convert through UTC to avoid off-by-one days.
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
    val colors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedDayContainerColor = Green, selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
        todayContentColor = Green, todayDateBorderColor = Green,
        selectedYearContainerColor = Green, selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
        currentYearContentColor = Green,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss, colors = colors, shape = MaterialTheme.shapes.large,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK", color = Green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    ) { DatePicker(state, colors = colors) }
}

@Composable
fun AmountDialog(title: String, initialCents: Long?, confirm: String = "Save", label: String = "Monthly limit", hint: String = "Enter 0 to remove the limit.", onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialCents?.let { Money.plain(it) } ?: "") }
    val cents = Money.parse(text)
    WalletDialog(title, onDismiss, confirm, confirmEnabled = cents != null, onConfirm = { onSave(cents!!); onDismiss() }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("$label · ${currencySymbol()}")
            AmountField(text, { text = it }, Green, text.isNotEmpty() && cents == null)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RecurringDialog(
    initial: Recurring?, accounts: List<Account>, categories: List<Category>,
    onSave: (Recurring) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var income by remember { mutableStateOf((initial?.amountCents ?: -1L) > 0) }
    var amount by remember { mutableStateOf(initial?.let { Money.plain(abs(it.amountCents)) } ?: "") }
    var accountId by remember { mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var freq by remember { mutableStateOf(initial?.freq() ?: Freq.MONTHLY) }
    var date by remember { mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.nextDate) } ?: LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val cents = Money.parse(amount)
    val valid = name.isNotBlank() && cents != null && cents > 0 && accountId != null
    val shown = categories.filter { it.isIncome == income }
    val tint = if (income) Green else Expense

    WalletDialog(
        if (initial == null) "New scheduled payment" else "Edit scheduled payment", onDismiss,
        confirmEnabled = valid, confirmColor = if (income) IncomeGreen else ExpenseRed,
        onConfirm = {
            onSave(Recurring(
                id = initial?.id ?: 0, accountId = accountId!!, categoryId = categoryId,
                amountCents = if (income) cents!! else -cents!!, name = name.trim(), frequency = freq.name,
                nextDate = date.toEpochDay(),
                // Keep the original billing day unless the user picked a new date.
                anchorDay = if (initial != null && date.toEpochDay() == initial.nextDate) initial.anchorDay else date.dayOfMonth,
                active = initial?.active ?: true,
            ))
            onDismiss()
        },
        leading = onDelete?.let { { TextButton({ confirmDelete = true }) { Text("Delete", color = Expense) } } },
    ) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name (e.g. Netflix)") }, singleLine = true, keyboardOptions = TextKeyboard,
            shape = MaterialTheme.shapes.small, colors = fieldColors())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Expense", !income, { income = false; categoryId = null }, Expense)
            Choice("Income", income, { income = true; categoryId = null }, Green)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Amount · ${currencySymbol()}")
            AmountField(amount, { amount = it }, tint, amount.isNotEmpty() && cents == null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Repeats")
            ChoiceGroup(Freq.entries, { it == freq }, { it.label }) { freq = it }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Account")
            ChoiceGroup(accounts, { it.id == accountId }, { it.name }) { accountId = it.id }
        }
        CategoryPicker(shown, categoryId) { categoryId = it }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Next payment")
            FormRow({ pickDate = true }) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = Green)
                Text(date.label(), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
            if (date.isBefore(LocalDate.now())) Text("A payment dated in the past is added to History right away.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (pickDate) WalletDatePicker(date, { date = it }) { pickDate = false }
    if (confirmDelete) ConfirmDialog("Delete scheduled payment?", "Payments already added to History stay.",
        onConfirm = { onDelete?.invoke(); onDismiss() }, onDismiss = { confirmDelete = false })
}

@Composable
fun CurrencyDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember { java.util.Currency.getAvailableCurrencies().sortedBy { it.currencyCode } }
    val common = remember {
        listOf("EUR", "USD", "GBP", "CHF", "JPY", "CAD", "AUD", "CNY", "INR", "SEK", "NOK", "DKK", "PLN", "MXN", "BRL")
            .mapNotNull { runCatching { java.util.Currency.getInstance(it) }.getOrNull() }
    }
    val shown = if (query.isBlank()) common
        else all.filter { it.currencyCode.contains(query, true) || it.displayName.contains(query, true) }.take(30)

    @Composable
    fun Row(code: String?, title: String, subtitle: String, symbol: String) {
        Row(Modifier.fillMaxWidth().clickable { onPick(code); onDismiss() }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(symbol, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (code == current) Icon(Icons.Rounded.Check, "Selected", tint = Green, modifier = Modifier.padding(start = 10.dp))
        }
    }

    WalletDialog("Currency", onDismiss) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search currencies") }, singleLine = true, keyboardOptions = TextKeyboard,
            shape = MaterialTheme.shapes.small, colors = fieldColors())
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = SheetCard) {
            Column {
                if (query.isBlank()) {
                    val device = java.text.NumberFormat.getCurrencyInstance().currency
                    Row(null, "Device default", device?.let { "${it.currencyCode} · ${it.displayName}" } ?: "", device?.symbol ?: "")
                    Hairline(14.dp)
                }
                shown.forEachIndexed { i, c -> if (i > 0) Hairline(14.dp); Row(c.currencyCode, c.currencyCode, c.displayName, c.symbol) }
            }
        }
        if (shown.isEmpty()) Text("No match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (query.isBlank()) Text("Search to find any other currency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
