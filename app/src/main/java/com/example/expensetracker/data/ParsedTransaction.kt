package com.example.expensetracker.data

data class ParsedTransaction(
    val date: String,           // "dd MMM yyyy, hh:mm a" — app display format
    val recipient: String,
    val note: String,
    val amount: String,         // plain decimal, no ₹, no commas
    val transactionId: String,
    val bankInfo: String,
    val type: String,           // "expense" or "income"
    val category: String = "cat_other"
)
