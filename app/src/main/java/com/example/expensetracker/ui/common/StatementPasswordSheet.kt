package com.example.expensetracker.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.expensetracker.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * Shown when a statement file turns out to be password-protected (an
 * `InvalidPasswordException` from PDFBox while detecting the format or parsing it). Offers the
 * two ways out: enter the password to retry, or go back to the file picker and choose a
 * different file instead.
 *
 * [isIncorrectRetry] is true when this is being shown again after a previously-entered password
 * still failed — the message switches to say so, rather than repeating the generic prompt as if
 * nothing had been tried yet.
 *
 * Follows the same free-function + BottomSheetDialog + `resolved` guard convention as
 * DuplicateResolutionSheet/BulkDuplicateResolutionSheet/SplitTransactionSheet.
 */
fun showStatementPasswordSheet(
    context: Context,
    inflater: LayoutInflater,
    fileName: String,
    isIncorrectRetry: Boolean,
    onSubmit: (password: String) -> Unit,
    onPickDifferentFile: () -> Unit
) {
    val sheet = BottomSheetDialog(context)
    val sheetView = inflater.inflate(R.layout.layout_statement_password_sheet, null)
    sheet.setContentView(sheetView)

    val messageView = sheetView.findViewById<TextView>(R.id.passwordSheetMessage)
    val passwordInput = sheetView.findViewById<EditText>(R.id.statementPasswordInput)
    val unlockButton = sheetView.findViewById<MaterialButton>(R.id.unlockStatementButton)
    val pickDifferentButton = sheetView.findViewById<MaterialButton>(R.id.pickDifferentFileButton)
    val closeButton = sheetView.findViewById<ImageButton>(R.id.closePasswordSheetButton)

    messageView.text = if (isIncorrectRetry) {
        "That password didn't work. Try again, or choose a different file."
    } else {
        "“$fileName” is password-protected. Enter the password to continue, or choose a different file."
    }
    if (isIncorrectRetry) {
        messageView.setTextColor(ContextCompat.getColor(context, R.color.color_expense))
    }

    unlockButton.isEnabled = false
    passwordInput.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            unlockButton.isEnabled = !s.isNullOrBlank()
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })

    var resolved = false
    fun close() {
        if (resolved) return
        resolved = true
        sheet.dismiss()
    }

    closeButton.setOnClickListener { close() }

    unlockButton.setOnClickListener {
        val password = passwordInput.text.toString()
        if (password.isBlank()) return@setOnClickListener
        close()
        onSubmit(password)
    }

    pickDifferentButton.setOnClickListener {
        close()
        onPickDifferentFile()
    }

    sheet.applyGlassBlur()
    sheet.show()
}
