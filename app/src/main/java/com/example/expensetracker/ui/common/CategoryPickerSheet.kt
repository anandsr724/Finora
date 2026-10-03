package com.example.expensetracker.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.Category
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Single-select category grid picker (search + 3-column grid), shared by EditPaymentActivity's
 * own category selector and the split-transaction editor's per-line category chip. Extracted
 * from what used to be EditPaymentActivity's private `CategoryPickerAdapter`/`showCategoryPicker()`.
 *
 * [onCreateNewCategory] is optional — pass null to hide the "Create new category" affordance
 * (e.g. from the split editor, which doesn't offer category creation inline).
 */
fun showCategoryPickerSheet(
    context: Context,
    inflater: LayoutInflater,
    categories: List<Category>,
    selectedCategoryId: String,
    onCreateNewCategory: (() -> Unit)?,
    onSelect: (Category) -> Unit
) {
    val sheet = BottomSheetDialog(context)
    val sheetView = inflater.inflate(R.layout.layout_category_picker_sheet, null)
    sheet.setContentView(sheetView)

    val recycler = sheetView.findViewById<RecyclerView>(R.id.categoryPickerRecyclerView)
    recycler.layoutManager = GridLayoutManager(context, 3)
    val adapter = CategoryPickerAdapter(categories, selectedCategoryId) { category ->
        sheet.dismiss()
        onSelect(category)
    }
    recycler.adapter = adapter

    sheetView.findViewById<EditText>(R.id.categoryPickerSearchInput).addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, before: Int, count: Int, after: Int) {
            adapter.filter(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })

    sheetView.findViewById<ImageButton>(R.id.closePickerButton).setOnClickListener {
        sheet.dismiss()
    }

    val createButton = sheetView.findViewById<MaterialButton>(R.id.createCategoryButton)
    if (onCreateNewCategory != null) {
        createButton.visibility = View.VISIBLE
        createButton.setOnClickListener {
            sheet.dismiss()
            onCreateNewCategory()
        }
    } else {
        createButton.visibility = View.GONE
    }

    sheet.applyGlassBlur()
    sheet.show()
}

private class CategoryPickerAdapter(
    private val allCategories: List<Category>,
    private val selectedId: String,
    private val onSelect: (Category) -> Unit
) : RecyclerView.Adapter<CategoryPickerAdapter.ViewHolder>() {

    private var categories: List<Category> = allCategories

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.categoryPickerCard)
        val iconCard: MaterialCardView = itemView.findViewById(R.id.categoryIconCard)
        val icon: ImageView = itemView.findViewById(R.id.categoryPickerIcon)
        val name: TextView = itemView.findViewById(R.id.categoryPickerName)
    }

    fun filter(query: String) {
        categories = if (query.isBlank()) allCategories
            else allCategories.filter { it.name.contains(query, ignoreCase = true) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        val isSelected = category.id == selectedId
        val ctx = holder.itemView.context
        val tint = ContextCompat.getColor(ctx, CategoryIconHelper.getIconTintColorRes(category.id))

        holder.icon.setImageResource(CategoryIconHelper.getIconResId(category.id))
        holder.name.text = category.name

        if (isSelected) {
            holder.card.strokeColor = tint
            holder.card.strokeWidth = (2 * ctx.resources.displayMetrics.density).toInt()
            holder.card.setCardBackgroundColor(ctx.themeColor(R.attr.colorGlassFillL3))
            holder.iconCard.setCardBackgroundColor(tint)
            holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.color_on_primary)
            )
            holder.name.setTextColor(ctx.themeColor(R.attr.colorOnSurface))
        } else {
            holder.card.strokeColor = ctx.themeColor(R.attr.colorGlassBorder)
            holder.card.strokeWidth = (1 * ctx.resources.displayMetrics.density).toInt()
            holder.card.setCardBackgroundColor(ctx.themeColor(R.attr.colorGlassFillL2))
            holder.iconCard.setCardBackgroundColor(withAlpha(tint, 0x26))
            holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
            holder.name.setTextColor(ctx.themeColor(R.attr.colorOnSurface))
        }

        holder.card.setOnClickListener { onSelect(category) }
    }

    override fun getItemCount() = categories.size

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)
}
