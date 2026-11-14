package app.smartwallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val badgeColors = listOf(Blue, Green, Amber, Indigo, Teal, Purple)

    Column {
        LargeTitle("Accounts") {
            if (accounts.size > 1) IconButton({ transferring = true }) { Icon(Icons.Rounded.SwapHoriz, "Transfer", tint = Green, modifier = Modifier.size(28.dp)) }
            IconButton({ adding = true }) { Icon(Icons.Rounded.Add, "Add account", tint = Green, modifier = Modifier.size(28.dp)) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text("Net worth", style = MaterialTheme.typography.bodyMedium, color = muted)
                    Text(Money.format(balances.values.sum()), style = MaterialTheme.typography.displaySmall.merge(Num), maxLines = 1)
                }
            }
            item { SectionHeader("${accounts.size} account${if (accounts.size == 1) "" else "s"}") }
            if (accounts.isEmpty()) item {
                Text("Add your first account (cash, bank, card…) with the + button.", Modifier.padding(horizontal = 20.dp), color = muted)
            } else item {
                Grouped {
                    accounts.forEachIndexed { i, a ->
                        if (i > 0) Hairline(64.dp)
                        val b = balances[a.id] ?: 0L
                        ListRow(a.name, onClick = { editing = a }, leading = { LetterBadge(a.name, badgeColors[i % badgeColors.size]) },
                            trailing = {
                                Text(Money.format(b), style = MaterialTheme.typography.bodyLarge.merge(Num), color = if (b < 0) Expense else MaterialTheme.colorScheme.onSurface)
                                Chevron()
                            })
                    }
                }
            }
        }
    }

    if (adding) AccountDialog(null, vm::saveAccount, null) { adding = false }
    editing?.let { a -> AccountDialog(a, vm::saveAccount, { vm.deleteAccount(a) }) { editing = null } }
    if (transferring) TransactionDialog(null, 2, accounts, categories, vm::saveTxn, vm::transfer, null) { transferring = false }
}
