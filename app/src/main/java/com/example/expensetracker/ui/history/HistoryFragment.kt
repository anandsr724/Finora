package com.example.expensetracker.ui.history

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.expensetracker.ui.common.themeColor
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.Category
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.CurrencyManager
import com.example.expensetracker.EditPaymentActivity
import com.example.expensetracker.PaymentTransaction
import com.example.expensetracker.R
import com.example.expensetracker.TransactionHistoryAdapter
import com.example.expensetracker.ui.common.applyCategoryDotGlow
import com.example.expensetracker.ui.common.applyGlassBlur
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyStateMessage: TextView
    private lateinit var historyMonthSpinner: Spinner
    private lateinit var filterCategoriesButton: MaterialButton
    private lateinit var filterAllButton: MaterialButton
    private lateinit var activeCategoryFiltersScroll: android.widget.HorizontalScrollView
    private lateinit var activeCategoryFiltersContainer: LinearLayout
    private lateinit var categoryFilterPillsContainer: LinearLayout

    private enum class SortOption(val label: String) {
        DATE_DESC("Newest First"),
        DATE_ASC("Oldest First"),
        AMOUNT_DESC("Amount: High-Low"),
        AMOUNT_ASC("Amount: Low-High")
    }

    private var allTransactions: List<PaymentTransaction> = emptyList()
    private val selectedCategories = mutableSetOf<String>()
    private var searchQuery: String = ""
    private var selectedMonthIndex = 0 // 0 = All Time
    private var currentSort = SortOption.DATE_DESC
    private val categoryPillMap = mutableMapOf<String, MaterialButton>()
    private val monthYearPairs = mutableListOf<Pair<Int, Int>>() // (year, month) for spinner positions 1+

    private lateinit var filterCategoriesBadge: TextView

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val editingId = data.getStringExtra("editingId") ?: return@registerForActivityResult
            val updatedTransaction = PaymentTransaction(
                id = editingId,
                amount = data.getStringExtra("amount") ?: "",
                recipient = data.getStringExtra("recipient") ?: "",
                note = data.getStringExtra("note") ?: "",
                dateTime = data.getStringExtra("dateTime") ?: "",
                transactionId = data.getStringExtra("transactionId") ?: "",
                bankInfo = data.getStringExtra("bankInfo") ?: "",
                category = data.getStringExtra("category") ?: "cat_other",
                currency = data.getStringExtra("currency") ?: CurrencyManager.getDefault(requireContext()),
                type = data.getStringExtra("type") ?: "expense"
            )
            csvManager.updateTransaction(updatedTransaction)
            Toast.makeText(requireContext(), "Transaction updated", Toast.LENGTH_SHORT).show()
            loadTransactions()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        csvManager = CSVManager(requireContext())
        categoryManager = CategoryManager(requireContext())
        categoryManager.initializeDefaultCategories()

        setupViews(view)
        setupSearch()
        loadTransactions()

        return view
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.transactions_history_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        searchEditText = view.findViewById(R.id.searchEditText)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyStateMessage = view.findViewById(R.id.emptyStateMessage)
        historyMonthSpinner = view.findViewById(R.id.historyMonthSpinner)
        filterCategoriesButton = view.findViewById(R.id.filterCategoriesButton)
        filterAllButton = view.findViewById(R.id.filterAllButton)
        activeCategoryFiltersScroll = view.findViewById(R.id.activeCategoryFiltersScroll)
        activeCategoryFiltersContainer = view.findViewById(R.id.activeCategoryFiltersContainer)
        categoryFilterPillsContainer = view.findViewById(R.id.categoryFilterPillsContainer)

        filterCategoriesBadge = view.findViewById(R.id.filterCategoriesBadge)
        filterCategoriesButton.setOnClickListener { showCategoryFilterSheet() }

        val sortButton = view.findViewById<MaterialButton>(R.id.sortButton)
        sortButton.setOnClickListener {
            val options = SortOption.values().map { it.label }.toTypedArray()
            val currentIdx = currentSort.ordinal
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Sort by")
                .setSingleChoiceItems(options, currentIdx) { dialog, idx ->
                    currentSort = SortOption.values()[idx]
                    sortButton.text = currentSort.label
                    filterTransactions()
                    dialog.dismiss()
                }
                .show()
        }

        filterAllButton.setOnClickListener {
            selectedCategories.clear()
            updateCategoryFilterPillsState()
            updateAllButtonState()
            activeCategoryFiltersScroll.visibility = View.GONE
            filterTransactions()
        }
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                filterTransactions()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupMonthSpinner(transactions: List<PaymentTransaction>) {
        val options = mutableListOf("All Time")
        monthYearPairs.clear()

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        val distinctMonths = transactions.mapNotNull { tx ->
            try {
                val d = dateFormat.parse(tx.dateTime) ?: return@mapNotNull null
                cal.time = d
                Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            } catch (e: Exception) { null }
        }.distinct().sortedByDescending { (y, m) -> y * 12 + m }

        val monthLabelFormat = SimpleDateFormat("MMMM yyyy", Locale("en", "IN"))
        distinctMonths.forEach { (year, month) ->
            cal.set(year, month, 1)
            options.add(monthLabelFormat.format(cal.time))
            monthYearPairs.add(Pair(year, month))
        }

        // Preserve current selection by year/month value
        val currentYearMonth = if (selectedMonthIndex > 0 && selectedMonthIndex <= monthYearPairs.size) {
            monthYearPairs[selectedMonthIndex - 1]
        } else null

        historyMonthSpinner.onItemSelectedListener = null
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_month, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_month)
        historyMonthSpinner.adapter = adapter

        val newPos = if (currentYearMonth != null) {
            val idx = monthYearPairs.indexOf(currentYearMonth)
            if (idx >= 0) idx + 1 else 0
        } else 0
        selectedMonthIndex = newPos
        historyMonthSpinner.setSelection(newPos)

        historyMonthSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMonthIndex = position
                filterTransactions()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun populateCategoryFilterPills() {
        categoryFilterPillsContainer.removeAllViews()
        categoryPillMap.clear()

        // Row now scrolls horizontally (matches the Stitch reference), so every used category
        // gets a pill — no more fitting a fixed count before overflowing into "more".
        val usedCategoryIds = allTransactions.map { it.category }.distinct()
        val density = resources.displayMetrics.density

        usedCategoryIds.forEach { catId ->
            val pill = MaterialButton(
                requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (40 * density).toInt()
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                insetTop = 0
                insetBottom = 0
                setIconResource(CategoryIconHelper.getIconResId(catId))
                iconSize = (18 * density).toInt()
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = (8 * density).toInt()
                text = categoryManager.getCategoryDisplayName(catId)
                textSize = 13f
                isAllCaps = false
                cornerRadius = (999 * density).toInt()
            }
            updatePillState(pill, selectedCategories.contains(catId))
            pill.setOnClickListener {
                if (selectedCategories.contains(catId)) {
                    selectedCategories.remove(catId)
                } else {
                    selectedCategories.add(catId)
                }
                updatePillState(pill, selectedCategories.contains(catId))
                updateAllButtonState()
                filterTransactions()
            }
            categoryPillMap[catId] = pill
            categoryFilterPillsContainer.addView(pill)
        }
    }

    private fun updateCategoryFilterPillsState() {
        categoryPillMap.forEach { (catId, pill) ->
            updatePillState(pill, selectedCategories.contains(catId))
        }
    }

    private fun updatePillState(pill: MaterialButton, selected: Boolean) {
        if (selected) {
            pill.backgroundTintList = ColorStateList.valueOf(
                resources.getColor(R.color.color_primary, null)
            )
            pill.iconTint = ColorStateList.valueOf(resources.getColor(R.color.color_on_primary, null))
            pill.setTextColor(resources.getColor(R.color.color_on_primary, null))
            pill.strokeWidth = 0
        } else {
            pill.backgroundTintList = ColorStateList.valueOf(requireContext().themeColor(R.attr.colorGlassFillL2))
            pill.iconTint = ColorStateList.valueOf(
                resources.getColor(R.color.color_primary, null)
            )
            pill.setTextColor(requireContext().themeColor(R.attr.colorOnSurface))
            pill.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            pill.strokeColor = ColorStateList.valueOf(requireContext().themeColor(R.attr.colorGlassBorder))
        }
    }

    private fun updateAllButtonState() {
        if (selectedCategories.isEmpty()) {
            filterAllButton.backgroundTintList = ColorStateList.valueOf(
                resources.getColor(R.color.color_primary, null)
            )
            filterAllButton.setTextColor(resources.getColor(R.color.color_on_primary, null))
            filterAllButton.strokeWidth = 0
        } else {
            filterAllButton.backgroundTintList = ColorStateList.valueOf(
                requireContext().themeColor(R.attr.colorGlassFillL2)
            )
            filterAllButton.setTextColor(requireContext().themeColor(R.attr.colorOnSurfaceMuted))
            filterAllButton.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            filterAllButton.strokeColor = ColorStateList.valueOf(requireContext().themeColor(R.attr.colorGlassBorder))
        }
        updateDotsButtonBadge()
    }

    private fun updateDotsButtonBadge() {
        val count = selectedCategories.size
        if (count > 0) {
            filterCategoriesBadge.text = count.toString()
            filterCategoriesBadge.visibility = View.VISIBLE
        } else {
            filterCategoriesBadge.visibility = View.GONE
        }
    }

    private fun showCategoryFilterSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_category_filter_sheet, null)
        sheet.setContentView(sheetView)

        val recycler = sheetView.findViewById<RecyclerView>(R.id.filterCategoryGrid)
        val clearAllBtn = sheetView.findViewById<MaterialButton>(R.id.clearAllFiltersButton)
        val applyBtn = sheetView.findViewById<MaterialButton>(R.id.applyFiltersButton)

        val tempSelected = selectedCategories.toMutableSet()
        val categories = categoryManager.getAllCategories()
        val usedCategoryIds = allTransactions.map { it.category }.distinct()
        val availableCategories = categories.filter { it.id in usedCategoryIds }

        val adapter = CategoryFilterAdapter(availableCategories, tempSelected) { count ->
            applyBtn.text = if (count == 0) "Apply" else "Apply ($count)"
        }
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        recycler.adapter = adapter

        applyBtn.text = if (tempSelected.isEmpty()) "Apply" else "Apply (${tempSelected.size})"

        clearAllBtn.setOnClickListener {
            tempSelected.clear()
            adapter.notifyDataSetChanged()
            applyBtn.text = "Apply"
        }

        applyBtn.setOnClickListener {
            selectedCategories.clear()
            selectedCategories.addAll(tempSelected)
            updateCategoryFilterPillsState()
            updateAllButtonState()
            activeCategoryFiltersScroll.visibility = View.GONE
            filterTransactions()
            sheet.dismiss()
        }

        sheetView.findViewById<ImageButton>(R.id.closeFilterSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        sheet.applyGlassBlur()
        sheet.show()
    }

    private fun updateActiveCategoryChips() {
        // Pills in the filter row show selection state; chips scroll is hidden
        activeCategoryFiltersScroll.visibility = View.GONE
    }

    private fun loadTransactions() {
        allTransactions = csvManager.getAllTransactions()
        setupMonthSpinner(allTransactions)
        populateCategoryFilterPills()
        filterTransactions()
    }

    private fun filterTransactions() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        val filtered = allTransactions.filter { tx ->
            val matchesSearch = searchQuery.isEmpty() ||
                    tx.recipient.lowercase().contains(searchQuery) ||
                    tx.note.lowercase().contains(searchQuery) ||
                    tx.amount.contains(searchQuery) ||
                    categoryManager.getCategoryDisplayName(tx.category).lowercase().contains(searchQuery)

            val matchesMonth = if (selectedMonthIndex == 0 || selectedMonthIndex > monthYearPairs.size) {
                true
            } else {
                val (targetYear, targetMonth) = monthYearPairs[selectedMonthIndex - 1]
                try {
                    val date = dateFormat.parse(tx.dateTime)
                    if (date != null) {
                        calendar.time = date
                        calendar.get(Calendar.MONTH) == targetMonth && calendar.get(Calendar.YEAR) == targetYear
                    } else false
                } catch (e: Exception) { false }
            }

            val matchesCategory = selectedCategories.isEmpty() || selectedCategories.contains(tx.category)

            matchesSearch && matchesMonth && matchesCategory
        }

        if (filtered.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            emptyStateMessage.text = if (searchQuery.isNotEmpty() || selectedCategories.isNotEmpty() || selectedMonthIndex != 0) {
                "Try adjusting your search or filters"
            } else {
                "Upload a payment screenshot to add your first transaction"
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            val sorted = when (currentSort) {
                SortOption.DATE_DESC -> filtered.sortedByDescending { tx ->
                    try { dateFormat.parse(tx.dateTime)?.time ?: 0L } catch (e: Exception) { 0L }
                }
                SortOption.DATE_ASC -> filtered.sortedBy { tx ->
                    try { dateFormat.parse(tx.dateTime)?.time ?: Long.MAX_VALUE } catch (e: Exception) { Long.MAX_VALUE }
                }
                SortOption.AMOUNT_DESC -> filtered.sortedByDescending { CurrencyManager.parseAmount(it.amount) }
                SortOption.AMOUNT_ASC -> filtered.sortedBy { CurrencyManager.parseAmount(it.amount) }
            }
            recyclerView.adapter = TransactionHistoryAdapter(sorted, categoryManager) { transaction ->
                showTransactionDetailSheet(transaction)
            }
        }
    }

    private fun showTransactionDetailSheet(transaction: PaymentTransaction) {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_transaction_detail_sheet, null)
        sheet.setContentView(sheetView)

        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))

        val categoryColor = ContextCompat.getColor(
            requireContext(),
            CategoryIconHelper.getIconTintColorRes(transaction.category)
        )
        sheetView.findViewById<ImageView>(R.id.detailCategoryIcon).apply {
            setImageResource(R.drawable.shape_dot_solid)
            setColorFilter(categoryColor)
            applyCategoryDotGlow(categoryColor)
        }

        val isIncome = transaction.type == "income"
        val numericAmount = CurrencyManager.parseAmount(transaction.amount)
        val currencySymbol = CurrencyManager.getSymbol(transaction.currency)
        sheetView.findViewById<TextView>(R.id.detailAmount).apply {
            text = "${if (isIncome) "+" else "-"}$currencySymbol${fmt.format(numericAmount)}"
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isIncome) R.color.color_income else R.color.color_expense
                )
            )
        }

        sheetView.findViewById<TextView>(R.id.detailCategoryBadge).text =
            categoryManager.getCategoryDisplayName(transaction.category)

        sheetView.findViewById<TextView>(R.id.detailRecipient).text = transaction.recipient

        val noteRow = sheetView.findViewById<LinearLayout>(R.id.detailNoteRow)
        val noteDivider = sheetView.findViewById<View>(R.id.detailNoteDivider)
        if (transaction.note.isNotEmpty()) {
            sheetView.findViewById<TextView>(R.id.detailNote).text = transaction.note
            noteRow.visibility = View.VISIBLE
            noteDivider.visibility = View.VISIBLE
        } else {
            noteRow.visibility = View.GONE
            noteDivider.visibility = View.GONE
        }

        try {
            val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(transaction.dateTime)
            sheetView.findViewById<TextView>(R.id.detailDateTime).text =
                if (date != null) SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(date)
                else transaction.dateTime
        } catch (e: Exception) {
            sheetView.findViewById<TextView>(R.id.detailDateTime).text = transaction.dateTime
        }

        sheetView.findViewById<TextView>(R.id.detailPaymentMethod).text =
            transaction.bankInfo.ifEmpty { "N/A" }

        sheetView.findViewById<TextView>(R.id.detailTransactionId).text =
            transaction.transactionId.ifEmpty { "N/A" }

        sheetView.findViewById<ImageButton>(R.id.closeDetailSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<MaterialButton>(R.id.detailEditButton).setOnClickListener {
            sheet.dismiss()
            editTransaction(transaction)
        }

        sheetView.findViewById<MaterialButton>(R.id.detailDeleteButton).setOnClickListener {
            sheet.dismiss()
            confirmDeleteTransaction(transaction)
        }

        sheet.show()
        sheet.applyGlassBlur()
    }

    private fun editTransaction(transaction: PaymentTransaction) {
        val intent = Intent(requireContext(), EditPaymentActivity::class.java).apply {
            putExtra("amount", transaction.amount)
            putExtra("recipient", transaction.recipient)
            putExtra("note", transaction.note)
            putExtra("dateTime", transaction.dateTime)
            putExtra("bankInfo", transaction.bankInfo)
            putExtra("category", transaction.category)
            putExtra("transactionId", transaction.transactionId)
            putExtra("editingId", transaction.id)
            putExtra("currency", transaction.currency)
            putExtra("type", transaction.type)
        }
        editLauncher.launch(intent)
    }

    private fun confirmDeleteTransaction(transaction: PaymentTransaction) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                csvManager.deleteTransaction(transaction.id)
                Toast.makeText(requireContext(), "Transaction deleted", Toast.LENGTH_SHORT).show()
                loadTransactions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadTransactions()
    }

    private inner class CategoryFilterAdapter(
        private val categories: List<Category>,
        private val selectedIds: MutableSet<String>,
        private val onSelectionChanged: (Int) -> Unit
    ) : RecyclerView.Adapter<CategoryFilterAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.categoryPickerCard)
            val iconCard: MaterialCardView = itemView.findViewById(R.id.categoryIconCard)
            val icon: ImageView = itemView.findViewById(R.id.categoryPickerIcon)
            val name: TextView = itemView.findViewById(R.id.categoryPickerName)
            val check: ImageView = itemView.findViewById(R.id.categoryPickerCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            val isSelected = selectedIds.contains(category.id)
            val tint = ContextCompat.getColor(requireContext(), CategoryIconHelper.getIconTintColorRes(category.id))

            holder.icon.setImageResource(CategoryIconHelper.getIconResId(category.id))
            holder.name.text = category.name
            holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE

            if (isSelected) {
                holder.card.setCardBackgroundColor(withAlpha(tint, 0x26))
                holder.card.strokeColor = tint
                holder.card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                holder.iconCard.setCardBackgroundColor(tint)
                holder.icon.imageTintList = ColorStateList.valueOf(
                    resources.getColor(R.color.color_on_primary, null)
                )
                holder.name.setTextColor(requireContext().themeColor(R.attr.colorOnSurface))
            } else {
                holder.card.setCardBackgroundColor(requireContext().themeColor(R.attr.colorGlassFillL2))
                holder.card.strokeColor = requireContext().themeColor(R.attr.colorGlassBorder)
                holder.card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                holder.iconCard.setCardBackgroundColor(withAlpha(tint, 0x26))
                holder.icon.imageTintList = ColorStateList.valueOf(tint)
                holder.name.setTextColor(requireContext().themeColor(R.attr.colorOnSurfaceMuted))
            }

            holder.card.setOnClickListener {
                if (selectedIds.contains(category.id)) {
                    selectedIds.remove(category.id)
                } else {
                    selectedIds.add(category.id)
                }
                notifyItemChanged(position)
                onSelectionChanged(selectedIds.size)
            }
        }

        override fun getItemCount() = categories.size

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
