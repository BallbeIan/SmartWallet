package app.smartwallet.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM accounts ORDER BY name") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts") suspend fun accountsOnce(): List<Account>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun accountOnce(id: Long): Account?
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE accountId = :id AND date <= :day") suspend fun sumUpTo(id: Long, day: Long): Long
    @Insert suspend fun insertAccount(a: Account): Long
    @Update suspend fun updateAccount(a: Account)
    @Delete suspend fun deleteAccount(a: Account)

    @Query("SELECT * FROM categories ORDER BY name") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM categories") suspend fun categoriesOnce(): List<Category>
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Insert suspend fun insertCategory(c: Category): Long
    @Insert suspend fun insertCategories(c: List<Category>)
    @Delete suspend fun deleteCategory(c: Category)

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC") fun transactions(): Flow<List<Txn>>
    @Query("SELECT * FROM transactions ORDER BY date, id") suspend fun transactionsOnce(): List<Txn>
    @Insert suspend fun insertTxn(t: Txn): Long
    @Insert suspend fun insertTxns(list: List<Txn>)
    @Query("DELETE FROM transactions WHERE transferId LIKE 'mend-%' OR transferId LIKE 'carry-%'") suspend fun deleteAutoTransactions()
    @Update suspend fun updateTxn(t: Txn)
    @Delete suspend fun deleteTxn(t: Txn)
    @Query("DELETE FROM transactions WHERE transferId = :id") suspend fun deleteTransfer(id: String)

    @Query("SELECT * FROM recurring ORDER BY active DESC, nextDate") fun recurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDate <= :today") suspend fun recurringDue(today: Long): List<Recurring>
    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDate BETWEEN :from AND :to") suspend fun recurringUpcoming(from: Long, to: Long): List<Recurring>
    @Query("DELETE FROM recurring WHERE toAccountId = :accountId") suspend fun deleteRecurringTransfersTo(accountId: Long)
    @Insert suspend fun insertRecurring(r: Recurring): Long
    @Update suspend fun updateRecurring(r: Recurring)
    @Delete suspend fun deleteRecurring(r: Recurring)

    @Query("SELECT * FROM budgets") fun budgets(): Flow<List<Budget>>
    // The unique index on categoryId makes REPLACE act as "set the limit for this category".
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(b: Budget)
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM budgets WHERE categoryId = :categoryId") suspend fun deleteBudgetFor(categoryId: Long)
}

@Database(
    entities = [Account::class, Category::class, Txn::class, Budget::class, Recurring::class],
    version = 5,
    exportSchema = false,
)
abstract class WalletDb : RoomDatabase() {
    abstract fun dao(): WalletDao

    companion object {
        // Adds scheduled payments without touching existing data.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER, `amountCents` INTEGER NOT NULL, `name` TEXT NOT NULL, `frequency` TEXT NOT NULL, `nextDate` INTEGER NOT NULL, `anchorDay` INTEGER NOT NULL, `active` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_accountId` ON `recurring` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)")
            }
        }

        // Scheduled transfers and conditions.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `toAccountId` INTEGER")
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `condition` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `conditionCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `amountMode` TEXT NOT NULL DEFAULT 'FIXED'")
            }
        }

        // Scheduled transfers and conditions were removed again: rebuild the table without those columns (SQLite on older
        // Android cannot drop columns). Scheduled transfers cannot be represented any more, so they are deleted.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM `recurring` WHERE `toAccountId` IS NOT NULL")
                db.execSQL("CREATE TABLE `recurring_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER, `amountCents` INTEGER NOT NULL, `name` TEXT NOT NULL, `frequency` TEXT NOT NULL, `nextDate` INTEGER NOT NULL, `anchorDay` INTEGER NOT NULL, `active` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `recurring_new` (`id`, `accountId`, `categoryId`, `amountCents`, `name`, `frequency`, `nextDate`, `anchorDay`, `active`) SELECT `id`, `accountId`, `categoryId`, `amountCents`, `name`, `frequency`, `nextDate`, `anchorDay`, `active` FROM `recurring`")
                db.execSQL("DROP TABLE `recurring`")
                db.execSQL("ALTER TABLE `recurring_new` RENAME TO `recurring`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_accountId` ON `recurring` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)")
            }
        }

        // Scheduled transfers between accounts.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `toAccountId` INTEGER")
            }
        }

        @Volatile private var instance: WalletDb? = null

        fun get(context: Context): WalletDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, WalletDb::class.java, "wallet.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
