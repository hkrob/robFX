package app.robfx.data

import java.util.Locale

data class Currency(val code: String, val name: String, val country: String) {
    /** Regional-indicator flag emoji; blank when there is no country. */
    val flag: String
        get() = if (country.length != 2) "" else country.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

object Currencies {
    val all: List<Currency> = listOf(
        Currency("USD", "US Dollar", "US"), Currency("EUR", "Euro", "EU"), Currency("GBP", "British Pound", "GB"),
        Currency("JPY", "Japanese Yen", "JP"), Currency("CNY", "Chinese Yuan", "CN"), Currency("HKD", "Hong Kong Dollar", "HK"),
        Currency("AUD", "Australian Dollar", "AU"), Currency("CAD", "Canadian Dollar", "CA"), Currency("CHF", "Swiss Franc", "CH"),
        Currency("NZD", "New Zealand Dollar", "NZ"), Currency("SGD", "Singapore Dollar", "SG"), Currency("KRW", "South Korean Won", "KR"),
        Currency("TWD", "New Taiwan Dollar", "TW"), Currency("MOP", "Macanese Pataca", "MO"), Currency("INR", "Indian Rupee", "IN"),
        Currency("THB", "Thai Baht", "TH"), Currency("MYR", "Malaysian Ringgit", "MY"), Currency("IDR", "Indonesian Rupiah", "ID"),
        Currency("PHP", "Philippine Peso", "PH"), Currency("VND", "Vietnamese Dong", "VN"), Currency("PKR", "Pakistani Rupee", "PK"),
        Currency("BDT", "Bangladeshi Taka", "BD"), Currency("LKR", "Sri Lankan Rupee", "LK"), Currency("KZT", "Kazakhstani Tenge", "KZ"),
        Currency("AED", "UAE Dirham", "AE"), Currency("SAR", "Saudi Riyal", "SA"), Currency("QAR", "Qatari Riyal", "QA"),
        Currency("KWD", "Kuwaiti Dinar", "KW"), Currency("BHD", "Bahraini Dinar", "BH"), Currency("OMR", "Omani Rial", "OM"),
        Currency("ILS", "Israeli Shekel", "IL"), Currency("TRY", "Turkish Lira", "TR"), Currency("EGP", "Egyptian Pound", "EG"),
        Currency("ZAR", "South African Rand", "ZA"), Currency("NGN", "Nigerian Naira", "NG"), Currency("KES", "Kenyan Shilling", "KE"),
        Currency("MAD", "Moroccan Dirham", "MA"), Currency("BRL", "Brazilian Real", "BR"), Currency("MXN", "Mexican Peso", "MX"),
        Currency("ARS", "Argentine Peso", "AR"), Currency("CLP", "Chilean Peso", "CL"), Currency("COP", "Colombian Peso", "CO"),
        Currency("PEN", "Peruvian Sol", "PE"), Currency("SEK", "Swedish Krona", "SE"), Currency("NOK", "Norwegian Krone", "NO"),
        Currency("DKK", "Danish Krone", "DK"), Currency("PLN", "Polish Zloty", "PL"), Currency("CZK", "Czech Koruna", "CZ"),
        Currency("HUF", "Hungarian Forint", "HU"), Currency("RON", "Romanian Leu", "RO"), Currency("BGN", "Bulgarian Lev", "BG"),
        Currency("ISK", "Icelandic Krona", "IS"), Currency("UAH", "Ukrainian Hryvnia", "UA"), Currency("RUB", "Russian Ruble", "RU"),
    )

    val codes: Set<String> = all.map { it.code }.toSet()
    private val byCode = all.associateBy { it.code }

    fun get(code: String): Currency = byCode[code] ?: Currency(code, code, "")

    /** First launch: the phone's own currency against the dollar (or the euro, if it already is the dollar). */
    fun localPair(locale: Locale = Locale.getDefault()): Pair<String, String> {
        val local = runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()
        val from = if (local != null && local in codes) local else "USD"
        return from to if (from == "USD") "EUR" else "USD"
    }
}
