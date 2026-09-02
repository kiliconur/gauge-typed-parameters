package com.company.gauge.typed.java

import com.company.gauge.typed.model.GaugeParameterKind
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType

/**
 * Maps a Gauge placeholder index onto the Java `PsiParameter` of the step implementation
 * and classifies that parameter's `PsiType`.
 *
 * The whole mapping is positional: the n-th `<placeholder>` of a `@Step` annotation value
 * corresponds to the n-th method parameter - that is exactly the contract the Gauge runtime
 * uses when it invokes the method.
 */
object JavaStepParameterResolver {

    /**
     * The raw base class used as the "browse every project enum" signal. A parameter declared
     * as exactly this type is [GaugeParameterKind.GenericEnumKind]; a parameter declared as a
     * concrete enum stays [GaugeParameterKind.SpecificEnumKind].
     */
    const val GENERIC_ENUM_FQN: String = "java.lang.Enum"

    /**
     * @param placeholderIndex 0-based index of the parameter under the caret
     * @param placeholderCount number of Gauge parameters in the invocation
     * @return the matching parameter, or `null` when the mapping is not certain
     */
    fun parameterAt(method: PsiMethod, placeholderIndex: Int, placeholderCount: Int): PsiParameter? {
        if (placeholderIndex < 0) return null
        val parameters = method.parameterList.parameters
        // A mismatch means Gauge itself would fail at run time; stay silent rather than guess.
        if (parameters.size != placeholderCount) return null
        return parameters.getOrNull(placeholderIndex)
    }

    fun kindOf(parameter: PsiParameter): GaugeParameterKind = kindOf(parameter.type)

    fun kindOf(type: PsiType): GaugeParameterKind {
        if (type is PsiPrimitiveType) {
            return when (type.canonicalText) {
                "boolean" -> GaugeParameterKind.BooleanKind
                "int", "long", "short", "byte" -> GaugeParameterKind.NumericKind(type.canonicalText, true)
                "double", "float" -> GaugeParameterKind.NumericKind(type.canonicalText, false)
                else -> GaugeParameterKind.UnsupportedKind
            }
        }

        val classType = type as? PsiClassType ?: return GaugeParameterKind.UnsupportedKind
        val psiClass = classType.resolve() ?: return GaugeParameterKind.UnsupportedKind

        // `Enum` itself is not an enum (Enum.isEnum() is false), but check it first anyway so
        // that no future platform change can make a generic parameter look like a specific one.
        if (psiClass.qualifiedName == GENERIC_ENUM_FQN) return GaugeParameterKind.GenericEnumKind

        if (psiClass.isEnum) return GaugeParameterKind.SpecificEnumKind(psiClass)

        return when (psiClass.qualifiedName) {
            "java.lang.Boolean" -> GaugeParameterKind.BooleanKind
            "java.lang.Integer" -> GaugeParameterKind.NumericKind("Integer", true)
            "java.lang.Long" -> GaugeParameterKind.NumericKind("Long", true)
            "java.lang.Short" -> GaugeParameterKind.NumericKind("Short", true)
            "java.lang.Byte" -> GaugeParameterKind.NumericKind("Byte", true)
            "java.lang.Double" -> GaugeParameterKind.NumericKind("Double", false)
            "java.lang.Float" -> GaugeParameterKind.NumericKind("Float", false)
            "java.math.BigInteger" -> GaugeParameterKind.NumericKind("BigInteger", true)
            "java.math.BigDecimal" -> GaugeParameterKind.NumericKind("BigDecimal", false)
            "java.lang.String", "java.lang.CharSequence" -> GaugeParameterKind.StringKind
            else -> GaugeParameterKind.UnsupportedKind
        }
    }
}
