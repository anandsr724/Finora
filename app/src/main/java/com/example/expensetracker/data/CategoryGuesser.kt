package com.example.expensetracker.data

object CategoryGuesser {

    // First match wins — order matters.
    private val RULES = listOf(
        "cat_food"          to listOf("zomato", "swiggy", "food", "restaurant", "cafe", "pizza", "burger", "dining", "eat", "barbeque", "biryani", "dominos", "kfc", "mcdonald"),
        "cat_transport"     to listOf("uber", "ola", "rapido", "irctc", "bus", "metro", "fuel", "petrol", "diesel", "auto", "taxi", "cabify", "namma", "bmtc", "dtc"),
        "cat_groceries"     to listOf("bigbasket", "blinkit", "zepto", "grofers", "jiomart", "grocery", "supermarket", "dmart", "reliance fresh", "more supermarket"),
        "cat_shopping"      to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "snapdeal", "tatacliq", "mall", "shoppers stop", "lifestyle"),
        "cat_entertainment" to listOf("netflix", "hotstar", "spotify", "prime video", "zee5", "sonyliv", "gaming", "steam", "playstation", "youtube premium", "jiocinema", "bookmyshow"),
        "cat_health"        to listOf("apollo", "medplus", "netmeds", "pharmeasy", "1mg", "pharma", "hospital", "clinic", "doctor", "pharmacy", "medicine", "diagnostic", "lab test"),
        "cat_utilities"     to listOf("electricity", "water", "gas", "broadband", "wifi", "airtel", "jio", "bsnl", "vodafone", "vi ", "bescom", "msedcl", "tata power", "recharge"),
        "cat_education"     to listOf("byju", "unacademy", "udemy", "coursera", "school", "college", "tuition", "fees", "vedantu", "toppr", "white hat", "simplilearn"),
        "cat_travel"        to listOf("hotel", "makemytrip", "goibibo", "booking.com", "oyo", "airbnb", "cleartrip", "flight", "ixigo", "yatra"),
        "cat_personal"      to listOf("salon", "spa", "grooming", "haircut", "beauty", "parlour", "massage")
    )

    fun guess(text: String): String {
        val lower = text.lowercase()
        for ((category, keywords) in RULES) {
            if (keywords.any { lower.contains(it) }) return category
        }
        return "cat_other"
    }
}
