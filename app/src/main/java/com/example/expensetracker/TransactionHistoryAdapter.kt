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
import com.example.expensetracker.ui.common.themeColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.example.expensetracker.CurrencyManager

class TransactionHistoryAdapter(
    private val transactions: List<PaymentTransaction>,
    private val categoryManager: CategoryManager? = null,
    // Home's reference row has no separate category badge — category is folded into the
    // subtitle instead ("{Category} • {Time}"). History's reference keeps the badge plus a
    // "{Date, Time} · {Bank}" subtitle. Same shared row/adapter, so this switches between them.
    private val showCategoryBadge: Boolean = true,
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
            if (isIncome) ContextCompat.getColor(holder.itemView.context, R.color.color_income)
            else holder.itemView.context.themeColor(R.attr.colorOnSurface)
        )

        // Recipient
        holder.recipientTextView.text = tx.recipient

        // Date
        val formattedDate = try {
            val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).parse(tx.dateTime)
            if (date != null) SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(date) else tx.dateTime
        } catch (e: Exception) {
            tx.dateTime
        }

        val categoryDisplayName = categoryManager?.getCategoryDisplayName(tx.category) ?: tx.category
        val categoryColor = ContextCompat.getColor(holder.itemView.context, CategoryIconHelper.getIconTintColorRes(tx.category))

        if (showCategoryBadge) {
            holder.categoryBadge.visibility = View.VISIBLE
            holder.categoryBadge.text = categoryDisplayName
            holder.categoryBadge.setTextColor(categoryColor)
            holder.dateTextView.text = formattedDate
            holder.bankTextView.text = tx.bankInfo.ifEmpty { "N/A" }
        } else {
            holder.categoryBadge.visibility = View.GONE
            holder.dateTextView.text = categoryDisplayName
            holder.bankTextView.text = formattedDate
        }

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
