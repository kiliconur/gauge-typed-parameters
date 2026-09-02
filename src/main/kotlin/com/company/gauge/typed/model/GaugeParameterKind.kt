package com.company.gauge.typed.model

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant

/**
 * The semantic kind of a Gauge step parameter, derived from the Java type of the
 * corresponding `PsiParameter`.
 *
 * Deliberately language-agnostic above [com.company.gauge.typed.java.JavaStepParameterResolver]
 * so that a Kotlin resolver can produce the same kinds later.
 */
sealed interface GaugeParameterKind {

    /**
     * A concrete Java enum such as `PageItems`. [constantNames] are the declared constants in
     * declaration order - they are offered directly, with no class-name browsing step.
     */
    data class SpecificEnumKind(val psiClass: PsiClass) : GaugeParameterKind {
        val typeName: String get() = psiClass.name ?: psiClass.qualifiedName ?: "enum"

        /**
         * Read once per instance. Instances are always created fresh from a just-resolved
         * `PsiType`, so there is no staleness risk in caching here.
         */
        val constantNames: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            psiClass.fields.filterIsInstance<PsiEnumConstant>().mapNotNull { it.name }
        }
    }

    /**
     * The parameter is declared as exactly `java.lang.Enum` - the raw base class, never a
     * concrete enum. That is an intentional signal from the step implementation: "any project
     * enum constant is acceptable here", which switches completion to the two-stage project
     * enum browser (enum class names first, then that class's constants).
     */
    data object GenericEnumKind : GaugeParameterKind

    /** `boolean` or `java.lang.Boolean`. */
    data object BooleanKind : GaugeParameterKind

    /** Any of the Java integral / floating point types and their box types. */
    data class NumericKind(val typeName: String, val integral: Boolean) : GaugeParameterKind

    /** `java.lang.String` / `CharSequence` - free text, no completion, no validation. */
    data object StringKind : GaugeParameterKind

    /** Anything we deliberately stay silent about (tables, custom types, generics, ...). */
    data object UnsupportedKind : GaugeParameterKind

    companion object {
        /**
         * Values the plugin can offer as completion for this kind without any further context,
         * empty when it cannot. [GenericEnumKind] deliberately yields nothing here: its
         * candidates depend on the project index and on what has been typed so far, and are
         * produced by [com.company.gauge.typed.enums.GenericEnumBrowser].
         */
        fun completionValues(kind: GaugeParameterKind): List<String> = when (kind) {
            is SpecificEnumKind -> kind.constantNames
            BooleanKind -> listOf("true", "false")
            else -> emptyList()
        }
    }
}
