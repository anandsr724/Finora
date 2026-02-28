package com.example.expensetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CategoryManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionHistoryAdapter(
    private val transactions: List<PaymentTransaction>,
    private val categoryManager: CategoryManager? = null,
    private val onEdit: ((PaymentTransaction) -> Unit)? = null,
    private val onDelete: ((PaymentTransaction) -> Unit)? = null
) : RecyclerView.Adapter<TransactionHistoryAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val amountTextView: TextView = itemView.findViewById(R.id.transaction_amount)
        val recipientTextView: TextView = itemView.findViewById(R.id.transaction_recipient)
        val dateTextView: TextView = itemView.findViewById(R.id.transaction_time)
        val bankTextView: TextView = itemView.findViewById(R.id.transaction_bank)
        val categoryBadge: TextView = itemView.findViewById(R.id.category_badge)
        val categoryEmoji: TextView = itemView.findViewById(R.id.category_emoji)
        val noteTextView: TextView = itemView.findViewById(R.id.transaction_note)
        val editButton: android.widget.Button? = itemView.findViewById(R.id.editButton)
        val deleteButton: android.widget.Button? = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction_history, parent, false)
        return TransactionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val currentTransaction = transactions[position]
        
        // Amount - format with currency symbol and Indian number formatting
        val numberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val numericAmount = currentTransaction.amount.replace("₹", "").replace(",", "").toDoubleOrNull()
        val currencySymbol = if (currentTransaction.currency == "INR") "₹" else currentTransaction.currency
        holder.amountTextView.text = if (numericAmount != null) {
            "$currencySymbol${numberFormat.format(numericAmount)}"
        } else {
            currentTransaction.amount
        }
        
        // Recipient
        holder.recipientTextView.text = currentTransaction.recipient
        
        // Date and time
        try {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            val date = dateFormat.parse(currentTransaction.dateTime)
            if (date != null) {
                val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
                holder.dateTextView.text = timeFormat.format(date)
            } else {
                holder.dateTextView.text = currentTransaction.dateTime
            }
        } catch (e: Exception) {
            holder.dateTextView.text = currentTransaction.dateTime
        }
        
        // Bank info
        holder.bankTextView.text = currentTransaction.bankInfo.ifEmpty { "N/A" }
        
        // Category
        val categoryDisplayName = categoryManager?.getCategoryDisplayName(currentTransaction.category) 
            ?: currentTransaction.category
        holder.categoryBadge.text = categoryDisplayName
        
        // Category emoji
        val categoryEmoji = categoryManager?.getCategoryEmoji(currentTransaction.category) ?: "📁"
        holder.categoryEmoji.text = categoryEmoji
        
        // Note
        if (currentTransaction.note.isNotEmpty()) {
            holder.noteTextView.text = currentTransaction.note
            holder.noteTextView.visibility = View.VISIBLE
        } else {
            holder.noteTextView.visibility = View.GONE
        }
        
        // Edit button click listener
        holder.editButton?.setOnClickListener {
            onEdit?.invoke(currentTransaction)
        }
        
        // Delete button click listener
        holder.deleteButton?.setOnClickListener {
            onDelete?.invoke(currentTransaction)
        }
    }

    override fun getItemCount() = transactions.size
}