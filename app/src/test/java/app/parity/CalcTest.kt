package app.parity

import app.parity.calc.Calc
import app.parity.calc.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {
    private fun type(vararg keys: String) = keys.fold("") { e, k -> Calc.key(e, k) }
    private fun value(vararg keys: String) = Calc.evaluate(type(*keys))?.stripTrailingZeros()?.toPlainString()

    @Test fun digitsAndLeadingZero() {
        assertEquals("5", type("0", "5"))
        assertEquals("0", type("0", "0"))
        assertEquals("100", type("1", "00"))
    }

    @Test fun onlyOneDecimalPointPerNumber() {
        assertEquals("1.5", type("1", ".", "5", "."))
        assertEquals("1.5×0.", type("1", ".", "5", "*", "."))
        assertEquals("0.", type("."))
    }

    @Test fun precedenceAndOperators() {
        assertEquals("14", value("2", "+", "3", "*", "4"))
        assertEquals("3.75", value("4", "5", "/", "1", "2"))
        assertEquals("-3", value("-", "3"))
        assertEquals("6", value("1", "2", "/", "2"))
    }

    @Test fun trailingOperatorIsIgnoredWhileTyping() {
        assertEquals("45", value("4", "5", "/"))
        assertEquals("0", value("-"))
    }

    @Test fun secondOperatorReplacesFirst() {
        assertEquals("5÷", type("5", "+", "/"))
        assertEquals("", type("+"))
    }

    @Test fun divisionByZeroIsNull() {
        assertNull(Calc.evaluate("5÷0"))
    }

    @Test fun noFloatNoise() {
        assertEquals("0.3", value("0", ".", "1", "+", "0", ".", "2"))
        assertEquals("0.3", Calc.key(type("0", ".", "1", "+", "0", ".", "2"), "="))
    }

    @Test fun lengthCap() {
        val e = type(*Array(20) { "9" })
        assertEquals(Calc.MAX_DIGITS, e.length)
    }

    @Test fun backspaceAndClear() {
        assertEquals("1", type("1", "2", "<"))
        assertEquals("", type("1", "2", "C"))
    }

    @Test fun prettyGroupsAndSpaces() {
        assertEquals("1,234,567.89", Format.pretty("1234567.89"))
        assertEquals("45 ÷ 1,200", Format.pretty("45÷1200"))
        assertEquals("0", Format.pretty(""))
        assertEquals("−3", Format.pretty("−3"))
    }

    @Test fun amountFormatting() {
        assertEquals("784.49", Format.amount(784.49))
        assertEquals("1,000", Format.amount(1000.0))
        assertEquals("3.6875", Format.amount(3.6875))
        assertEquals("0.0067123", Format.amount(0.0067123))
        assertEquals("0", Format.amount(0.0))
    }

    @Test fun rateKeepsSixSignificantDigits() {
        assertEquals("7.8449", Format.rate(7.8449))
        assertEquals("149.123", Format.rate(149.123456))
        assertEquals("0.0067123", Format.rate(0.0067123))
        assertEquals("0.00671235", Format.rate(0.006712351))
    }

    @Test fun percentSignsAreTypographic() {
        assertEquals("+1.25%", Format.percent(0.0125))
        assertEquals("−2.00%", Format.percent(-0.02))
        assertEquals("0.00%", Format.percent(0.0))
    }
}
