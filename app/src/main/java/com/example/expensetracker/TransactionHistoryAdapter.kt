package com.example.expensetracker

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.example.expensetracker.CurrencyManager

class TransactionHistoryAdapter(
    private val transactions: List<PaymentTransaction>,
    private val categoryManager: CategoryManager? = null,
    private val onClick: ((PaymentTransaction) -> Unit)? = null
) : RecyclerView.Adapter<TransactionHistoryAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val amountTextView: TextView = itemView.findViewById(R.id.transaction_amount)
        val recipientTextView: TextView = itemView.findViewById(R.id.transaction_recipient)
        val dateTextView: TextView = itemView.findViewById(R.id.transaction_time)
        val bankTextView: TextView = itemView.findViewById(R.id.transaction_bank)
        val categoryBadge: TextView = itemView.findViewById(R.id.category_badge)
        val categoryIcon: ImageView = itemView.findViewById(R.id.category_icon)
        val categoryIconContainer: FrameLayout = itemView.findViewById(R.id.category_icon_container)
        val noteTextView: TextView = itemView.findViewById(R.id.transaction_note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction_history, parent, false)
        return TransactionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val tx = transactions[position]

        // Amount
        val numberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))
        val numericAmount = CurrencyManager.parseAmount(tx.amount)
        val currencySymbol = CurrencyManager.getSymbol(tx.currency)
        val isIncome = tx.type == "income"
        holder.amountTextView.text = "${if (isIncome) "+" else "-"}$currencySymbol${numberFormat.format(numericAmount)}"
        // Both Home and History reference screens only color income (emerald, "+"); expense
        // rows stay plain on-surface text with a "-" sign, not coral.
        holder.amountTextView.setTextColor(
            ContextCompat.getColor(holder.itemView.context, if (isIncome) R.color.color_income else R.color.color_on_surface)
        )

        // Recipient
        holder.recipientTextView.text = tx.recipient

        // Date
        try {
            val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(tx.dateTime)
            holder.dateTextView.text = if (date != null)
                SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(date)
            else tx.dateTime
        } catch (e: Exception) {
            holder.dateTextView.text = tx.dateTime
        }

        // Bank info
        holder.bankTextView.text = tx.bankInfo.ifEmpty { "N/A" }

        // Category badge
        holder.categoryBadge.text = categoryManager?.getCategoryDisplayName(tx.category) ?: tx.category
        val categoryColor = ContextCompat.getColor(holder.itemView.context, CategoryIconHelper.getIconTintColorRes(tx.category))
        holder.categoryBadge.setTextColor(categoryColor)

        // Category icon — colored circular badge tinted to the category's semantic color
        holder.categoryIcon.setImageResource(CategoryIconHelper.getIconResId(tx.category))
        holder.categoryIcon.setColorFilter(categoryColor)
        (holder.categoryIconContainer.background as? GradientDrawable)?.setColor(withAlpha(categoryColor, 0x26))

        // Note
        if (tx.note.isNotEmpty()) {
            holder.noteTextView.text = tx.note
            holder.noteTextView.visibility = View.VISIBLE
        } else {
            holder.noteTextView.visibility = View.GONE
        }

        // Click listener on entire card
        holder.itemView.setOnClickListener { onClick?.invoke(tx) }
    }

    override fun getItemCount() = transactions.size

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)
}
