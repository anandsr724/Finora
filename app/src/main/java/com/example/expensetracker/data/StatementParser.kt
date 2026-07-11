package com.example.expensetracker.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

interface StatementParser {
    fun parse(context: Context, uri: Uri): List<ParsedTransaction>
}

enum class StatementType { SBI_XLSX, SBI_PDF, GPAY_PDF, UNKNOWN }

object StatementFormat {

    private const val TAG = "StatementFormat"

    fun detect(context: Context, uri: Uri): StatementType {
        val name = uri.lastPathSegment?.lowercase() ?: ""
        return when {
            name.endsWith(".xlsx") || name.contains("xlsx") -> StatementType.SBI_XLSX
            name.endsWith(".pdf") || name.contains("pdf")   -> detectPdfType(context, uri)
            else -> {
                // Fall back to MIME type check
                val mime = context.contentResolver.getType(uri) ?: ""
                when {
                    mime.contains("spreadsheet") || mime.contains("excel") -> StatementType.SBI_XLSX
                    mime.contains("pdf") -> detectPdfType(context, uri)
                    else -> StatementType.UNKNOWN
                }
            }
        }
    }

    private fun detectPdfType(context: Context, uri: Uri): StatementType {
        return try {
            PDFBoxResourceLoader.init(context)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val doc = PDDocument.load(stream)
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = minOf(2, doc.numberOfPages)
                }
                val text = stripper.getText(doc).lowercase()
                doc.close()
                when {
                    text.contains("google pay") || text.contains("paid to") ||
                    text.contains("received from") -> StatementType.GPAY_PDF
                    text.contains("state bank") || text.contains("sbi") ||
                    text.contains("account statement") -> StatementType.SBI_PDF
                    else -> StatementType.UNKNOWN
                }
            } ?: StatementType.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "PDF type detection failed", e)
            StatementType.UNKNOWN
        }
    }
}
