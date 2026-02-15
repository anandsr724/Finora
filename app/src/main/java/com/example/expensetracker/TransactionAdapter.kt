package com.example.expensetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TransactionAdapter(
    private val transactions: List<PaymentTransaction>,
    private val categoryManager: CategoryManager,
    private val onAction: (PaymentTransaction, String) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val amountText: TextView = view.findViewById(R.id.amountText)
        val recipientText: TextView = view.findViewById(R.id.recipientText)
        val categoryText: TextView = view.findViewById(R.id.categoryText)
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
        holder.categoryText.text = categoryManager.getCategoryDisplayName(transaction.category)
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
