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
     * `java.lang.String` / `CharSequence`.
     *
     * Free text as far as the language is concerned: **never validated, never restricted** - any
     * value the user types is legal. As pure editing assistance, completion offers the two-stage
     * project enum browser here (enum class names first, then that class's constants), because
     * in practice a great many Gauge string parameters carry an enum constant name that the step
     * implementation converts itself. Nothing about that is enforced.
     */
    data object StringEnumBrowserKind : GaugeParameterKind

    /** `boolean` or `java.lang.Boolean`. */
    data object BooleanKind : GaugeParameterKind

    /** Any of the Java integral / floating point types and their box types. */
    data class NumericKind(val typeName: String, val integral: Boolean) : GaugeParameterKind

    /** Anything we deliberately stay silent about (tables, custom types, generics, ...). */
    data object UnsupportedKind : GaugeParameterKind

    companion object {
        /**
         * Values the plugin can offer as completion for this kind without any further context,
         * empty when it cannot. [StringEnumBrowserKind] deliberately yields nothing here: its
         * candidates depend on the project index and on what has been typed so far, and are
         * produced by [com.company.gauge.typed.enums.ProjectEnumBrowser].
         */
        fun completionValues(kind: GaugeParameterKind): List<String> = when (kind) {
            is SpecificEnumKind -> kind.constantNames
            BooleanKind -> listOf("true", "false")
            else -> emptyList()
        }
    }
}
