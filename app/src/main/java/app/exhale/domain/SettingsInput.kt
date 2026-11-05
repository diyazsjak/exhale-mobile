package app.exhale.domain

import java.util.Currency

/**
 * Input rules for the Settings screens. Fields filter what is typed, so most mistakes can't be
 * made; what filtering can't prevent (an empty field, a pack of 0) is reported per field, and
 * going back saves the valid fields while keeping the saved value of the others.
 */
object SettingsInput {
    /** Upper bound for counts: cigarettes a day, starting allowance, pack size. */
    const val COUNT_MAX = 100
    const val PACK_DIGITS = 3
    const val MONEY_WHOLE_DIGITS = 7

    /** What an empty goal name is saved as. */
    const val GOAL_NAME_BLANK = "Something nice"

    /** Longest goal name the app accepts. */
    const val GOAL_NAME_MAX = 40

    /** Digits only, at most [max] of them. */
    fun digits(raw: String, max: Int = PACK_DIGITS): String = raw.filter { it in '0'..'9' }.take(max)

    /** A money amount as typed: digits, one decimal mark (`.` or `,`), two decimals at most. */
    fun money(raw: String): String {
        val kept = raw.filter { it in '0'..'9' || it == '.' || it == ',' }
        val mark = kept.indexOfFirst { it == '.' || it == ',' }
        if (mark < 0) return kept.take(MONEY_WHOLE_DIGITS)
        val whole = kept.substring(0, mark).take(MONEY_WHOLE_DIGITS)
        val cents = kept.substring(mark + 1).filter { it in '0'..'9' }.take(2)
        return whole + kept[mark] + cents
    }

    /** A currency code as typed: Latin letters only, upper case, three at most. */
    fun currency(raw: String): String = raw.filter { it in 'a'..'z' || it in 'A'..'Z' }.uppercase().take(3)

    /** Pack size from its field, or null when empty or outside 1..[COUNT_MAX]. */
    fun packSize(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..COUNT_MAX }

    /** Amount in minor units from a money field ("10.5" → 1050), or null when there is no number. */
    fun minor(text: String): Long? {
        val t = text.replace(',', '.').trimEnd('.')
        if (t.isEmpty() || t.startsWith('.')) return null
        val whole = t.substringBefore('.').toLongOrNull() ?: return null
        val cents = t.substringAfter('.', "").padEnd(2, '0').take(2).toLongOrNull() ?: 0
        return whole * 100 + cents
    }

    fun currencyOk(code: String): Boolean = code.length == 3 && runCatching { Currency.getInstance(code) }.isSuccess

    /** The text a price field starts with: always two decimals, "10.00". */
    fun priceText(minor: Long): String = "%d.%02d".format(minor / 100, minor % 100)

    /** The text an amount field starts with: "599", or "599.50" when there are cents. */
    fun amountText(minor: Long): String = if (minor % 100 == 0L) (minor / 100).toString() else priceText(minor)

    /** A field whose typed value could not be saved, so its saved value stays. */
    enum class Field { PACK_SIZE, PACK_PRICE, CURRENCY, GOAL_AMOUNT }

    /** The result of going back from an edit screen: what to save, whether anything changed, what was kept. */
    data class Applied(val settings: UserSettings, val changed: Boolean, val kept: List<Field>)

    /** A pack screen closing: saves each valid field, keeps the saved value of the rest. */
    fun applyPack(s: UserSettings, perPack: String, price: String, currency: String): Applied {
        val kept = mutableListOf<Field>()
        val size = packSize(perPack) ?: s.cigsPerPack.also { kept += Field.PACK_SIZE }
        val priceMinor = minor(price) ?: s.packPriceMinor.also { kept += Field.PACK_PRICE }
        val code = currency.takeIf(::currencyOk) ?: s.currencyCode.also { kept += Field.CURRENCY }
        val next = s.copy(cigsPerPack = size, packPriceMinor = priceMinor, currencyCode = code)
        return Applied(next, next != s, kept)
    }

    /** A goal screen closing: an empty name becomes [GOAL_NAME_BLANK]; an empty amount keeps the saved one. */
    fun applyGoal(s: UserSettings, name: String, amount: String): Applied {
        val kept = mutableListOf<Field>()
        val goal = minor(amount) ?: s.goalMinor.also { kept += Field.GOAL_AMOUNT }
        val next = s.copy(goalName = name.trim().ifEmpty { GOAL_NAME_BLANK }.take(GOAL_NAME_MAX), goalMinor = goal)
        return Applied(next, next != s, kept)
    }
}
