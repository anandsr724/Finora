package com.example.expensetracker.ui.history

import android.app.Activity
import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.Category
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.EditPaymentActivity
import com.example.expensetracker.PaymentTransaction
import com.example.expensetracker.R
import com.example.expensetracker.TransactionHistoryAdapter
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

    private var allTransactions: List<PaymentTransaction> = emptyList()
    private val selectedCategories = mutableSetOf<String>()
    private var searchQuery: String = ""
    private var selectedMonthIndex = 0 // 0 = All Time
    private val categoryPillMap = mutableMapOf<String, MaterialButton>()
    private val monthYearPairs = mutableListOf<Pair<Int, Int>>() // (year, month) for spinner positions 1+

    companion object {
        private const val EDIT_REQUEST_CODE = 1001
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

        filterCategoriesButton.setOnClickListener { showCategoryFilterSheet() }

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
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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

        val usedCategoryIds = allTransactions.map { it.category }.distinct()
        val density = resources.displayMetrics.density

        usedCategoryIds.forEach { catId ->
            val pill = MaterialButton(
                requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                val size = (44 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (6 * density).toInt()
                }
                setPadding(0, 0, 0, 0)
                insetTop = 0
                insetBottom = 0
                setIconResource(CategoryIconHelper.getIconResId(catId))
                iconSize = (20 * density).toInt()
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 0
                text = ""
                cornerRadius = (14 * density).toInt()
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
                resources.getColor(R.color.primary_indigo, null)
            )
            pill.iconTint = ColorStateList.valueOf(resources.getColor(R.color.white, null))
            pill.strokeWidth = 0
        } else {
            pill.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.white, null))
            pill.iconTint = ColorStateList.valueOf(
                resources.getColor(R.color.text_secondary_light, null)
            )
            pill.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            pill.strokeColor = resources.getColorStateList(R.color.border_light, null)
        }
    }

    private fun updateAllButtonState() {
        if (selectedCategories.isEmpty()) {
            filterAllButton.backgroundTintList = ColorStateList.valueOf(
                resources.getColor(R.color.primary_indigo, null)
            )
            filterAllButton.setTextColor(resources.getColor(R.color.white, null))
            filterAllButton.strokeWidth = 0
        } else {
            filterAllButton.backgroundTintList = ColorStateList.valueOf(
                resources.getColor(R.color.white, null)
            )
            filterAllButton.setTextColor(resources.getColor(R.color.text_secondary_light, null))
            filterAllButton.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            filterAllButton.strokeColor = resources.getColorStateList(R.color.border_light, null)
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
            val sorted = filtered.sortedByDescending { tx ->
                try { dateFormat.parse(tx.dateTime)?.time ?: 0L } catch (e: Exception) { 0L }
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

        sheetView.findViewById<ImageView>(R.id.detailCategoryIcon)
            .setImageResource(CategoryIconHelper.getIconResId(transaction.category))

        val numericAmount = transaction.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull()
        val currencySymbol = if (transaction.currency == "INR") "Rs." else transaction.currency
        sheetView.findViewById<TextView>(R.id.detailAmount).text =
            if (numericAmount != null) "$currencySymbol${fmt.format(numericAmount)}" else transaction.amount

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
        }
        startActivityForResult(intent, EDIT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val editingId = data.getStringExtra("editingId") ?: return
            val updatedTransaction = PaymentTransaction(
                id = editingId,
                amount = data.getStringExtra("amount") ?: "",
                recipient = data.getStringExtra("recipient") ?: "",
                note = data.getStringExtra("note") ?: "",
                dateTime = data.getStringExtra("dateTime") ?: "",
                transactionId = data.getStringExtra("transactionId") ?: "",
                bankInfo = data.getStringExtra("bankInfo") ?: "",
                category = data.getStringExtra("category") ?: "cat_other"
            )
            csvManager.updateTransaction(updatedTransaction)
            Toast.makeText(requireContext(), "Transaction updated", Toast.LENGTH_SHORT).show()
            loadTransactions()
        }
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
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            val isSelected = selectedIds.contains(category.id)

            holder.icon.setImageResource(CategoryIconHelper.getIconResId(category.id))
            holder.name.text = category.name

            if (isSelected) {
                holder.card.setCardBackgroundColor(resources.getColor(R.color.primary_indigo, null))
                holder.card.strokeWidth = 0
                holder.iconCard.setCardBackgroundColor(
                    android.graphics.Color.parseColor("#5448B0")
                )
                holder.icon.imageTintList = ColorStateList.valueOf(
                    resources.getColor(R.color.white, null)
                )
                holder.name.setTextColor(resources.getColor(R.color.white, null))
            } else {
                holder.card.setCardBackgroundColor(resources.getColor(R.color.white, null))
                holder.card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                holder.iconCard.setCardBackgroundColor(
                    android.graphics.Color.parseColor("#EEF2FF")
                )
                holder.icon.imageTintList = ColorStateList.valueOf(
                    resources.getColor(R.color.primary_indigo, null)
                )
                holder.name.setTextColor(resources.getColor(R.color.text_secondary_light, null))
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
    }
}
