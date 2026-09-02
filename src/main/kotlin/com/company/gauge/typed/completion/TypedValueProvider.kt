package com.company.gauge.typed.completion

import com.company.gauge.typed.model.GaugeParameterKind

/** A single completion candidate produced from a Java type. */
data class TypedValue(
    val value: String,
    val typeText: String?,
    val priority: Double,
)

/** Produces the completion candidates for one semantic parameter kind. */
interface TypedValueProvider {
    fun supports(kind: GaugeParameterKind): Boolean
    fun values(kind: GaugeParameterKind): List<TypedValue>
}

/** `enum Element { LOGIN_BUTTON, ... }` -> `LOGIN_BUTTON`, ... */
class EnumCompletionProvider : TypedValueProvider {
    override fun supports(kind: GaugeParameterKind) = kind is GaugeParameterKind.SpecificEnumKind

    override fun values(kind: GaugeParameterKind): List<TypedValue> {
        val enumKind = kind as? GaugeParameterKind.SpecificEnumKind ?: return emptyList()
        return enumKind.constantNames.map { TypedValue(it, enumKind.typeName, PRIORITY) }
    }

    private companion object {
        const val PRIORITY = 100.0
    }
}

/** `boolean` / `Boolean` -> `true`, `false` */
class BooleanCompletionProvider : TypedValueProvider {
    override fun supports(kind: GaugeParameterKind) = kind === GaugeParameterKind.BooleanKind

    override fun values(kind: GaugeParameterKind): List<TypedValue> =
        if (supports(kind)) {
            listOf(
                TypedValue("true", "boolean", PRIORITY),
                TypedValue("false", "boolean", PRIORITY - 1),
            )
        } else {
            emptyList()
        }

    private companion object {
        const val PRIORITY = 100.0
    }
}
