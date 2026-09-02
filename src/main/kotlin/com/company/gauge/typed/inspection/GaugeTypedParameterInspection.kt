package com.company.gauge.typed.inspection

import com.company.gauge.typed.GaugeTypedParametersBundle
import com.company.gauge.typed.gauge.GaugeStepAdapter
import com.company.gauge.typed.gauge.GaugeStepResolver
import com.company.gauge.typed.java.JavaStepParameterResolver
import com.company.gauge.typed.model.GaugeValueValidator
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.company.gauge.typed.gauge.GaugeDialect

/**
 * Flags Gauge step parameter values in `.spec` and `.cpt` files that the resolved Java step implementation could never
 * accept: an unknown enum constant, a non-boolean for a `boolean` parameter, a non-number
 * for a numeric parameter.
 *
 * Silence is the default. Nothing is reported when the project is indexing, when the step
 * cannot be resolved, when it resolves ambiguously, when the placeholder count does not match
 * the Java parameter count, or when the parameter type is anything else.
 *
 * The decision of what is valid lives in [GaugeValueValidator]; this class only walks the PSI
 * and turns violations into localized problems with quick fixes.
 */
class GaugeTypedParameterInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val project = holder.project
        if (project.isDisposed || DumbService.isDumb(project)) return PsiElementVisitor.EMPTY_VISITOR
        // Registered without a language attribute so that one settings entry covers both Gauge
        // languages; every other file type is dismissed right here.
        if (GaugeDialect.of(holder.file) == null) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                // Both dialects: SpecStep in .spec files, ConceptStep in .cpt files.
                if (!GaugeDialect.isStepElement(element)) return
                try {
                    inspectStep(element, holder)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: IndexNotReadyException) {
                    LOG.debug(e)
                } catch (e: Throwable) {
                    LOG.warn("Gauge typed parameter inspection failed", e)
                }
            }
        }
    }

    private fun inspectStep(step: PsiElement, holder: ProblemsHolder) {
        val args = GaugeStepAdapter.argsOf(step)
        if (args.isEmpty()) return

        val template = GaugeStepAdapter.buildTemplate(step)
        val method = GaugeStepResolver.getInstance(holder.project)
            .resolveImplementation(step, template) ?: return

        args.forEachIndexed { index, arg ->
            if (!GaugeStepAdapter.isStaticArg(arg)) return@forEachIndexed
            val leaf = GaugeStepAdapter.staticArgValueLeaf(arg) ?: return@forEachIndexed
            val value = leaf.text
            if (value.isBlank()) return@forEachIndexed

            val parameter = JavaStepParameterResolver.parameterAt(method, index, template.placeholderCount)
                ?: return@forEachIndexed
            val kind = JavaStepParameterResolver.kindOf(parameter)
            val violation = GaugeValueValidator.validate(kind, value) ?: return@forEachIndexed

            val fixes: Array<LocalQuickFix> = violation.suggestions
                .map { ReplaceParameterValueFix(it) }
                .toTypedArray()

            holder.registerProblem(
                leaf,
                messageFor(violation),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                *fixes,
            )
        }
    }

    private fun messageFor(violation: GaugeValueValidator.Violation): String = when (violation) {
        is GaugeValueValidator.Violation.UnknownEnumConstant ->
            GaugeTypedParametersBundle.message(
                "inspection.message.unknown.enum.value",
                violation.typeName,
                violation.value,
            )

        is GaugeValueValidator.Violation.InvalidBoolean ->
            GaugeTypedParametersBundle.message("inspection.message.invalid.boolean.value", violation.value)

        is GaugeValueValidator.Violation.InvalidNumber ->
            GaugeTypedParametersBundle.message(
                "inspection.message.invalid.numeric.value",
                violation.typeName,
                violation.value,
            )
    }

    private companion object {
        val LOG: Logger = Logger.getInstance(GaugeTypedParameterInspection::class.java)
    }
}
