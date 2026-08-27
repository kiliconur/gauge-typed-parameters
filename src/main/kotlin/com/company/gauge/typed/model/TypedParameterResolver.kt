package com.company.gauge.typed.model

import com.company.gauge.typed.GtpLog
import com.company.gauge.typed.gauge.GaugeParameterContext
import com.company.gauge.typed.gauge.GaugeStepResolver
import com.company.gauge.typed.java.JavaStepParameterResolver
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter

/**
 * Turns "the caret is in this Gauge parameter" into "this parameter is a Java enum / boolean /
 * number".
 *
 * Every failure mode - dumb mode, unresolved step, ambiguous step, parameter count mismatch,
 * unsupported type - is expressed as `null` / [GaugeParameterKind.UnsupportedKind]. Callers
 * must stay silent in those cases; no false positives.
 */
object TypedParameterResolver {

    data class Resolved(
        val method: PsiMethod,
        val parameter: PsiParameter,
        val kind: GaugeParameterKind,
    )

    fun resolve(project: Project, context: GaugeParameterContext): Resolved? {
        if (project.isDisposed || DumbService.isDumb(project)) return null


        val method = GaugeStepResolver.getInstance(project).resolveImplementation(
            step = context.step,
            template = context.template,
            // The auto-quote case rewrites plain step text, so Gauge's own reference (which
            // matches the literal step text) cannot help there.
            useGaugeReference = context.insideQuotes,
        )
        if (method == null) return null

        val parameters = method.parameterList.parameters
        val parameter = JavaStepParameterResolver.parameterAt(
            method,
            context.placeholderIndex,
            context.placeholderCount,
        )
        if (parameter == null) {
            GtpLog.info(
                "7. NO PsiParameter: placeholder index=${context.placeholderIndex}," +
                    " Gauge placeholders=${context.placeholderCount}," +
                    " Java parameters=${parameters.size}" +
                    (if (parameters.size != context.placeholderCount) " - COUNT MISMATCH" else ""),
            )
            return null
        }
        GtpLog.info(
            "7. PsiParameter '${parameter.name}' type=${parameter.type.canonicalText}" +
                " (${parameter.type.javaClass.simpleName})",
        )

        val kind = JavaStepParameterResolver.kindOf(parameter)
        when (kind) {
            is GaugeParameterKind.EnumKind -> GtpLog.info(
                "8. enum class resolved: ${kind.psiClass.qualifiedName}" +
                    " | 9. constants=${kind.constantNames}",
            )
            GaugeParameterKind.BooleanKind -> GtpLog.info("8. boolean parameter | 9. values=[true, false]")
            else -> GtpLog.info("8. kind=$kind - no completion values for this type by design")
        }

        return Resolved(method, parameter, kind)
    }
}
