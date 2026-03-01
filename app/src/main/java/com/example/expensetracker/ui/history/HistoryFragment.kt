package com.example.expensetracker.ui.history

import android.app.Activity
import android.content.Intent
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: TextInputEditText
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyStateMessage: TextView
    private lateinit var historyMonthSpinner: Spinner
    private lateinit var filterCategoriesButton: MaterialButton
    private lateinit var activeCategoryFiltersScroll: android.widget.HorizontalScrollView
    private lateinit var activeCategoryFiltersContainer: LinearLayout

    private var allTransactions: List<PaymentTransaction> = emptyList()
    private val selectedCategories = mutableSetOf<String>()
    private var searchQuery: String = ""
    private var selectedMonthIndex = 0 // 0 = All Time

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
        setupMonthSpinner()
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
        activeCategoryFiltersScroll = view.findViewById(R.id.activeCategoryFiltersScroll)
        activeCategoryFiltersContainer = view.findViewById(R.id.activeCategoryFiltersContainer)

        filterCategoriesButton.setOnClickListener { showCategoryFilterSheet() }
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

    private fun setupMonthSpinner() {
        val options = mutableListOf("All Time")
        val calendar = Calendar.getInstance()
        for (i in 0 until 12) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)
            options.add(SimpleDateFormat("MMMM yyyy", Locale("en", "IN")).format(calendar.time))
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        historyMonthSpinner.adapter = adapter
        historyMonthSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMonthIndex = position
                filterTransactions()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun showCategoryFilterSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_category_filter_sheet, null)
        sheet.setContentView(sheetView)

        val container = sheetView.findViewById<ChipGroup>(R.id.filterCategoryGrid)
        val clearAllBtn = sheetView.findViewById<MaterialButton>(R.id.clearAllFiltersButton)
        val applyBtn = sheetView.findViewById<MaterialButton>(R.id.applyFiltersButton)

        val tempSelected = selectedCategories.toMutableSet()
        val categories = categoryManager.getAllCategories()
        val usedCategoryIds = allTransactions.map { it.category }.distinct()
        val availableCategories = categories.filter { it.id in usedCategoryIds }

        availableCategories.forEach { category ->
            val chip = Chip(requireContext())
            chip.text = category.name
            chip.isCheckable = true
            chip.isChecked = tempSelected.contains(category.id)
            chip.chipCornerRadius = 24f
            chip.chipMinHeight = (40 * resources.displayMetrics.density).toFloat()
            chip.setChipBackgroundColorResource(
                if (tempSelected.contains(category.id)) R.color.primary_indigo else android.R.color.transparent
            )
            chip.setTextColor(resources.getColor(
                if (tempSelected.contains(category.id)) R.color.white else R.color.text_secondary_light, null
            ))
            chip.setOnClickListener {
                if (tempSelected.contains(category.id)) {
                    tempSelected.remove(category.id)
                    chip.setChipBackgroundColorResource(android.R.color.transparent)
                    chip.setTextColor(resources.getColor(R.color.text_secondary_light, null))
                } else {
                    tempSelected.add(category.id)
                    chip.setChipBackgroundColorResource(R.color.primary_indigo)
                    chip.setTextColor(resources.getColor(R.color.white, null))
                }
                applyBtn.text = if (tempSelected.isEmpty()) "Apply" else "Apply (${tempSelected.size})"
            }
            container.addView(chip)
        }

        applyBtn.text = if (tempSelected.isEmpty()) "Apply" else "Apply (${tempSelected.size})"

        clearAllBtn.setOnClickListener {
            tempSelected.clear()
            for (i in 0 until container.childCount) {
                val chip = container.getChildAt(i) as? Chip ?: continue
                chip.isChecked = false
                chip.setChipBackgroundColorResource(android.R.color.transparent)
                chip.setTextColor(resources.getColor(R.color.text_secondary_light, null))
            }
            applyBtn.text = "Apply"
        }

        applyBtn.setOnClickListener {
            selectedCategories.clear()
            selectedCategories.addAll(tempSelected)
            updateActiveCategoryChips()
            filterTransactions()
            sheet.dismiss()
        }

        sheetView.findViewById<ImageButton>(R.id.closeFilterSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun updateActiveCategoryChips() {
        activeCategoryFiltersContainer.removeAllViews()
        if (selectedCategories.isEmpty()) {
            activeCategoryFiltersScroll.visibility = View.GONE
            filterCategoriesButton.text = "Filter"
            return
        }

        activeCategoryFiltersScroll.visibility = View.VISIBLE
        filterCategoriesButton.text = "Filter (${selectedCategories.size})"

        selectedCategories.forEach { catId ->
            val cat = categoryManager.getCategoryById(catId)
            val chip = Chip(requireContext())
            chip.text = cat?.name ?: catId
            chip.isCloseIconVisible = true
            chip.chipCornerRadius = 24f
            chip.setChipBackgroundColorResource(R.color.primary_indigo)
            chip.setTextColor(resources.getColor(R.color.white, null))
            chip.setCloseIconTintResource(R.color.white)
            chip.setOnCloseIconClickListener {
                selectedCategories.remove(catId)
                updateActiveCategoryChips()
                filterTransactions()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
            activeCategoryFiltersContainer.addView(chip, params)
        }
    }

    private fun loadTransactions() {
        allTransactions = csvManager.getAllTransactions()
        filterTransactions()
    }

    private fun filterTransactions() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        val filtered = allTransactions.filter { tx ->
            // Search filter
            val matchesSearch = searchQuery.isEmpty() ||
                    tx.recipient.lowercase().contains(searchQuery) ||
                    tx.note.lowercase().contains(searchQuery) ||
                    categoryManager.getCategoryDisplayName(tx.category).lowercase().contains(searchQuery)

            // Month filter
            val matchesMonth = if (selectedMonthIndex == 0) {
                true
            } else {
                val cal = Calendar.getInstance().apply { time = Date(); add(Calendar.MONTH, -(selectedMonthIndex - 1)) }
                val targetMonth = cal.get(Calendar.MONTH)
                val targetYear = cal.get(Calendar.YEAR)
                try {
                    val date = dateFormat.parse(tx.dateTime)
                    if (date != null) {
                        calendar.time = date
                        calendar.get(Calendar.MONTH) == targetMonth && calendar.get(Calendar.YEAR) == targetYear
                    } else false
                } catch (e: Exception) { false }
            }

            // Category multi-select filter
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

        // Category icon
        sheetView.findViewById<ImageView>(R.id.detailCategoryIcon)
            .setImageResource(CategoryIconHelper.getIconResId(transaction.category))

        // Amount
        val numericAmount = transaction.amount.replace("Rs.", "").replace(",", "").toDoubleOrNull()
        val currencySymbol = if (transaction.currency == "INR") "Rs." else transaction.currency
        sheetView.findViewById<TextView>(R.id.detailAmount).text =
            if (numericAmount != null) "$currencySymbol${fmt.format(numericAmount)}" else transaction.amount

        // Category badge
        sheetView.findViewById<TextView>(R.id.detailCategoryBadge).text =
            categoryManager.getCategoryDisplayName(transaction.category)

        // Recipient
        sheetView.findViewById<TextView>(R.id.detailRecipient).text = transaction.recipient

        // Note
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

        // Date & Time
        try {
            val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(transaction.dateTime)
            sheetView.findViewById<TextView>(R.id.detailDateTime).text =
                if (date != null) SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(date)
                else transaction.dateTime
        } catch (e: Exception) {
            sheetView.findViewById<TextView>(R.id.detailDateTime).text = transaction.dateTime
        }

        // Payment Method
        sheetView.findViewById<TextView>(R.id.detailPaymentMethod).text =
            transaction.bankInfo.ifEmpty { "N/A" }

        // Transaction ID
        sheetView.findViewById<TextView>(R.id.detailTransactionId).text =
            transaction.transactionId.ifEmpty { "N/A" }

        // Close
        sheetView.findViewById<ImageButton>(R.id.closeDetailSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        // Edit
        sheetView.findViewById<MaterialButton>(R.id.detailEditButton).setOnClickListener {
            sheet.dismiss()
            editTransaction(transaction)
        }

        // Delete
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
}
