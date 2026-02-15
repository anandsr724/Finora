package com.example.expensetracker.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.EditPaymentActivity
import com.example.expensetracker.R
import com.example.expensetracker.TransactionHistoryAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var transactionAdapter: TransactionHistoryAdapter

    companion object {
        private const val PICK_IMAGE_REQUEST = 1004
        private const val EDIT_REQUEST_CODE = 1001
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

        // FAB - opens manual entry form directly
        val fab = view.findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
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
        
        // Calculate monthly total
        val monthlyTotal = calculateMonthlyTotal(transactions)
        val thisMonthAmount = view.findViewById<TextView>(R.id.thisMonthAmount)
        val transactionsCount = view.findViewById<TextView>(R.id.transactionsCount)
        val quickStatsContainer = view.findViewById<LinearLayout>(R.id.quickStatsContainer)
        val emptyStateCard = view.findViewById<MaterialCardView>(R.id.emptyStateCard)
        val viewAllButton = view.findViewById<TextView>(R.id.viewAllButton)

        // Format amounts
        val numberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))
        thisMonthAmount.text = "₹${numberFormat.format(monthlyTotal)}"
        transactionsCount.text = transactions.size.toString()

        // Show/hide empty state
        if (transactions.isEmpty()) {
            emptyStateCard.visibility = View.VISIBLE
            quickStatsContainer.visibility = View.GONE
            viewAllButton.visibility = View.GONE
        } else {
            emptyStateCard.visibility = View.GONE
            quickStatsContainer.visibility = View.VISIBLE
            if (transactions.size > 3) {
                viewAllButton.visibility = View.VISIBLE
            }
        }

        // Load recent transactions (max 3)
        val recentTransactions = transactions.take(3)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recent_transactions_list)
        
        if (recentTransactions.isNotEmpty()) {
            transactionAdapter = TransactionHistoryAdapter(recentTransactions, categoryManager)
            recyclerView.adapter = transactionAdapter
            recyclerView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.GONE
        }
    }

    private fun calculateMonthlyTotal(transactions: List<com.example.expensetracker.PaymentTransaction>): Double {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        return transactions.filter { transaction ->
            try {
                val date = dateFormat.parse(transaction.dateTime)
                if (date != null) {
                    calendar.time = date
                    val transactionMonth = calendar.get(Calendar.MONTH)
                    val transactionYear = calendar.get(Calendar.YEAR)
                    transactionMonth == currentMonth && transactionYear == currentYear
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }.sumOf { it.amount.replace("₹", "").replace(",", "").toDoubleOrNull() ?: 0.0 }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        view?.let { loadData(it) }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
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
        startActivityForResult(intent, EDIT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data
            // Navigate to AddFragment with the image URI
            val bundle = android.os.Bundle()
            bundle.putString("imageUri", imageUri.toString())
            findNavController().navigate(R.id.nav_add, bundle)
        }

        if (requestCode == EDIT_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            // Save the transaction data returned from EditPaymentActivity
            val amount = data.getStringExtra("amount") ?: ""
            val recipient = data.getStringExtra("recipient") ?: ""
            val dateTime = data.getStringExtra("dateTime") ?: ""
            val transactionId = data.getStringExtra("transactionId") ?: ""
            val note = data.getStringExtra("note") ?: ""
            val bankInfo = data.getStringExtra("bankInfo") ?: ""
            val category = data.getStringExtra("category") ?: "cat_other"
            val editingId = data.getStringExtra("editingId")

            if (editingId != null) {
                // Update existing transaction
                val updatedTransaction = com.example.expensetracker.PaymentTransaction(
                    id = editingId,
                    amount = amount,
                    recipient = recipient,
                    note = note,
                    dateTime = dateTime,
                    transactionId = transactionId,
                    bankInfo = bankInfo,
                    category = category
                )
                csvManager.updateTransaction(updatedTransaction)
            } else {
                // Save new transaction
                val transaction = com.example.expensetracker.PaymentTransaction(
                    amount = amount,
                    recipient = recipient,
                    note = note,
                    dateTime = dateTime,
                    transactionId = transactionId,
                    bankInfo = bankInfo,
                    category = category
                )
                csvManager.saveTransaction(transaction)
            }
            
            // Refresh data when transaction is saved
            view?.let { loadData(it) }
        }
    }
}