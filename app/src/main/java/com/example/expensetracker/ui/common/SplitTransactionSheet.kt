package com.example.expensetracker.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
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
import com.example.expensetracker.data.SplitBreakdownCodec
import com.example.expensetracker.data.TransactionSplit
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.Locale

/**
 * Bottom sheet to split a transaction's total amount across multiple category lines (or edit/
 * remove an existing split). [onSave] receives the new encoded `splitBreakdown` — an empty
 * string means "not split" (the transaction reverts to its single original category).
 *
 * Follows the same free-function + BottomSheetDialog + `resolved` guard convention as
 * DuplicateResolutionSheet/BulkDuplicateResolutionSheet.
 */
fun showSplitTransactionSheet(
    context: Context,
    inflater: LayoutInflater,
    transaction: PaymentTransaction,
    categoryManager: CategoryManager,
    onSave: (newSplitBreakdown: String) -> Unit
) {
    val sheet = BottomSheetDialog(context)
    val sheetView = inflater.inflate(R.layout.layout_split_transaction_sheet, null)
    sheet.setContentView(sheetView)

    val totalAmount = CurrencyManager.parseAmount(transaction.amount)
    val currencySymbol = CurrencyManager.getSymbol(transaction.currency)
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    val totalCents = Math.round(totalAmount * 100)

    val titleView = sheetView.findViewById<TextView>(R.id.splitSheetTitle)
    val totalText = sheetView.findViewById<TextView>(R.id.splitTotalText)
    val remainingText = sheetView.findViewById<TextView>(R.id.splitRemainingText)
    val scrollView = sheetView.findViewById<androidx.core.widget.NestedScrollView>(R.id.splitLinesScrollView)
    val linesContainer = sheetView.findViewById<LinearLayout>(R.id.splitLinesContainer)
    val addButton = sheetView.findViewById<MaterialButton>(R.id.addSplitLineButton)
    val saveButton = sheetView.findViewById<MaterialButton>(R.id.saveSplitButton)
    val removeSplitButton = sheetView.findViewById<MaterialButton>(R.id.removeSplitButton)
    val closeButton = sheetView.findViewById<ImageButton>(R.id.closeSplitSheetButton)

    val existingSplits = SplitBreakdownCodec.decode(transaction.splitBreakdown)
    val isEditingExisting = existingSplits.size >= 2

    titleView.text = if (isEditingExisting) "Edit Split" else "Split Transaction"
    totalText.text = "Total: $currencySymbol${fmt.format(totalAmount)}"
    removeSplitButton.visibility = if (isEditingExisting) View.VISIBLE else View.GONE

    data class LineRow(val root: View, var categoryId: String, val amountInput: EditText, val icon: ImageView, val iconBg: View, val nameView: TextView)

    val rows = mutableListOf<LineRow>()

    fun applyCategoryToRow(row: LineRow, categoryId: String) {
        row.categoryId = categoryId
        val category = categoryManager.getCategoryById(categoryId)
        val tint = ContextCompat.getColor(context, CategoryIconHelper.getIconTintColorRes(categoryId))
        row.icon.setImageResource(CategoryIconHelper.getIconResId(categoryId))
        row.icon.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_on_primary))
        (row.iconBg.background as? android.graphics.drawable.GradientDrawable)?.setColor(tint)
        row.nameView.text = category?.name ?: categoryManager.getCategoryDisplayName(categoryId)
        row.nameView.setTextColor(context.themeColor(R.attr.colorOnSurface))
    }

    // Rows with no category selected yet are placeholders, not real split lines — excluded from
    // the sum, validation, and (at save time) the persisted payload, so a stray blank row never
    // blocks Save or ends up silently saved as an empty entry.
    fun realRows() = rows.filter { it.categoryId.isNotEmpty() }

    fun currentSum(): Long = realRows().sumOf { Math.round((it.amountInput.text.toString().toDoubleOrNull() ?: 0.0) * 100) }

    fun refreshValidation() {
        val sumCents = currentSum()
        val remainingCents = totalCents - sumCents
        when {
            remainingCents == 0L -> {
                remainingText.text = "✓ Fully allocated"
                remainingText.setTextColor(ContextCompat.getColor(context, R.color.color_income))
            }
            remainingCents > 0 -> {
                remainingText.text = "Remaining: $currencySymbol${fmt.format(remainingCents / 100.0)}"
                remainingText.setTextColor(ContextCompat.getColor(context, R.color.amber_500))
            }
            else -> {
                remainingText.text = "Over by $currencySymbol${fmt.format(-remainingCents / 100.0)}"
                remainingText.setTextColor(ContextCompat.getColor(context, R.color.color_expense))
            }
        }
        val real = realRows()
        val linesValid = real.size >= 2 && real.all { (it.amountInput.text.toString().toDoubleOrNull() ?: 0.0) > 0.0 }
        saveButton.isEnabled = linesValid && remainingCents == 0L
    }

    fun addRow(initialCategoryId: String, initialAmount: String): LineRow {
        val rowView = inflater.inflate(R.layout.item_split_line, linesContainer, false)
        val chip = rowView.findViewById<LinearLayout>(R.id.splitLineCategoryChip)
        val icon = rowView.findViewById<ImageView>(R.id.splitLineIcon)
        val iconBg = rowView.findViewById<View>(R.id.splitLineIconBg)
        val nameView = rowView.findViewById<TextView>(R.id.splitLineCategoryName)
        val amountInput = rowView.findViewById<EditText>(R.id.splitLineAmountInput)
        val removeButton = rowView.findViewById<ImageButton>(R.id.splitLineRemoveButton)

        val row = LineRow(rowView, initialCategoryId, amountInput, icon, iconBg, nameView)
        if (initialCategoryId.isNotEmpty()) applyCategoryToRow(row, initialCategoryId)
        if (initialAmount.isNotEmpty()) amountInput.setText(initialAmount)

        chip.setOnClickListener {
            showCategoryPickerSheet(context, inflater, categoryManager.getAllCategories(), row.categoryId, onCreateNewCategory = null) { category ->
                applyCategoryToRow(row, category.id)
                refreshValidation()
            }
        }
        amountInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshValidation() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        removeButton.setOnClickListener {
            rows.remove(row)
            linesContainer.removeView(rowView)
            refreshValidation()
        }

        rows.add(row)
        linesContainer.addView(rowView)
        return row
    }

    fun scrollToReveal(row: LineRow) {
        scrollView.post { scrollView.smoothScrollTo(0, row.root.top) }
    }

    if (isEditingExisting) {
        existingSplits.forEach { addRow(it.category, it.amount) }
    } else {
        addRow(transaction.category, transaction.amount)
        addRow("", "")
    }
    refreshValidation()

    addButton.setOnClickListener {
        val existingEmpty = rows.firstOrNull { it.categoryId.isEmpty() }
        val row = existingEmpty ?: addRow("", "").also { refreshValidation() }
        scrollToReveal(row)
    }

    var resolved = false
    fun close() {
        if (resolved) return
        resolved = true
        sheet.dismiss()
    }

    closeButton.setOnClickListener { close() }

    saveButton.setOnClickListener {
        val splits = realRows().map { TransactionSplit(it.categoryId, it.amountInput.text.toString().trim()) }
        val encoded = SplitBreakdownCodec.encode(splits)
        close()
        onSave(encoded)
    }

    removeSplitButton.setOnClickListener {
        close()
        onSave("")
    }

    sheet.applyGlassBlur()
    sheet.show()
}
