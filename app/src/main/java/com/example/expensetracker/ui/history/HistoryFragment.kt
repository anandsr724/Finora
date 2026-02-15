package com.example.expensetracker.ui.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.Category
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.PaymentTransaction
import com.example.expensetracker.R
import com.example.expensetracker.TransactionHistoryAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var transactionAdapter: TransactionHistoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: TextInputEditText
    private lateinit var categoryFiltersContainer: LinearLayout
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyStateMessage: android.widget.TextView
    private lateinit var filterAllChip: Chip

    private var allTransactions: List<PaymentTransaction> = emptyList()
    private var selectedCategory: String? = null
    private var searchQuery: String = ""

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
        setupCategoryFilters()
        loadTransactions()

        return view
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.transactions_history_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        searchEditText = view.findViewById(R.id.searchEditText)
        categoryFiltersContainer = view.findViewById(R.id.categoryFiltersContainer)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyStateMessage = view.findViewById(R.id.emptyStateMessage)
        filterAllChip = view.findViewById(R.id.filterAllChip)

        filterAllChip.setOnClickListener {
            selectedCategory = null
            updateFilterChips()
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

    private fun setupCategoryFilters() {
        val categories = categoryManager.getAllCategories()
        val categoryMap = mutableMapOf<String, String>()
        
        // Get unique categories from transactions
        allTransactions.forEach { transaction ->
            if (!categoryMap.containsKey(transaction.category)) {
                val category = categories.find { it.id == transaction.category }
                categoryMap[transaction.category] = category?.emoji ?: "📁"
            }
        }

        // Create filter chips for each category
        categoryMap.forEach { (categoryId, emoji) ->
            val chip = Chip(requireContext())
            chip.text = emoji
            chip.chipMinHeight = (40 * resources.displayMetrics.density).toFloat()
            chip.chipCornerRadius = 12f
            chip.isClickable = true
            chip.isCheckable = false
            
            val category = categories.find { it.id == categoryId }
            chip.contentDescription = category?.name ?: categoryId
            
            chip.setOnClickListener {
                selectedCategory = if (selectedCategory == categoryId) null else categoryId
                updateFilterChips()
                filterTransactions()
            }
            
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.marginEnd = (8 * resources.displayMetrics.density).toInt()
            categoryFiltersContainer.addView(chip, layoutParams)
        }
    }

    private fun updateFilterChips() {
        // Update "All" chip
        filterAllChip.setChipBackgroundColorResource(
            if (selectedCategory == null) R.color.primary_indigo else android.R.color.transparent
        )
        filterAllChip.setTextColor(
            resources.getColor(
                if (selectedCategory == null) R.color.white else R.color.text_secondary_light,
                null
            )
        )

        // Update category chips
        for (i in 1 until categoryFiltersContainer.childCount) {
            val chip = categoryFiltersContainer.getChildAt(i) as? Chip ?: continue
            val categoryId = chip.contentDescription?.toString() ?: continue
            val isSelected = selectedCategory == categoryId
            
            chip.setChipBackgroundColorResource(
                if (isSelected) R.color.primary_indigo else android.R.color.transparent
            )
            chip.setTextColor(
                resources.getColor(
                    if (isSelected) R.color.white else R.color.text_secondary_light,
                    null
                )
            )
        }
    }

    private fun loadTransactions() {
        allTransactions = csvManager.getAllTransactions()
        filterTransactions()
    }

    private fun filterTransactions() {
        val filtered = allTransactions.filter { transaction ->
            // Search filter
            val matchesSearch = searchQuery.isEmpty() ||
                    transaction.recipient.lowercase().contains(searchQuery) ||
                    transaction.note.lowercase().contains(searchQuery) ||
                    transaction.category.lowercase().contains(searchQuery)
            
            // Category filter
            val matchesCategory = selectedCategory == null || transaction.category == selectedCategory
            
            matchesSearch && matchesCategory
        }

        if (filtered.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            emptyStateMessage.text = if (searchQuery.isNotEmpty() || selectedCategory != null) {
                "Try adjusting your search or filters"
            } else {
                "Upload a payment screenshot to add your first transaction"
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            
            // Group by date
            val groupedTransactions = groupTransactionsByDate(filtered)
            transactionAdapter = TransactionHistoryAdapter(groupedTransactions, categoryManager)
            recyclerView.adapter = transactionAdapter
        }
    }

    private fun groupTransactionsByDate(transactions: List<PaymentTransaction>): List<PaymentTransaction> {
        // For now, just return sorted by date (newest first)
        // In a more advanced implementation, we could add date headers
        return transactions.sortedByDescending { transaction ->
            try {
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                dateFormat.parse(transaction.dateTime)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTransactions()
    }
}