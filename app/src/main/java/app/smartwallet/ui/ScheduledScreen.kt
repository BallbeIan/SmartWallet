package app.smartwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
    val monthlyOut = -active.filter { it.amountCents < 0 }.sumOf { it.monthlyCents() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Column(Modifier.padding(20.dp, 8.dp, 20.dp, 20.dp)) {
                    Text("ESTIMATED PER MONTH", style = MaterialTheme.typography.labelSmall, color = muted)
                    Text(Money.format(monthlyOut), style = MaterialTheme.typography.displaySmall.merge(Num))
                    Text("${active.size} active · ${items.size - active.size} paused", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            if (items.isEmpty()) item {
                Text("Add subscriptions and other repeating payments. They are added to History automatically on their due date.",
                    Modifier.padding(horizontal = 20.dp), color = muted)
            } else item {
                Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        items.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            val cat = catById[r.categoryId]
                            val tint = cat?.let { Color(it.color) } ?: muted
                            Row(Modifier.fillMaxWidth().clickable { editing = r }.padding(horizontal = 16.dp, vertical = 12.dp).alpha(if (r.active) 1f else 0.5f),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                    Text(r.name.take(1).uppercase(), color = tint, style = MaterialTheme.typography.titleMedium)
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(listOf(r.freq().label, "next ${LocalDate.ofEpochDay(r.nextDate).label()}", accountNames[r.accountId].orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Money.format(r.amountCents), style = MaterialTheme.typography.titleMedium.merge(Num),
                                        color = if (r.amountCents > 0) Green else MaterialTheme.colorScheme.onSurface)
                                    Switch(r.active, { vm.setRecurringActive(r, it) }, Modifier.height(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (accounts.isEmpty()) vm.message.value = "Add an account first" else adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Icon(Icons.Filled.Add, "Add scheduled payment") }
    }

    if (adding) RecurringDialog(null, accounts, categories, onSave = { vm.saveRecurring(it); vm.processDue() }, onDelete = null) { adding = false }
    editing?.let { r -> RecurringDialog(r, accounts, categories, onSave = { vm.saveRecurring(it); vm.processDue() }, onDelete = { vm.deleteRecurring(r) }) { editing = null } }
}
