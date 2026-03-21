package com.example.expensetracker

import android.content.Context

object CurrencyManager {

    private const val PREF_NAME = "currency_prefs"
    private const val KEY_DEFAULT = "default_currency"

    data class CurrencyInfo(
        val code: String,
        val symbol: String,
        val name: String,
        val rateToINR: Double   // 1 unit of this currency = rateToINR INR
    )

    val CURRENCIES = listOf(
        CurrencyInfo("INR", "₹",  "Indian Rupee",         1.0),
        CurrencyInfo("USD", "$",  "US Dollar",            83.5),
        CurrencyInfo("EUR", "€",  "Euro",                 90.2),
        CurrencyInfo("GBP", "£",  "British Pound",       105.8),
        CurrencyInfo("JPY", "¥",  "Japanese Yen",          0.56),
        CurrencyInfo("AED", "د.إ","UAE Dirham",            22.7),
        CurrencyInfo("SGD", "S$", "Singapore Dollar",     62.0),
        CurrencyInfo("CAD", "C$", "Canadian Dollar",      61.5),
        CurrencyInfo("AUD", "A$", "Australian Dollar",    53.8),
        CurrencyInfo("CNY", "CN¥","Chinese Yuan",          11.5),
        CurrencyInfo("CHF", "Fr", "Swiss Franc",           95.0),
        CurrencyInfo("MYR", "RM", "Malaysian Ringgit",     17.8),
        CurrencyInfo("NPR", "रू", "Nepalese Rupee",         0.625)
    )

    fun getDefault(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT, "INR") ?: "INR"

    fun setDefault(context: Context, code: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEFAULT, code).apply()
    }

    fun getSymbol(code: String): String =
        CURRENCIES.find { it.code == code }?.symbol ?: code

    fun getInfo(code: String): CurrencyInfo? = CURRENCIES.find { it.code == code }

    /**
     * Convert [amount] from [fromCode] to [toCode] using static rates (via INR as base).
     */
    fun convert(amount: Double, fromCode: String, toCode: String): Double {
        if (fromCode == toCode) return amount
        val from = CURRENCIES.find { it.code == fromCode } ?: return amount
        val to   = CURRENCIES.find { it.code == toCode   } ?: return amount
        val inINR = amount * from.rateToINR
        return inINR / to.rateToINR
    }

    /** Strip common currency prefixes from a raw amount string and return a Double. */
    fun parseAmount(raw: String): Double =
        raw.replace("₹", "").replace("Rs.", "").replace("Rs", "")
            .replace("$", "").replace("€", "").replace("£", "").replace("¥", "")
            .replace(",", "").trim().toDoubleOrNull() ?: 0.0
}
