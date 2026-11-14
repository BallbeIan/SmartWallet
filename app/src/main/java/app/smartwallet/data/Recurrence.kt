package app.smartwallet.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID

/**
 * Turns every due occurrence into a real transaction, dated when it was due, and moves nextDate forward.
 * Safe to call repeatedly and from several places (the app and the background worker). Returns how many were added.
 */
suspend fun processDueRecurring(db: WalletDb): Int {
    val dao = db.dao()
    val today = LocalDate.now()
    var added = 0
    db.withTransaction {
        for (r in dao.recurringDue(today.toEpochDay())) {
            var next = LocalDate.ofEpochDay(r.nextDate)
            var guard = 0 // a bad row must never loop forever
            while (!next.isAfter(today) && guard++ < 1000) {
                val date = next.toEpochDay()
                if (r.toAccountId == null) {
                    dao.insertTxn(Txn(accountId = r.accountId, categoryId = r.categoryId, amountCents = r.amountCents, note = r.name, date = date))
                    added++
                } else if (r.toAccountId != r.accountId && dao.accountOnce(r.accountId) != null && dao.accountOnce(r.toAccountId) != null) {
                    // A transfer is two linked rows, like one made by hand.
                    val id = UUID.randomUUID().toString()
                    dao.insertTxn(Txn(accountId = r.accountId, categoryId = null, amountCents = -r.amountCents, note = r.name, date = date, transferId = id))
                    dao.insertTxn(Txn(accountId = r.toAccountId, categoryId = null, amountCents = r.amountCents, note = r.name, date = date, transferId = id))
                    added++
                }
                next = r.advance(next)
            }
            dao.updateRecurring(r.copy(nextDate = next.toEpochDay()))
        }
    }
    return added
}
