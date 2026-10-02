package com.example.expensetracker.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import com.example.expensetracker.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * Shown when the user hits Import on a statement-import review screen with duplicate-flagged
 * transactions selected that were never opened individually (e.g. via "Select All"). Rather than
 * silently treating every one of those as "keep both", offers one bulk decision for all of them
 * at once, or a way back to the review list to resolve each one individually.
 *
 * [onResolution] receives the chosen bulk [DuplicateResolution] to apply to every unresolved
 * duplicate, or null if the user chose to review individually (or dismissed the sheet) — in
 * which case nothing should change and the import should not proceed.
 */
fun showBulkDuplicateResolutionSheet(
    context: Context,
    inflater: LayoutInflater,
    count: Int,
    onResolution: (DuplicateResolution?) -> Unit
) {
    val sheet = BottomSheetDialog(context)
    val sheetView = inflater.inflate(R.layout.layout_bulk_duplicate_resolution_sheet, null)
    sheet.setContentView(sheetView)

    val plural = count != 1
    sheetView.findViewById<TextView>(R.id.bulkDuplicateBodyText).text =
        "$count of the transactions you selected " +
            (if (plural) "look" else "looks") + " like " +
            (if (plural) "duplicates" else "a duplicate") + " you haven't reviewed " +
            "individually. Choose what to do with " + (if (plural) "all of them" else "it") +
            ", or go back and review " + (if (plural) "each one" else "it") + "."

    var resolved = false
    fun resolve(resolution: DuplicateResolution?) {
        if (resolved) return
        resolved = true
        sheet.dismiss()
        onResolution(resolution)
    }

    sheet.setOnDismissListener { resolve(null) }
    sheetView.findViewById<ImageButton>(R.id.closeBulkDuplicateSheetButton).setOnClickListener { resolve(null) }
    sheetView.findViewById<MaterialButton>(R.id.bulkReviewIndividuallyButton).setOnClickListener { resolve(null) }
    sheetView.findViewById<MaterialButton>(R.id.bulkKeepBothButton).setOnClickListener { resolve(DuplicateResolution.KEEP_BOTH) }
    sheetView.findViewById<MaterialButton>(R.id.bulkReplaceExistingButton).setOnClickListener { resolve(DuplicateResolution.REPLACE_EXISTING) }
    sheetView.findViewById<MaterialButton>(R.id.bulkDiscardNewButton).setOnClickListener { resolve(DuplicateResolution.KEEP_EXISTING) }

    sheet.applyGlassBlur()
    sheet.show()
}
