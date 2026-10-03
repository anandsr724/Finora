package com.example.expensetracker

import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.data.SplitBreakdownCodec
import com.example.expensetracker.data.categoryAmountBreakdown
import com.example.expensetracker.data.isSplit
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
        val categoryBadgeContainer: LinearLayout = itemView.findViewById(R.id.category_badge_container)
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

        val isSplitRow = tx.isSplit()
        val iconCategoryId = if (isSplitRow) "cat_other" else tx.category
        val categoryColor = ContextCompat.getColor(holder.itemView.context, CategoryIconHelper.getIconTintColorRes(iconCategoryId))

        if (showCategoryBadge) {
            holder.categoryBadgeContainer.visibility = View.VISIBLE
            val ctx = holder.itemView.context
            if (isSplitRow) {
                val pills = tx.categoryAmountBreakdown().map { (catId, _) ->
                    val name = categoryManager?.getCategoryDisplayName(catId) ?: catId
                    val color = ContextCompat.getColor(ctx, CategoryIconHelper.getIconTintColorRes(catId))
                    name to color
                }
                layoutCategoryBadges(holder.categoryBadgeContainer, pills)
            } else {
                val name = categoryManager?.getCategoryDisplayName(tx.category) ?: tx.category
                layoutCategoryBadges(holder.categoryBadgeContainer, listOf(name to categoryColor))
            }
            holder.dateTextView.text = formattedDate
            holder.bankTextView.text = tx.bankInfo.ifEmpty { "N/A" }
        } else {
            holder.categoryBadgeContainer.visibility = View.GONE
            val categoryDisplayName = if (isSplitRow) "Split · ${SplitBreakdownCodec.decode(tx.splitBreakdown).size} categories"
                else categoryManager?.getCategoryDisplayName(tx.category) ?: tx.category
            holder.dateTextView.text = categoryDisplayName
            holder.bankTextView.text = formattedDate
        }

        // Category icon — colored circular badge tinted to the category's semantic color
        holder.categoryIcon.setImageResource(CategoryIconHelper.getIconResId(iconCategoryId))
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

    /**
     * Fills [container] with one pill per (name, color) in [items]. For a single item this is
     * just today's single category badge. For several (a split transaction), all pills are
     * added first, then — once the row has actually been laid out, so real measured widths are
     * known — trimmed to however many fit the container's width, replacing the rest with a
     * single "+N" overflow pill. A tag-based token guards against a stale trim running after the
     * holder has been recycled and rebound to different data.
     */
    private fun layoutCategoryBadges(container: LinearLayout, items: List<Pair<String, Int>>) {
        container.removeAllViews()
        val ctx = container.context

        if (items.size <= 1) {
            items.forEach { (name, color) -> container.addView(makeCategoryPill(ctx, name, color)) }
            container.tag = null
            return
        }

        val token = Any()
        container.tag = token
        val pills = items.map { (name, color) -> makeCategoryPill(ctx, name, color) }
        pills.forEach { container.addView(it) }

        container.post {
            if (container.tag !== token) return@post // a newer bind has since taken over this row
            val available = container.width
            if (available <= 0) return@post

            fun pillSpan(p: TextView) = p.width + (p.layoutParams as LinearLayout.LayoutParams).marginEnd

            var fitCount = 0
            var used = 0
            for (p in pills) {
                used += pillSpan(p)
                if (used > available) break
                fitCount++
            }
            if (fitCount >= items.size) return@post // everything fit as-is, nothing to trim

            var visibleCount = fitCount.coerceAtLeast(1)
            while (visibleCount > 1) {
                val overflow = makeOverflowPill(ctx, items.size - visibleCount)
                overflow.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val chipSpan = overflow.measuredWidth + (overflow.layoutParams as LinearLayout.LayoutParams).marginEnd
                val visibleSpan = (0 until visibleCount).sumOf { pillSpan(pills[it]) }
                if (visibleSpan + chipSpan <= available) break
                visibleCount--
            }

            container.removeAllViews()
            for (i in 0 until visibleCount) container.addView(pills[i])
            container.addView(makeOverflowPill(ctx, items.size - visibleCount))
        }
    }

    private fun makeCategoryPill(ctx: android.content.Context, text: String, color: Int): TextView {
        val density = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(color)
            background = ContextCompat.getDrawable(ctx, R.drawable.category_badge_background)
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * density).toInt() }
        }
    }

    private fun makeOverflowPill(ctx: android.content.Context, count: Int): TextView =
        makeCategoryPill(ctx, "+$count", ctx.themeColor(R.attr.colorOnSurfaceMuted))
}
