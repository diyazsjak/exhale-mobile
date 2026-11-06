package app.exhale.domain

import app.exhale.domain.SettingsInput.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsInputTest {
    private val saved = UserSettings(
        onboarded = true, cigsPerPack = 20, packPriceMinor = 1000, currencyCode = "USD",
        goalName = "New headphones", goalMinor = 59900,
    )

    @Test fun digits_dropsEverythingButDigits_andCaps() {
        assertEquals("25", SettingsInput.digits("2a5"))
        assertEquals("100", SettingsInput.digits("1000"))
        assertEquals("", SettingsInput.digits("-x"))
    }

    @Test fun money_keepsOneMark_andTwoDecimals() {
        assertEquals("12.34", SettingsInput.money("12.345x"))
        assertEquals("12,5", SettingsInput.money("12,5."))
        assertEquals("1234567", SettingsInput.money("123456789"))
        assertEquals("10.", SettingsInput.money("10."))
        assertEquals("", SettingsInput.money("abc"))
    }

    @Test fun currency_lettersOnly_upperCase_three() {
        assertEquals("CHF", SettingsInput.currency("ch1fx"))
        assertEquals("KZ", SettingsInput.currency("kz"))
        assertEquals("", SettingsInput.currency("₸ 12"))
    }

    @Test fun packSize_isOneToHundred() {
        assertNull(SettingsInput.packSize(""))
        assertNull(SettingsInput.packSize("0"))
        assertNull(SettingsInput.packSize("101"))
        assertEquals(25, SettingsInput.packSize("25"))
    }

    @Test fun minor_parsesBothMarks() {
        assertEquals(1050L, SettingsInput.minor("10.5"))
        assertEquals(1050L, SettingsInput.minor("10,50"))
        assertEquals(1000L, SettingsInput.minor("10."))
        assertEquals(0L, SettingsInput.minor("0"))
        assertNull(SettingsInput.minor(""))
        assertNull(SettingsInput.minor("."))
    }

    @Test fun fieldTexts() {
        assertEquals("10.00", SettingsInput.priceText(1000))
        assertEquals("599", SettingsInput.amountText(59900))
        assertEquals("599.50", SettingsInput.amountText(59950))
    }

    @Test fun currencyOk_needsAKnownCode() {
        assertTrue(SettingsInput.currencyOk("KZT"))
        assertFalse(SettingsInput.currencyOk("KZ"))
        assertFalse(SettingsInput.currencyOk("QQQ"))
    }

    @Test fun applyPack_savesValidFields_keepsTheRest() {
        val a = SettingsInput.applyPack(saved, perPack = "25", price = "", currency = "EUR")
        assertTrue(a.changed)
        assertEquals(25, a.settings.cigsPerPack)
        assertEquals(1000L, a.settings.packPriceMinor)
        assertEquals("EUR", a.settings.currencyCode)
        assertEquals(listOf(Field.PACK_PRICE), a.kept)
    }

    @Test fun applyPack_nothingTyped_changesNothing() {
        val a = SettingsInput.applyPack(saved, "20", "10.00", "USD")
        assertFalse(a.changed)
        assertTrue(a.kept.isEmpty())
    }

    @Test fun applyPack_allInvalid_keepsAll() {
        val a = SettingsInput.applyPack(saved, "0", ".", "QQ")
        assertFalse(a.changed)
        assertEquals(listOf(Field.PACK_SIZE, Field.PACK_PRICE, Field.CURRENCY), a.kept)
    }

    @Test fun applyGoal_blankNameBecomesSomethingNice_emptyAmountKept() {
        val a = SettingsInput.applyGoal(saved, "  ", "")
        assertTrue(a.changed)
        assertEquals(SettingsInput.GOAL_NAME_BLANK, a.settings.goalName)
        assertEquals(59900L, a.settings.goalMinor)
        assertEquals(listOf(Field.GOAL_AMOUNT), a.kept)
    }

    @Test fun goalNameBlank_isTheDefaultGoalName() {
        assertEquals(SettingsInput.GOAL_NAME_BLANK, UserSettings().goalName)
    }

    @Test fun applyGoal_savesNameAndAmount() {
        val a = SettingsInput.applyGoal(saved, "A weekend in Almaty", "1250000")
        assertEquals("A weekend in Almaty", a.settings.goalName)
        assertEquals(125_000_000L, a.settings.goalMinor)
        assertTrue(a.kept.isEmpty())
    }

    @Test fun quickCurrencies_regionFirstThenUsdEurKzt() {
        assertEquals("CHF", SettingsInput.regionCurrency(Locale("de", "CH")))
        assertEquals(listOf("CHF", "USD", "EUR"), SettingsInput.quickCurrencies(Locale("de", "CH")))
        assertEquals(listOf("EUR", "USD", "KZT"), SettingsInput.quickCurrencies(Locale("de", "DE")))
        // A locale without a region has no currency of its own.
        assertNull(SettingsInput.regionCurrency(Locale("en")))
        assertEquals(listOf("USD", "EUR", "KZT"), SettingsInput.quickCurrencies(Locale("en")))
    }
}
