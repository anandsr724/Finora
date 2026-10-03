package com.example.expensetracker.data

import android.util.Log
import com.example.expensetracker.CurrencyManager
import com.example.expensetracker.PaymentTransaction
import org.json.JSONArray
import org.json.JSONObject

/** One category line of a split transaction — a category id plus the portion of the parent
 *  transaction's total amount allocated to it, in the parent's own currency (no per-line
 *  currency/date/id — this is a line item, not its own transaction). */
data class TransactionSplit(val category: String, val amount: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("category", category)
        put("amount", amount)
    }

    companion object {
        fun fromJson(json: JSONObject): TransactionSplit =
            TransactionSplit(json.getString("category"), json.getString("amount"))
    }
}

/**
 * Encodes/decodes [PaymentTransaction.splitBreakdown] — a JSON array of [TransactionSplit], or
 * an empty string when the transaction isn't split. Same `org.json` style as
 * CategoryManager's categories.json, no new dependency.
 */
object SplitBreakdownCodec {
    /** Fewer than 2 lines isn't a meaningful split — encodes to "" (not split) either way. */
    fun encode(splits: List<TransactionSplit>): String {
        if (splits.size < 2) return ""
        val array = JSONArray()
        splits.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    /** Malformed/blank input decodes to an empty list rather than throwing — a corrupted field
     *  degrades to "not split", matching CategoryManager's own defensive JSON-parsing style. */
    fun decode(raw: String): List<TransactionSplit> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { TransactionSplit.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e("TransactionSplit", "Error parsing splitBreakdown: $raw", e)
            emptyList()
        }
    }
}

/** True if this transaction has 2+ split category lines. */
fun PaymentTransaction.isSplit(): Boolean =
    SplitBreakdownCodec.decode(splitBreakdown).size >= 2

/**
 * (categoryId, amount-in-this-transaction's-currency) pairs for category-based reporting: the
 * decoded split lines if split, else a single pair from this transaction's own category/amount.
 * Callers apply CurrencyManager.convert(amount, this.currency, target) per pair exactly as they
 * already do for a plain transaction's amount today.
 */
fun PaymentTransaction.categoryAmountBreakdown(): List<Pair<String, Double>> {
    val splits = SplitBreakdownCodec.decode(splitBreakdown)
    return if (splits.size >= 2) {
        splits.map { it.category to CurrencyManager.parseAmount(it.amount) }
    } else {
        listOf(category to CurrencyManager.parseAmount(amount))
    }
}
