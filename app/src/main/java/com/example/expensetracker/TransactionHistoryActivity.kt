package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageButton

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private lateinit var csvManager: CSVManager
    private var transactions = listOf<PaymentTransaction>()

    companion object {
        private const val EDIT_TRANSACTION_REQUEST = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_history)

        csvManager = CSVManager(this)

        recyclerView = findViewById(R.id.transactionsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadTransactions()
    }

    private fun loadTransactions() {
        transactions = csvManager.getAllTransactions()
        val categoryManager = CategoryManager(this)  // ADD THIS
        adapter = TransactionAdapter(transactions, categoryManager) { transaction, action ->  // PASS categoryManager
            when (action) {
                "edit" -> editTransaction(transaction)
                "delete" -> confirmDeleteTransaction(transaction)
            }
        }
        recyclerView.adapter = adapter

        // Update empty state
        findViewById<TextView>(R.id.emptyStateText).visibility =
            if (transactions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun editTransaction(transaction: PaymentTransaction) {
        val intent = Intent(this, EditPaymentActivity::class.java).apply {
            putExtra("amount", transaction.amount)
            putExtra("recipient", transaction.recipient)
            putExtra("note", transaction.note)
            putExtra("dateTime", transaction.dateTime)
            putExtra("transactionId", transaction.transactionId)
            putExtra("bankInfo", transaction.bankInfo)
            putExtra("category", transaction.category)  // ADD THIS
            putExtra("editingId", transaction.id) // Pass the ID to know which transaction to update
        }
        startActivityForResult(intent, EDIT_TRANSACTION_REQUEST)
    }

    private fun confirmDeleteTransaction(transaction: PaymentTransaction) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?\n\n${transaction.amount} to ${transaction.recipient}")
            .setPositiveButton("Delete") { _, _ ->
                deleteTransaction(transaction)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTransaction(transaction: PaymentTransaction) {
        val success = csvManager.deleteTransaction(transaction.id)
        if (success) {
            Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
            loadTransactions() // Reload the list
        } else {
            Toast.makeText(this, "Error deleting transaction", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_TRANSACTION_REQUEST && resultCode == RESULT_OK && data != null) {
            val editingId = data.getStringExtra("editingId") ?: return

            val updatedTransaction = PaymentTransaction(
                id = editingId, // Keep the same ID
                amount = data.getStringExtra("amount") ?: "",
                recipient = data.getStringExtra("recipient") ?: "",
                note = data.getStringExtra("note") ?: "",
                dateTime = data.getStringExtra("dateTime") ?: "",
                transactionId = data.getStringExtra("transactionId") ?: "",
                bankInfo = data.getStringExtra("bankInfo") ?: "",
                category = data.getStringExtra("category") ?: "cat_other",  // ADD THIS
                createdAt = transactions.find { it.id == editingId }?.createdAt ?: ""
            )

            val success = csvManager.updateTransaction(updatedTransaction)
            if (success) {
                Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show()
                loadTransactions()
            } else {
                Toast.makeText(this, "Error updating transaction", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// RecyclerView Adapter
class TransactionAdapter(
    private val transactions: List<PaymentTransaction>,
    private val categoryManager: CategoryManager,  // ADD THIS PARAMETER
    private val onAction: (PaymentTransaction, String) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val amountText: TextView = view.findViewById(R.id.amountText)
        val recipientText: TextView = view.findViewById(R.id.recipientText)
        val categoryText: TextView = view.findViewById(R.id.categoryText)  // ADD THIS
        val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        val noteText: TextView = view.findViewById(R.id.noteText)
        val bankText: TextView = view.findViewById(R.id.bankText)
        val editButton: ImageButton = view.findViewById(R.id.editButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        holder.amountText.text = transaction.amount
        holder.recipientText.text = "To: ${transaction.recipient}"
        holder.categoryText.text = categoryManager.getCategoryDisplayName(transaction.category)  // ADD THIS
        holder.dateTimeText.text = transaction.dateTime

        if (transaction.note.isNotEmpty()) {
            holder.noteText.text = "📝 ${transaction.note}"
            holder.noteText.visibility = View.VISIBLE
        } else {
            holder.noteText.visibility = View.GONE
        }

        if (transaction.bankInfo.isNotEmpty()) {
            holder.bankText.text = "🏦 ${transaction.bankInfo}"
            holder.bankText.visibility = View.VISIBLE
        } else {
            holder.bankText.visibility = View.GONE
        }

        holder.editButton.setOnClickListener {
            onAction(transaction, "edit")
        }

        holder.deleteButton.setOnClickListener {
            onAction(transaction, "delete")
        }
    }

    override fun getItemCount() = transactions.size
}