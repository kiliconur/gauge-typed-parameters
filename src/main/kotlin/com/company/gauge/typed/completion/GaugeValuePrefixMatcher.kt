package com.company.gauge.typed.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher

/**
 * Prefix matcher for Gauge parameter values.
 *
 * Two responsibilities:
 *  * the *prefix* (inherited from [PrefixMatcher]) defines the document range a selected
 *    lookup element replaces - it is exactly the text already typed inside the quotes, so
 *    picking `LOGIN_BUTTON` on `"LOG<caret>"` yields `"LOGIN_BUTTON"` and never
 *    `"LOGLOGIN_BUTTON"` or `""LOGIN_BUTTON""`;
 *  * [prefixMatches] does the filtering, case-insensitively and camel-hump aware.
 *
 * When [matchAll] is set, filtering is disabled while the replacement range is kept. That is
 * what makes `* "WRONG_VALUE<caret>"` still offer every enum constant on Ctrl+Space, so the
 * bogus value can be replaced in one keystroke.
 */
class GaugeValuePrefixMatcher(
    prefix: String,
    private val matchAll: Boolean,
) : PrefixMatcher(prefix) {

    private val delegate = CamelHumpMatcher(prefix, false)

    override fun prefixMatches(name: String): Boolean = matchAll || delegate.prefixMatches(name)

    override fun isStartMatch(name: String): Boolean = matchAll || delegate.isStartMatch(name)

    override fun cloneWithPrefix(prefix: String): PrefixMatcher =
        if (prefix == this.prefix) this else GaugeValuePrefixMatcher(prefix, matchAll)

    companion object {
        /**
         * Builds a matcher for [prefix]; falls back to "show everything" when nothing in
         * [candidates] matches what has been typed.
         */
        @JvmStatic
        fun forCandidates(prefix: String, candidates: Collection<String>): GaugeValuePrefixMatcher {
            val probe = CamelHumpMatcher(prefix, false)
            val anyMatch = candidates.any { probe.prefixMatches(it) }
            return GaugeValuePrefixMatcher(prefix, !anyMatch)
        }
    }
}
