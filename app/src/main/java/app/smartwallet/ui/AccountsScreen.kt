package app.smartwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.smartwallet.WalletViewModel
import app.smartwallet.data.Account
import app.smartwallet.data.Money

@Composable
fun AccountsScreen(vm: WalletViewModel) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val balances by vm.balances.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
            item {
                Column(Modifier.padding(20.dp, 28.dp, 20.dp, 20.dp)) {
                    Text("NET WORTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(balances.values.sum()), style = MaterialTheme.typography.displaySmall.merge(Num))
                    Text("${accounts.size} account${if (accounts.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Text("ACCOUNTS", Modifier.padding(20.dp, 8.dp, 20.dp, 8.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (accounts.isEmpty()) item {
                Text("Add your first account (cash, bank, card…) with the + button.", Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else item {
                Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        accounts.forEachIndexed { i, a ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            val b = balances[a.id] ?: 0L
                            Row(Modifier.fillMaxWidth().clickable { editing = a }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                    Text(a.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(a.name, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                                Text(Money.format(b), style = MaterialTheme.typography.bodyLarge.merge(Num),
                                    color = if (b < 0) Expense else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (accounts.size > 1) SmallFloatingActionButton(onClick = { transferring = true }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Filled.SwapHoriz, "Transfer")
            }
            FloatingActionButton(onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Filled.Add, "Add account")
            }
        }
    }

    if (adding) AccountDialog(null, vm::saveAccount, null) { adding = false }
    editing?.let { a -> AccountDialog(a, vm::saveAccount, { vm.deleteAccount(a) }) { editing = null } }
    if (transferring) TransactionDialog(null, 2, accounts, categories, vm::saveTxn, vm::transfer, null) { transferring = false }
}
