package com.example.expensetracker.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import com.example.expensetracker.CurrencyManager
import com.example.expensetracker.PaymentTransaction
import com.example.expensetracker.R
import com.example.expensetracker.data.DuplicateDetector
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.Locale

/** What the user decided to do about a transaction that looks like a duplicate. */
enum class DuplicateResolution { KEEP_EXISTING, REPLACE_EXISTING, KEEP_BOTH }

/**
 * Shows a bottom sheet comparing a new transaction against the existing one it looks like a
 * duplicate of, and lets the user decide whether to discard the new one, replace the existing
 * one with it, or keep both. Used by every entry point (manual entry, OCR, statement import)
 * so duplicate handling behaves identically everywhere.
 *
 * Dismissing the sheet without picking an action (back press, tapping outside, the close
 * button) resolves to KEEP_EXISTING — the safe default that never creates a duplicate.
 */
fun showDuplicateResolutionSheet(
    context: Context,
    inflater: LayoutInflater,
    newTransaction: PaymentTransaction,
    match: DuplicateDetector.Match,
    onResolution: (DuplicateResolution) -> Unit
) {
    val sheet = BottomSheetDialog(context)
    val sheetView = inflater.inflate(R.layout.layout_duplicate_transaction_sheet, null)
    sheet.setContentView(sheetView)

    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))

    fun bind(amountView: TextView, recipientView: TextView, metaView: TextView, tx: PaymentTransaction) {
        val sym = CurrencyManager.getSymbol(tx.currency)
        val sign = if (tx.type == "income") "+" else "-"
        amountView.text = "$sign$sym${fmt.format(CurrencyManager.parseAmount(tx.amount))}"
        recipientView.text = tx.recipient.ifBlank { "(unknown)" }
        metaView.text = listOf(tx.dateTime, tx.bankInfo).filter { it.isNotBlank() }.joinToString(" • ")
    }

    bind(
        sheetView.findViewById(R.id.existingAmount),
        sheetView.findViewById(R.id.existingRecipient),
        sheetView.findViewById(R.id.existingMeta),
        match.existing
    )
    bind(
        sheetView.findViewById(R.id.newAmount),
        sheetView.findViewById(R.id.newRecipient),
        sheetView.findViewById(R.id.newMeta),
        newTransaction
    )

    sheetView.findViewById<TextView>(R.id.duplicateReasonText).text = when (match.reason) {
        DuplicateDetector.MatchReason.TRANSACTION_ID ->
            "A saved transaction already has the same transaction ID."
        DuplicateDetector.MatchReason.AMOUNT_DATE ->
            "A saved transaction already has the same amount, type and date."
    }

    var resolved = false
    fun resolve(resolution: DuplicateResolution) {
        if (resolved) return
        resolved = true
        sheet.dismiss()
        onResolution(resolution)
    }

    sheet.setOnDismissListener { resolve(DuplicateResolution.KEEP_EXISTING) }
    sheetView.findViewById<ImageButton>(R.id.closeDuplicateSheetButton).setOnClickListener {
        resolve(DuplicateResolution.KEEP_EXISTING)
    }
    sheetView.findViewById<MaterialButton>(R.id.keepBothButton).setOnClickListener {
        resolve(DuplicateResolution.KEEP_BOTH)
    }
    sheetView.findViewById<MaterialButton>(R.id.replaceExistingButton).setOnClickListener {
        resolve(DuplicateResolution.REPLACE_EXISTING)
    }
    sheetView.findViewById<MaterialButton>(R.id.discardNewButton).setOnClickListener {
        resolve(DuplicateResolution.KEEP_EXISTING)
    }

    sheet.applyGlassBlur()
    sheet.show()
}
