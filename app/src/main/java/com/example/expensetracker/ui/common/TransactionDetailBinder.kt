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
import com.example.expensetracker.data.categoryAmountBreakdown
import com.example.expensetracker.data.isSplit
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Populates the shared `layout_transaction_detail_content.xml` views for [transaction] and
 * wires its Close/Edit/Split/Delete actions. Used by MainActivity's docked detail sheet — pulled
 * out of HomeFragment/HistoryFragment (which used to each inflate and populate their own copy of
 * this layout inside a BottomSheetDialog) so both call sites share one implementation.
 */
fun bindTransactionDetail(
    view: View,
    transaction: PaymentTransaction,
    categoryManager: CategoryManager,
    onClose: () -> Unit,
    onEdit: (PaymentTransaction) -> Unit,
    onSplit: (PaymentTransaction) -> Unit,
    onDelete: (PaymentTransaction) -> Unit
) {
    val context = view.context
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    val currencySymbol = CurrencyManager.getSymbol(transaction.currency)

    val isIncome = transaction.type == "income"
    val numericAmount = CurrencyManager.parseAmount(transaction.amount)
    view.findViewById<TextView>(R.id.detailAmount).apply {
        text = "${if (isIncome) "+" else "-"}$currencySymbol${fmt.format(numericAmount)}"
        setTextColor(
            ContextCompat.getColor(context, if (isIncome) R.color.color_income else R.color.color_expense)
        )
    }

    val badgeContainer = view.findViewById<View>(R.id.detailCategoryBadgeContainer)
    val splitContainer = view.findViewById<View>(R.id.detailSplitBreakdownContainer)
    val splitList = view.findViewById<LinearLayout>(R.id.detailSplitBreakdownList)

    if (transaction.isSplit()) {
        badgeContainer.visibility = View.GONE
        splitContainer.visibility = View.VISIBLE

        val breakdown = transaction.categoryAmountBreakdown()
        view.findViewById<TextView>(R.id.detailSplitBreakdownHeader).text =
            "Split · ${breakdown.size} categories"

        splitList.removeAllViews()
        val density = context.resources.displayMetrics.density
        breakdown.forEachIndexed { idx, (catId, amount) ->
            val color = ContextCompat.getColor(context, CategoryIconHelper.getIconTintColorRes(catId))
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }
            val dot = View(context).apply {
                val size = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (10 * density).toInt() }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                }
            }
            val nameView = TextView(context).apply {
                text = categoryManager.getCategoryDisplayName(catId)
                textSize = 13f
                setTextColor(context.themeColor(R.attr.colorOnSurface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val amountView = TextView(context).apply {
                text = "$currencySymbol${fmt.format(amount)}"
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(context.themeColor(R.attr.colorOnSurface))
            }
            row.addView(dot)
            row.addView(nameView)
            row.addView(amountView)
            splitList.addView(row)
            if (idx < breakdown.size - 1) {
                splitList.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(context.themeColor(R.attr.colorOnSurfaceFaint))
                })
            }
        }
    } else {
        badgeContainer.visibility = View.VISIBLE
        splitContainer.visibility = View.GONE

        val categoryColor = ContextCompat.getColor(context, CategoryIconHelper.getIconTintColorRes(transaction.category))
        view.findViewById<ImageView>(R.id.detailCategoryIcon).apply {
            setImageResource(R.drawable.shape_dot_solid)
            setColorFilter(categoryColor)
            applyCategoryDotGlow(categoryColor)
        }
        view.findViewById<TextView>(R.id.detailCategoryBadge).text =
            categoryManager.getCategoryDisplayName(transaction.category)
    }

    view.findViewById<MaterialButton>(R.id.detailSplitButton).apply {
        text = if (transaction.isSplit()) "Edit Split" else "Split"
        setOnClickListener {
            onClose()
            onSplit(transaction)
        }
    }

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
