package app.smartwallet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val initialCents: Long = 0,
)

@Entity(tableName = "categories", indices = [Index("name", unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isIncome: Boolean,
    val color: Long,
)

/** Amounts are signed cents: negative is money out. A transfer is two rows sharing a transferId. */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(Account::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("date")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long?,
    val amountCents: Long,
    val note: String = "",
    val date: Long, // epoch day
    val transferId: String? = null,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId", unique = true)],
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val limitCents: Long,
)

enum class Freq(val label: String) { WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly") }

/** A subscription or repeating payment. Due occurrences become normal transactions (see WalletViewModel.processDue). */
@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(Account::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("categoryId")],
)
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long?,
    val amountCents: Long,
    val name: String,
    val frequency: String,
    val nextDate: Long, // epoch day of the next occurrence
    val anchorDay: Int, // day of month to return to after short months (31st -> Feb 28 -> Mar 31)
    val active: Boolean = true,
)

fun Recurring.freq(): Freq = Freq.entries.firstOrNull { it.name == frequency } ?: Freq.MONTHLY

fun Recurring.advance(from: java.time.LocalDate): java.time.LocalDate = when (freq()) {
    Freq.WEEKLY -> from.plusWeeks(1)
    Freq.MONTHLY -> from.plusMonths(1).let { it.withDayOfMonth(minOf(anchorDay, it.lengthOfMonth())) }
    Freq.YEARLY -> from.plusYears(1).let { it.withDayOfMonth(minOf(anchorDay, it.lengthOfMonth())) }
}

fun Recurring.monthlyCents(): Long = when (freq()) {
    Freq.WEEKLY -> amountCents * 52 / 12
    Freq.MONTHLY -> amountCents
    Freq.YEARLY -> amountCents / 12
}
