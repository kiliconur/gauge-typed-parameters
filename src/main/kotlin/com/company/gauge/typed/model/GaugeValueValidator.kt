package com.company.gauge.typed.model

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Pure validation of a Gauge static parameter value against the semantic kind of the Java
 * parameter it maps to.
 *
 * Deliberately free of IntelliJ APIs and of message formatting so that it can be reasoned about -
 * and unit tested - in isolation. [com.company.gauge.typed.inspection.GaugeTypedParameterInspection]
 * turns a [Violation] into a localized message plus quick fixes.
 */
object GaugeValueValidator {

    sealed interface Violation {
        val value: String
        val suggestions: List<String>

        data class UnknownEnumConstant(
            val typeName: String,
            override val value: String,
            override val suggestions: List<String>,
        ) : Violation

        data class InvalidBoolean(
            override val value: String,
            override val suggestions: List<String>,
        ) : Violation

        data class InvalidNumber(
            val typeName: String,
            override val value: String,
        ) : Violation {
            override val suggestions: List<String> get() = emptyList()
        }
    }

    private const val MAX_SUGGESTIONS = 3

    /** @return the violation, or `null` when [value] is acceptable (or cannot be judged). */
    fun validate(kind: GaugeParameterKind, value: String): Violation? = when (kind) {
        is GaugeParameterKind.SpecificEnumKind -> validateEnum(kind, value)
        GaugeParameterKind.BooleanKind -> validateBoolean(value)
        is GaugeParameterKind.NumericKind -> validateNumeric(kind, value)
        // A String parameter is unrestricted: "anything", "custom value", "abc123" are all
        // legal, and the enum browser offered on it is completion assistance only. It is never
        // validated against the project's enums - not even against the class the user just
        // browsed - so intermediate text such as "PageItems2." is never marked either.
        GaugeParameterKind.StringEnumBrowserKind, GaugeParameterKind.UnsupportedKind -> null
    }

    private fun validateEnum(kind: GaugeParameterKind.SpecificEnumKind, value: String): Violation? {
        val constants = kind.constantNames
        // An enum with no readable constants means incomplete PSI - stay silent.
        if (constants.isEmpty() || value in constants) return null
        return Violation.UnknownEnumConstant(kind.typeName, value, suggestions(value, constants))
    }

    private fun validateBoolean(value: String): Violation? {
        if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) return null
        return Violation.InvalidBoolean(value, suggestions(value, listOf("true", "false")))
    }

    private fun validateNumeric(kind: GaugeParameterKind.NumericKind, value: String): Violation? {
        val text = value.trim()
        val valid = if (kind.integral) {
            runCatching { BigInteger(text) }.isSuccess
        } else {
            runCatching { BigDecimal(text) }.isSuccess
        }
        return if (valid) null else Violation.InvalidNumber(kind.typeName, text)
    }

    /** The closest candidates to [value], best first, at most [MAX_SUGGESTIONS]. */
    fun suggestions(value: String, candidates: List<String>): List<String> {
        if (value.isEmpty()) return emptyList()
        val threshold = maxOf(2, value.length / 3)
        return candidates
            .map { it to editDistance(value.uppercase(), it.uppercase()) }
            .filter { it.second <= threshold }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }

    /** Plain Levenshtein distance - used only to rank quick-fix suggestions. */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
