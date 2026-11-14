package app.smartwallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.*
import java.time.LocalDate

@Composable
fun ScheduledScreen(vm: WalletViewModel) {
    val items by vm.recurring.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var adding by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val catById = categories.associateBy { it.id }
    val accountNames = accounts.associate { it.id to it.name }
    val active = items.filter { it.active }
    val monthlyOut = -active.filter { !it.isTransfer() && it.amountCents < 0 }.sumOf { it.monthlyCents() }

    Column {
        LargeTitle("Scheduled") {
            IconButton({ if (accounts.isEmpty()) vm.message.value = "Add an account first" else adding = true }) { Icon(Icons.Rounded.Add, "Add scheduled payment", tint = Green, modifier = Modifier.size(28.dp)) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text("Estimated per month", style = MaterialTheme.typography.bodyMedium, color = muted)
                    Text(Money.format(monthlyOut), style = MaterialTheme.typography.displaySmall.merge(Num), maxLines = 1)
                    Text("${active.size} active · ${items.size - active.size} paused", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            item { SectionHeader("Payments") }
            if (items.isEmpty()) item {
                Text("Add subscriptions and other repeating payments. They are added to History automatically on their due date.",
                    Modifier.padding(horizontal = 20.dp), color = muted)
            } else item {
                Grouped {
                    items.forEachIndexed { i, r ->
                        if (i > 0) Hairline(64.dp)
                        val cat = catById[r.categoryId]
                        ListRow(
                            title = r.name, modifier = Modifier.alpha(if (r.active) 1f else 0.5f), onClick = { editing = r },
                            subtitle = listOf(r.freq().label, "next ${LocalDate.ofEpochDay(r.nextDate).shortLabel()}",
                                if (r.isTransfer()) "${accountNames[r.accountId].orEmpty()} → ${accountNames[r.toAccountId].orEmpty()}" else accountNames[r.accountId].orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                            leading = { if (r.isTransfer()) Squircle(Icons.Rounded.SwapHoriz, Blue, 36.dp) else LetterBadge(r.name, cat?.let { Color(it.color) } ?: Gray) },
                            trailing = {
                                Text(Money.format(r.amountCents), style = MaterialTheme.typography.bodyLarge.merge(Num),
                                    color = if (!r.isTransfer() && r.amountCents > 0) IncomeText else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(10.dp))
                                AppSwitch(r.active, { vm.setRecurringActive(r, it) })
                            },
                        )
                    }
                }
            }
        }
    }

    if (adding) RecurringDialog(null, accounts, categories, onSave = { vm.saveRecurring(it); vm.processDue() }, onDelete = null) { adding = false }
    editing?.let { r -> RecurringDialog(r, accounts, categories, onSave = { vm.saveRecurring(it); vm.processDue() }, onDelete = { vm.deleteRecurring(r) }) { editing = null } }
}
