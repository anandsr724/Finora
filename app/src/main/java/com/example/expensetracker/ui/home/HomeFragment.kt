package com.example.expensetracker.ui.home

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.CurrencyManager
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

class HomeFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var transactionAdapter: TransactionHistoryAdapter

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val imageUri = result.data?.data
            val bundle = android.os.Bundle()
            bundle.putString("imageUri", imageUri.toString())
            findNavController().navigate(com.example.expensetracker.R.id.nav_add, bundle)
        }
    }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val amount = data.getStringExtra("amount") ?: ""
            val recipient = data.getStringExtra("recipient") ?: ""
            val dateTime = data.getStringExtra("dateTime") ?: ""
            val transactionId = data.getStringExtra("transactionId") ?: ""
            val note = data.getStringExtra("note") ?: ""
            val bankInfo = data.getStringExtra("bankInfo") ?: ""
            val category = data.getStringExtra("category") ?: "cat_other"
            val currency = data.getStringExtra("currency") ?: com.example.expensetracker.CurrencyManager.getDefault(requireContext())
            val type = data.getStringExtra("type") ?: "expense"
            val editingId = data.getStringExtra("editingId")

            if (editingId != null) {
                val updatedTransaction = com.example.expensetracker.PaymentTransaction(
                    id = editingId, amount = amount, recipient = recipient, note = note,
                    dateTime = dateTime, transactionId = transactionId, bankInfo = bankInfo,
                    category = category, currency = currency, type = type
                )
                csvManager.updateTransaction(updatedTransaction)
            } else {
                val transaction = com.example.expensetracker.PaymentTransaction(
                    amount = amount, recipient = recipient, note = note, dateTime = dateTime,
                    transactionId = transactionId, bankInfo = bankInfo, category = category,
                    currency = currency, type = type
                )
                csvManager.saveTransaction(transaction)
            }
            view?.let { loadData(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        csvManager = CSVManager(requireContext())
        categoryManager = CategoryManager(requireContext())

        setupViews(view)
        loadData(view)

        return view
    }

    private fun setupViews(view: View) {
        // Upload button - opens file picker directly
        val uploadButton = view.findViewById<MaterialButton>(R.id.uploadButton)
        uploadButton.setOnClickListener {
            openFilePicker()
        }

        // Manual Entry button - opens edit form directly
        val manualEntryButton = view.findViewById<MaterialButton>(R.id.manualEntryButton)
        manualEntryButton.setOnClickListener {
            openManualEntryForm()
        }

        // View All button
        val viewAllButton = view.findViewById<TextView>(R.id.viewAllButton)
        viewAllButton.setOnClickListener {
            findNavController().navigate(R.id.nav_history)
        }

        // RecyclerView setup
        val recyclerView = view.findViewById<RecyclerView>(R.id.recent_transactions_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadData(view: View) {
        val transactions = csvManager.getAllTransactions()
        val numberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))

        // Date subtitle
        val dateSubtitle = view.findViewById<TextView>(R.id.dateSubtitle)
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH)
        dateSubtitle.text = dateFormat.format(Date())

        val defaultCurrency = CurrencyManager.getDefault(requireContext())
        val sym = CurrencyManager.getSymbol(defaultCurrency)

        // Net balance = total income − total expenses
        val totalIncome  = transactions.filter { it.type == "income" }.sumOf {
            CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency)
        }
        val totalExpense = transactions.filter { it.type == "expense" }.sumOf {
            CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency)
        }
        val netBalance = totalIncome - totalExpense
        val totalBalanceAmount = view.findViewById<TextView>(R.id.totalBalanceAmount)
        totalBalanceAmount.text = "$sym${numberFormat.format(netBalance)}"

        // Monthly expense and monthly income
        val monthlyExpense = calculateMonthlyTotal(transactions, defaultCurrency)
        val monthlyIncome  = calculateMonthlyIncome(transactions, defaultCurrency)
        val thisMonthAmount = view.findViewById<TextView>(R.id.thisMonthAmount)
        val transactionsCount = view.findViewById<TextView>(R.id.transactionsCount)
        val quickStatsContainer = view.findViewById<LinearLayout>(R.id.quickStatsContainer)
        val emptyStateCard = view.findViewById<MaterialCardView>(R.id.emptyStateCard)
        val viewAllButton = view.findViewById<TextView>(R.id.viewAllButton)

        thisMonthAmount.text = "$sym${numberFormat.format(monthlyExpense)}"
        transactionsCount.text = "$sym${numberFormat.format(monthlyIncome)}"

        // Show/hide empty state
        if (transactions.isEmpty()) {
            emptyStateCard.visibility = View.VISIBLE
            quickStatsContainer.visibility = View.GONE
            viewAllButton.visibility = View.GONE
        } else {
            emptyStateCard.visibility = View.GONE
            quickStatsContainer.visibility = View.VISIBLE
            if (transactions.size > 5) {
                viewAllButton.visibility = View.VISIBLE
            }
        }

        // Load recent transactions sorted by date, newest first
        val txDateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        val recentTransactions = transactions
            .sortedByDescending { tx ->
                try { txDateFormat.parse(tx.dateTime)?.time ?: 0L } catch (e: Exception) { 0L }
            }
            .take(5)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recent_transactions_list)
        
        if (recentTransactions.isNotEmpty()) {
            transactionAdapter = TransactionHistoryAdapter(recentTransactions, categoryManager) { transaction ->
                showTransactionDetailSheet(transaction)
            }
            recyclerView.adapter = transactionAdapter
            recyclerView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.GONE
        }
    }

    private fun calculateMonthlyTotal(
        transactions: List<com.example.expensetracker.PaymentTransaction>,
        defaultCurrency: String = CurrencyManager.getDefault(requireContext())
    ): Double {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        return transactions.filter { transaction ->
            if (transaction.type != "expense") return@filter false
            try {
                val date = dateFormat.parse(transaction.dateTime)
                if (date != null) {
                    calendar.time = date
                    calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear
                } else false
            } catch (e: Exception) { false }
        }.sumOf {
            CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency)
        }
    }

    private fun calculateMonthlyIncome(
        transactions: List<com.example.expensetracker.PaymentTransaction>,
        defaultCurrency: String = CurrencyManager.getDefault(requireContext())
    ): Double {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        return transactions.filter { transaction ->
            if (transaction.type != "income") return@filter false
            try {
                val date = dateFormat.parse(transaction.dateTime)
                if (date != null) {
                    calendar.time = date
                    calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear
                } else false
            } catch (e: Exception) { false }
        }.sumOf {
            CurrencyManager.convert(CurrencyManager.parseAmount(it.amount), it.currency, defaultCurrency)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        view?.let { loadData(it) }
    }

    private fun showTransactionDetailSheet(transaction: PaymentTransaction) {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_transaction_detail_sheet, null)
        sheet.setContentView(sheetView)

        val fmt = NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))

        sheetView.findViewById<ImageView>(R.id.detailCategoryIcon)
            .setImageResource(CategoryIconHelper.getIconResId(transaction.category))

        val numericAmount = CurrencyManager.parseAmount(transaction.amount)
        val currencySymbol = CurrencyManager.getSymbol(transaction.currency)
        sheetView.findViewById<TextView>(R.id.detailAmount).text =
            "$currencySymbol${fmt.format(numericAmount)}"

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
            val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.ENGLISH).parse(transaction.dateTime)
            sheetView.findViewById<TextView>(R.id.detailDateTime).text =
                if (date != null) SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.ENGLISH).format(date)
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
                view?.let { loadData(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun openManualEntryForm() {
        val intent = Intent(requireContext(), EditPaymentActivity::class.java).apply {
            putExtra("amount", "")
            putExtra("recipient", "")
            putExtra("note", "")
            putExtra("dateTime", "")
            putExtra("transactionId", "")
            putExtra("bankInfo", "")
            putExtra("category", "cat_other")
            putExtra("isManualEntry", true)
        }
        editLauncher.launch(intent)
    }
}