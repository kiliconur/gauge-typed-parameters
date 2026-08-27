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

    /** A Java enum. [constantNames] are the declared constants in declaration order. */
    data class EnumKind(val psiClass: PsiClass) : GaugeParameterKind {
        val typeName: String get() = psiClass.name ?: psiClass.qualifiedName ?: "enum"

        /**
         * Read once per instance. Instances are always created fresh from a just-resolved
         * `PsiType`, so there is no staleness risk in caching here.
         */
        val constantNames: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            psiClass.fields.filterIsInstance<PsiEnumConstant>().mapNotNull { it.name }
        }
    }

    /** `boolean` or `java.lang.Boolean`. */
    data object BooleanKind : GaugeParameterKind

    /** Any of the Java integral / floating point types and their box types. */
    data class NumericKind(val typeName: String, val integral: Boolean) : GaugeParameterKind

    /** `java.lang.String` / `CharSequence` - free text, no completion, no validation. */
    data object StringKind : GaugeParameterKind

    /** Anything we deliberately stay silent about (tables, custom types, generics, ...). */
    data object UnsupportedKind : GaugeParameterKind

    companion object {
        /** Values the plugin can offer as completion for this kind, empty when it cannot. */
        fun completionValues(kind: GaugeParameterKind): List<String> = when (kind) {
            is EnumKind -> kind.constantNames
            BooleanKind -> listOf("true", "false")
            else -> emptyList()
        }
    }
}
