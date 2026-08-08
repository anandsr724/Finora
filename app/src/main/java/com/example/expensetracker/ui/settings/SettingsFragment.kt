package com.example.expensetracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.CSVManager
import com.example.expensetracker.Category
import com.example.expensetracker.CategoryIconHelper
import com.example.expensetracker.CategoryManager
import com.example.expensetracker.R
import com.example.expensetracker.ui.common.applyGlassBlur
import com.example.expensetracker.ui.common.applyVividGlow
import com.example.expensetracker.ui.common.themeColor
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var csvManager: CSVManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var manageCategoriesButton: MaterialButton
    private lateinit var categoryPreviewContainer: LinearLayout
    private lateinit var saveScreenshotsSwitch: SwitchMaterial
    private lateinit var feedbackSwitch: SwitchMaterial

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1002
        private const val IMPORT_CSV_REQUEST = 1005
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        csvManager = CSVManager(requireContext())
        categoryManager = CategoryManager(requireContext())
        categoryManager.initializeDefaultCategories()

        setupViews(view)
        loadCategoryPreview()

        return view
    }

    private fun setupViews(view: View) {
        manageCategoriesButton = view.findViewById(R.id.manageCategoriesButton)
        categoryPreviewContainer = view.findViewById(R.id.categoryPreviewContainer)
        saveScreenshotsSwitch = view.findViewById(R.id.saveScreenshotsSwitch)
        feedbackSwitch = view.findViewById(R.id.feedbackSwitch)

        manageCategoriesButton.setOnClickListener {
            showManageCategoriesSheet()
        }

        // A colored elevation-shadow glow reads fine on the small category icon badges, but on
        // this larger rectangular tile Android's hard-edged shadow rendering looked like a
        // jarring solid box rather than a soft halo — so this button relies on its tinted
        // background/border alone, matching Import's weight instead.
        view.findViewById<View>(R.id.export_csv_button).setOnClickListener {
            exportCsvToDownloads()
        }

        view.findViewById<View>(R.id.import_csv_button).setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "text/csv"
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(android.content.Intent.createChooser(intent, "Select CSV file"), IMPORT_CSV_REQUEST)
        }

        view.findViewById<View>(R.id.clear_all_data_button).setOnClickListener {
            confirmClearAllData()
        }

        view.findViewById<LinearLayout>(R.id.uploadStatementRow).setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.example.expensetracker.StatementImportActivity::class.java))
        }

        setupCurrencySetting(view)
        setupAppearanceSetting(view)
        setupDevTrackingSwitch()
        setupFeedbackSwitch()
    }

    private fun setupAppearanceSetting(view: View) {
        val appearanceRow = view.findViewById<LinearLayout>(R.id.appearanceRow)
        val appearanceValue = view.findViewById<TextView>(R.id.appearanceValue)

        appearanceValue.text = com.example.expensetracker.ThemeManager.getSelected(requireContext()).label

        appearanceRow.setOnClickListener {
            showThemePickerSheet()
        }
    }

    private fun showThemePickerSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_theme_picker_sheet, null)
        sheet.setContentView(sheetView)

        var selectedTheme = com.example.expensetracker.ThemeManager.getSelected(requireContext())

        val luminousRow = sheetView.findViewById<LinearLayout>(R.id.themeOptionLuminous)
        val onyxRow = sheetView.findViewById<LinearLayout>(R.id.themeOptionOnyx)
        val luminousCheck = sheetView.findViewById<ImageView>(R.id.themeLuminousCheckIcon)
        val luminousRing = sheetView.findViewById<View>(R.id.themeLuminousEmptyRing)
        val onyxCheck = sheetView.findViewById<ImageView>(R.id.themeOnyxCheckIcon)
        val onyxRing = sheetView.findViewById<View>(R.id.themeOnyxEmptyRing)

        fun refreshSelection() {
            val isLuminous = selectedTheme == com.example.expensetracker.ThemeManager.AppTheme.LUMINOUS
            luminousCheck.visibility = if (isLuminous) View.VISIBLE else View.GONE
            luminousRing.visibility = if (isLuminous) View.GONE else View.VISIBLE
            onyxCheck.visibility = if (!isLuminous) View.VISIBLE else View.GONE
            onyxRing.visibility = if (!isLuminous) View.GONE else View.VISIBLE
        }
        refreshSelection()

        luminousRow.setOnClickListener {
            selectedTheme = com.example.expensetracker.ThemeManager.AppTheme.LUMINOUS
            refreshSelection()
        }
        onyxRow.setOnClickListener {
            selectedTheme = com.example.expensetracker.ThemeManager.AppTheme.ONYX
            refreshSelection()
        }

        sheetView.findViewById<ImageButton>(R.id.closeThemeSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<MaterialButton>(R.id.applyThemeButton).setOnClickListener {
            val changed = selectedTheme != com.example.expensetracker.ThemeManager.getSelected(requireContext())
            com.example.expensetracker.ThemeManager.setSelected(requireContext(), selectedTheme)
            sheet.dismiss()
            // A theme swap changes attrs bound at Activity.setTheme() time — every already-
            // inflated view (including this Fragment's own) needs to be re-created against the
            // new theme, which only a full Activity recreate() achieves.
            if (changed) requireActivity().recreate()
        }

        sheet.show()
        sheet.applyGlassBlur()
    }

    private fun setupCurrencySetting(view: View) {
        val currencyRow = view.findViewById<LinearLayout>(R.id.defaultCurrencyRow)
        val currencyValue = view.findViewById<TextView>(R.id.defaultCurrencyValue)

        fun refreshDisplay() {
            val code = com.example.expensetracker.CurrencyManager.getDefault(requireContext())
            val info = com.example.expensetracker.CurrencyManager.getInfo(code)
            currencyValue.text = "$code ${info?.symbol ?: ""}"
        }
        refreshDisplay()

        currencyRow.setOnClickListener {
            showCurrencyPickerSheet { refreshDisplay() }
        }
    }

    private fun showCurrencyPickerSheet(onApplied: () -> Unit) {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_currency_picker_sheet, null)
        sheet.setContentView(sheetView)
        // The currency list is tall enough that Material's default half-expanded collapse
        // hides the Apply button below the fold, and swipes just scroll the inner RecyclerView
        // instead of expanding the sheet — force full expansion so Apply is always reachable.
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true

        val allCurrencies = com.example.expensetracker.CurrencyManager.CURRENCIES
        var selectedCode = com.example.expensetracker.CurrencyManager.getDefault(requireContext())

        val recycler = sheetView.findViewById<RecyclerView>(R.id.currencyPickerRecyclerView)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = CurrencyPickerAdapter(allCurrencies, selectedCode) { code ->
            selectedCode = code
        }
        recycler.adapter = adapter

        sheetView.findViewById<EditText>(R.id.currencySearchEditText).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString().orEmpty()
                adapter.filter(query)
            }
        })

        sheetView.findViewById<ImageButton>(R.id.closeCurrencySheetButton).setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<MaterialButton>(R.id.applyCurrencyButton).setOnClickListener {
            com.example.expensetracker.CurrencyManager.setDefault(requireContext(), selectedCode)
            onApplied()
            sheet.dismiss()
        }

        sheet.show()
        sheet.applyGlassBlur()
    }

    private fun loadCategoryPreview() {
        val categories = categoryManager.getAllCategories()
        categoryPreviewContainer.removeAllViews()
        // Preview grid shows the first 3 (matching the Stitch reference's 3-column preview);
        // "Manage" opens the full, scrollable list.
        val preview = categories.take(3)
        preview.forEachIndexed { index, category ->
            val cell = layoutInflater.inflate(R.layout.item_settings_category_preview, categoryPreviewContainer, false)
            val tint = ContextCompat.getColor(requireContext(), CategoryIconHelper.getIconTintColorRes(category.id))
            // Icon must contrast against its own badge fill, not match it — using the same
            // `tint` for both (as before) made the icon glyph disappear into its background.
            val previewIcon = cell.findViewById<ImageView>(R.id.previewIcon).apply {
                setImageResource(CategoryIconHelper.getIconResId(category))
                setColorFilter(Color.WHITE)
            }
            cell.findViewById<View>(R.id.previewIconBg).apply {
                (background as? GradientDrawable)?.setColor(withAlpha(tint, 0xFF))
                applyVividGlow(tint, cornerRadiusDp = 12f)
                // Elevation affects draw order between siblings regardless of XML declaration
                // order — giving this background View elevation (for the glow shadow) made it
                // draw on top of the icon ImageView, hiding the glyph entirely. Bump the icon's
                // own elevation just above it so it stays on top.
                previewIcon.elevation = elevation + 1f
            }
            cell.findViewById<android.widget.TextView>(R.id.previewName).text = category.name
            if (index < preview.size - 1) {
                (cell.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            categoryPreviewContainer.addView(cell)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun showManageCategoriesSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_manage_categories_sheet, null)
        sheet.setContentView(sheetView)

        val recycler = sheetView.findViewById<RecyclerView>(R.id.manageCategoriesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        fun refreshSheet() {
            val cats = categoryManager.getAllCategories()
            recycler.adapter = ManageCategoryAdapter(
                cats,
                onDelete = { category ->
                    confirmDeleteCategory(category) {
                        loadCategoryPreview()
                        refreshSheet()
                    }
                },
                onEdit = { category ->
                    sheet.dismiss()
                    showEditCategoryDialog(category) {
                        loadCategoryPreview()
                        showManageCategoriesSheet()
                    }
                }
            )
        }
        refreshSheet()

        sheetView.findViewById<ImageButton>(R.id.closeCategoriesSheetButton).setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<MaterialButton>(R.id.addNewCategoryButton).setOnClickListener {
            sheet.dismiss()
            showAddNewCategoryDialog {
                loadCategoryPreview()
            }
        }

        sheetView.findViewById<MaterialButton>(R.id.restoreDefaultsButton).setOnClickListener {
            val restored = categoryManager.restoreDefaultCategories()
            val msg = if (restored > 0) "Restored $restored default categories" else "All defaults already present"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            loadCategoryPreview()
            refreshSheet()
        }

        sheet.applyGlassBlur()
        sheet.show()
    }

    private fun showAddNewCategoryDialog(onAdded: () -> Unit) {
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = sheetView.findViewById<EditText>(R.id.categoryNameInput)
        val iconPickerGrid = sheetView.findViewById<GridLayout>(R.id.iconPickerGrid)

        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<ImageButton>(R.id.closeButton).setOnClickListener { sheet.dismiss() }
        sheetView.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener { sheet.dismiss() }

        val iconOptions = CategoryIconHelper.allIconKeys.map { key -> key to CategoryIconHelper.getIconResId(key) }
        var selectedIconKey = iconOptions[0].first
        val density = resources.displayMetrics.density
        val cellSize = (48 * density).toInt()
        val cellMargin = (4 * density).toInt()
        val iconPadding = (12 * density).toInt()
        val iconViews = mutableListOf<Pair<FrameLayout, ImageView>>()

        // Each swatch is filled with its own category tint color (CategoryIconHelper); a ring
        // border indicates the current selection instead of swapping fill color.
        fun makeBackground(key: String, selected: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(requireContext(), CategoryIconHelper.getIconTintColorRes(key)))
            if (selected) {
                setStroke((2.5f * density).toInt(), requireContext().themeColor(R.attr.colorOnSurface))
            }
        }
        fun updateSelection(selectedIdx: Int) {
            iconViews.forEachIndexed { idx, (frame, img) ->
                val key = iconOptions[idx].first
                frame.background = makeBackground(key, idx == selectedIdx)
                img.setColorFilter(Color.WHITE)
            }
        }

        iconOptions.forEachIndexed { idx, (key, resId) ->
            val frame = FrameLayout(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED)
                ).apply {
                    width = cellSize; height = cellSize
                    setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                }
                background = makeBackground(key, false)
            }
            val img = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageResource(resId)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                setColorFilter(Color.WHITE)
            }
            frame.addView(img)
            frame.setOnClickListener { selectedIconKey = key; updateSelection(idx) }
            iconViews.add(frame to img)
            iconPickerGrid.addView(frame)
        }
        updateSelection(0)

        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).setOnClickListener {
            val name = categoryNameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val success = categoryManager.addCategory(name, selectedIconKey)
                if (success) {
                    Toast.makeText(requireContext(), "Category added!", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    onAdded()
                } else {
                    Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.applyGlassBlur()
        sheet.show()
    }

    private fun showEditCategoryDialog(category: Category, onUpdated: () -> Unit) {
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val categoryNameInput = sheetView.findViewById<EditText>(R.id.categoryNameInput)
        val iconPickerGrid = sheetView.findViewById<GridLayout>(R.id.iconPickerGrid)

        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.sheetTitle).text = "Edit Category"
        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).text = "Save Changes"
        categoryNameInput.setText(category.name)

        sheetView.findViewById<ImageButton>(R.id.closeButton).setOnClickListener { sheet.dismiss() }
        sheetView.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener { sheet.dismiss() }

        val allKeys = CategoryIconHelper.allIconKeys
        val iconOptions = allKeys.map { key -> key to CategoryIconHelper.getIconResId(key) }
        var selectedIconKey = when {
            category.emoji in allKeys -> category.emoji
            category.id in allKeys -> category.id
            else -> "cat_other"
        }
        val density = resources.displayMetrics.density
        val cellSize = (48 * density).toInt()
        val cellMargin = (4 * density).toInt()
        val iconPadding = (12 * density).toInt()
        val iconViews = mutableListOf<Pair<FrameLayout, ImageView>>()

        // Each swatch is filled with its own category tint color (CategoryIconHelper); a ring
        // border indicates the current selection instead of swapping fill color.
        fun makeBackground(key: String, selected: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(requireContext(), CategoryIconHelper.getIconTintColorRes(key)))
            if (selected) {
                setStroke((2.5f * density).toInt(), requireContext().themeColor(R.attr.colorOnSurface))
            }
        }
        fun updateSelection(selectedIdx: Int) {
            iconViews.forEachIndexed { idx, (frame, img) ->
                val key = iconOptions[idx].first
                frame.background = makeBackground(key, idx == selectedIdx)
                img.setColorFilter(Color.WHITE)
            }
        }

        iconOptions.forEachIndexed { idx, (key, resId) ->
            val frame = FrameLayout(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED)
                ).apply {
                    width = cellSize; height = cellSize
                    setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                }
                background = makeBackground(key, false)
            }
            val img = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageResource(resId)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                setColorFilter(Color.WHITE)
            }
            frame.addView(img)
            frame.setOnClickListener { selectedIconKey = key; updateSelection(idx) }
            iconViews.add(frame to img)
            iconPickerGrid.addView(frame)
        }
        val initialIdx = allKeys.indexOf(selectedIconKey).coerceAtLeast(0)
        updateSelection(initialIdx)

        sheetView.findViewById<MaterialButton>(R.id.addCategoryButton).setOnClickListener {
            val name = categoryNameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val success = categoryManager.updateCategory(category.id, name, selectedIconKey)
                if (success) {
                    Toast.makeText(requireContext(), "Category updated!", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    onUpdated()
                } else {
                    Toast.makeText(requireContext(), "Error updating category", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.applyGlassBlur()
        sheet.show()
    }

    private fun confirmDeleteCategory(category: Category, onDeleted: () -> Unit) {
        val affectedCount = csvManager.getAllTransactions().count { it.category == category.id }
        val warningLine = if (affectedCount > 0)
            "\n\n$affectedCount transaction${if (affectedCount == 1) "" else "s"} using this category will show as Uncategorized."
        else ""
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete \"${category.name}\"?$warningLine")
            .setPositiveButton("Delete") { _, _ ->
                val success = categoryManager.deleteCategory(category.id)
                if (success) {
                    Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show()
                    onDeleted()
                } else {
                    Toast.makeText(requireContext(), "Error deleting category", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupDevTrackingSwitch() {
        val prefs = requireContext().getSharedPreferences("finora_prefs", Context.MODE_PRIVATE)
        saveScreenshotsSwitch.isChecked = prefs.getBoolean("dev_tracking_enabled", false)
        saveScreenshotsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dev_tracking_enabled", isChecked).apply()
            val msg = if (isChecked) "OCR data capture enabled"
                      else "OCR data capture disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFeedbackSwitch() {
        val prefs = requireContext().getSharedPreferences("finora_prefs", Context.MODE_PRIVATE)
        feedbackSwitch.isChecked = prefs.getBoolean("feedback_enabled", false)
        feedbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("feedback_enabled", isChecked).apply()
            val msg = if (isChecked) "Feedback prompt enabled" else "Feedback prompt disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCsvToDownloads() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
                return
            }
        }

        val exportedFile = csvManager.exportToDownloads()
        if (exportedFile != null) {
            Toast.makeText(requireContext(), "CSV exported to Downloads!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAllData() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Clear All Data")
            .setMessage(
                "This will permanently delete all ${csvManager.getTransactionCount()} saved transactions. This cannot be undone!"
            )
            .setPositiveButton("Delete All") { _, _ ->
                val success = csvManager.clearAllTransactions()
                if (success) {
                    Toast.makeText(requireContext(), "All data cleared", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error clearing data", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportCsvToDownloads()
            } else {
                Toast.makeText(
                    requireContext(), "Storage permission needed for export", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_CSV_REQUEST && resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            val (imported, skipped) = csvManager.importFromCsv(uri)
            val msg = if (imported > 0)
                "Imported $imported transaction${if (imported == 1) "" else "s"}${if (skipped > 0) " ($skipped skipped)" else ""}"
            else
                "No new transactions found"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    private inner class ManageCategoryAdapter(
        private val categories: List<Category>,
        private val onDelete: (Category) -> Unit,
        private val onEdit: (Category) -> Unit
    ) : RecyclerView.Adapter<ManageCategoryAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val emoji: ImageView = itemView.findViewById(R.id.categoryEmoji)
            val name: TextView = itemView.findViewById(R.id.categoryNameText)
            val type: TextView = itemView.findViewById(R.id.categoryTypeText)
            val editButton: ImageButton = itemView.findViewById(R.id.editButton)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.emoji.setImageResource(CategoryIconHelper.getIconResId(category))
            holder.emoji.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, CategoryIconHelper.getIconTintColorRes(category.id))
            )
            holder.name.text = category.name
            holder.type.text = "DEFAULT"
            holder.type.visibility = if (category.isPredefined) View.VISIBLE else View.GONE
            holder.deleteButton.visibility = if (category.isPredefined) View.GONE else View.VISIBLE
            holder.editButton.setOnClickListener { onEdit(category) }
            holder.deleteButton.setOnClickListener { onDelete(category) }
        }

        override fun getItemCount() = categories.size
    }

    private inner class CurrencyPickerAdapter(
        private val allItems: List<com.example.expensetracker.CurrencyManager.CurrencyInfo>,
        initialSelectedCode: String,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<CurrencyPickerAdapter.ViewHolder>() {

        private var selectedCode = initialSelectedCode
        private var items = allItems

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatarBg: View = itemView.findViewById(R.id.currencyAvatarBg)
            val symbol: TextView = itemView.findViewById(R.id.currencySymbol)
            val name: TextView = itemView.findViewById(R.id.currencyName)
            val code: TextView = itemView.findViewById(R.id.currencyCode)
            val checkIcon: ImageView = itemView.findViewById(R.id.currencyCheckIcon)
            val emptyRing: View = itemView.findViewById(R.id.currencyEmptyRing)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_currency_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val currency = items[position]
            val isSelected = currency.code == selectedCode

            holder.itemView.background = ContextCompat.getDrawable(
                holder.itemView.context,
                if (isSelected) R.drawable.currency_item_selected_background else R.drawable.currency_item_unselected_background
            )
            holder.avatarBg.background = ContextCompat.getDrawable(
                holder.itemView.context,
                if (isSelected) R.drawable.currency_avatar_selected_background else R.drawable.currency_avatar_unselected_background
            )
            holder.symbol.text = currency.symbol
            holder.symbol.setTextColor(
                if (isSelected) ContextCompat.getColor(holder.itemView.context, R.color.color_primary)
                else holder.itemView.context.themeColor(R.attr.colorOnSurface)
            )
            holder.name.text = currency.name
            holder.code.text = currency.code
            holder.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.emptyRing.visibility = if (isSelected) View.GONE else View.VISIBLE

            holder.itemView.setOnClickListener {
                selectedCode = currency.code
                onSelect(currency.code)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = items.size

        fun filter(query: String) {
            items = if (query.isBlank()) {
                allItems
            } else {
                allItems.filter {
                    it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
                }
            }
            notifyDataSetChanged()
        }
    }
}
