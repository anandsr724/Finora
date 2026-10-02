package com.example.expensetracker.ui.common

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.CurrencyManager
import com.example.expensetracker.PaymentTransaction
import com.example.expensetracker.R
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Populates the shared `layout_transaction_detail_content.xml` views for [transaction] and
 * wires its Close/Edit/Delete actions. Used by MainActivity's docked detail sheet — pulled out
 * of HomeFragment/HistoryFragment (which used to each inflate and populate their own copy of
 * this layout inside a BottomSheetDialog) so both call sites share one implementation.
 */
fun bindTransactionDetail(
    view: View,
    transaction: PaymentTransaction,
    categoryManager: CategoryManager,
    onClose: () -> Unit,
    onEdit: (PaymentTransaction) -> Unit,
    onDelete: (PaymentTransaction) -> Unit
) {
    val context = view.context
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))

    val categoryColor = ContextCompat.getColor(context, CategoryIconHelper.getIconTintColorRes(transaction.category))
    view.findViewById<ImageView>(R.id.detailCategoryIcon).apply {
        setImageResource(R.drawable.shape_dot_solid)
        setColorFilter(categoryColor)
        applyCategoryDotGlow(categoryColor)
    }

    val isIncome = transaction.type == "income"
    val numericAmount = CurrencyManager.parseAmount(transaction.amount)
    val currencySymbol = CurrencyManager.getSymbol(transaction.currency)
    view.findViewById<TextView>(R.id.detailAmount).apply {
        text = "${if (isIncome) "+" else "-"}$currencySymbol${fmt.format(numericAmount)}"
        setTextColor(
            ContextCompat.getColor(context, if (isIncome) R.color.color_income else R.color.color_expense)
        )
    }

    view.findViewById<TextView>(R.id.detailCategoryBadge).text =
        categoryManager.getCategoryDisplayName(transaction.category)

    view.findViewById<TextView>(R.id.detailRecipient).text = transaction.recipient

    val noteRow = view.findViewById<LinearLayout>(R.id.detailNoteRow)
    val noteDivider = view.findViewById<View>(R.id.detailNoteDivider)
    if (transaction.note.isNotEmpty()) {
        view.findViewById<TextView>(R.id.detailNote).text = transaction.note
        noteRow.visibility = View.VISIBLE
        noteDivider.visibility = View.VISIBLE
    } else {
        noteRow.visibility = View.GONE
        noteDivider.visibility = View.GONE
    }

    try {
        val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(transaction.dateTime)
        view.findViewById<TextView>(R.id.detailDateTime).text =
            if (date != null) SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(date)
            else transaction.dateTime
    } catch (e: Exception) {
        view.findViewById<TextView>(R.id.detailDateTime).text = transaction.dateTime
    }

    view.findViewById<TextView>(R.id.detailPaymentMethod).text =
        transaction.bankInfo.ifEmpty { "N/A" }

    view.findViewById<TextView>(R.id.detailTransactionId).text =
        transaction.transactionId.ifEmpty { "N/A" }

    view.findViewById<ImageButton>(R.id.closeDetailSheetButton).setOnClickListener { onClose() }
    view.findViewById<MaterialButton>(R.id.detailEditButton).setOnClickListener {
        onClose()
        onEdit(transaction)
    }
    view.findViewById<MaterialButton>(R.id.detailDeleteButton).setOnClickListener {
        onClose()
        onDelete(transaction)
    }
}
