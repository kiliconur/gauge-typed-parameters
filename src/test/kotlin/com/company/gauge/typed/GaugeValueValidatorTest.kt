package com.company.gauge.typed

import com.company.gauge.typed.model.GaugeParameterKind
import com.company.gauge.typed.model.GaugeValueValidator
import com.company.gauge.typed.model.GaugeValueValidator.Violation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests - no IntelliJ fixture, no IDE, no indexing. These run in milliseconds and
 * cover the decision logic behind the inspection.
 */
class GaugeValueValidatorTest {

    @Test
    fun `boolean accepts true and false in any case`() {
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "true"))
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "false"))
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "TRUE"))
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "False"))
    }

    @Test
    fun `boolean rejects anything else and suggests the closest literal`() {
        val violation = GaugeValueValidator.validate(GaugeParameterKind.BooleanKind, "tru")
        assertTrue(violation is Violation.InvalidBoolean)
        assertEquals(listOf("true"), violation!!.suggestions)
    }

    @Test
    fun `integral numbers accept digits and reject text`() {
        val int = GaugeParameterKind.NumericKind("int", true)
        assertNull(GaugeValueValidator.validate(int, "3"))
        assertNull(GaugeValueValidator.validate(int, "-42"))
        val violation = GaugeValueValidator.validate(int, "abc")
        assertTrue(violation is Violation.InvalidNumber)
        assertEquals("int", (violation as Violation.InvalidNumber).typeName)
    }

    @Test
    fun `integral numbers reject a decimal point`() {
        val long = GaugeParameterKind.NumericKind("long", true)
        assertTrue(GaugeValueValidator.validate(long, "3.5") is Violation.InvalidNumber)
    }

    @Test
    fun `floating point numbers accept decimals`() {
        val double = GaugeParameterKind.NumericKind("double", false)
        assertNull(GaugeValueValidator.validate(double, "3.5"))
        assertNull(GaugeValueValidator.validate(double, "-0.125"))
        assertTrue(GaugeValueValidator.validate(double, "abc") is Violation.InvalidNumber)
    }

    @Test
    fun `string and unsupported kinds are never reported`() {
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "anything at all"))
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "custom value"))
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "abc123"))
        // Text left behind mid-browsing is still just text.
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.StringEnumBrowserKind, "PageItems2."))
        assertNull(GaugeValueValidator.validate(GaugeParameterKind.UnsupportedKind, "anything at all"))
    }

    @Test
    fun `edit distance ranks the typo fix first`() {
        val candidates = listOf("LOGIN_BUTTON", "LOGOUT_BUTTON", "SETTINGS_BUTTON")
        val suggestions = GaugeValueValidator.suggestions("LOGNI_BUTTON", candidates)
        assertEquals("LOGIN_BUTTON", suggestions.first())
        assertTrue("at most three fixes are offered", suggestions.size <= 3)
        assertTrue("unrelated constants are not offered", "SETTINGS_BUTTON" !in suggestions)
    }

    @Test
    fun `a value nothing resembles gets no suggestions`() {
        val candidates = listOf("LOGIN_BUTTON", "LOGOUT_BUTTON")
        assertEquals(emptyList<String>(), GaugeValueValidator.suggestions("zzzz", candidates))
    }

    @Test
    fun `levenshtein basics`() {
        assertEquals(0, GaugeValueValidator.editDistance("ABC", "ABC"))
        assertEquals(1, GaugeValueValidator.editDistance("ABC", "ABD"))
        assertEquals(2, GaugeValueValidator.editDistance("LOGNI", "LOGIN") /* two substitutions */)
        assertEquals(3, GaugeValueValidator.editDistance("", "ABC"))
    }
}
