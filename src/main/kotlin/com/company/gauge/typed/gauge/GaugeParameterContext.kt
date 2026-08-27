package com.company.gauge.typed.gauge

import com.company.gauge.typed.GtpLog
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.thoughtworks.gauge.language.psi.SpecArg
import com.thoughtworks.gauge.language.psi.SpecStep

/**
 * Everything the typed-parameter machinery needs to know about "the Gauge parameter the
 * caret is in".
 *
 * @param step the step invocation PSI element
 * @param template canonical, parameterised form of that invocation
 * @param placeholderIndex 0-based index of the parameter under the caret
 * @param prefix the already typed text of that parameter, up to the caret
 * @param insideQuotes true for `* "LOG<caret>" ...`, false for the optional
 *        auto-quote case `* LOG<caret> ...`
 * @param valueLeaf the `ARG` token holding the current value, when it exists
 */
data class GaugeParameterContext(
    val step: SpecStep,
    val template: GaugeStepAdapter.StepTemplate,
    val placeholderIndex: Int,
    val prefix: String,
    val insideQuotes: Boolean,
    val valueLeaf: PsiElement?,
) {
    val placeholderCount: Int get() = template.placeholderCount

    companion object {

        /**
         * Builds the context for the leaf [element] under the caret.
         *
         * @param allowOutsideQuotes enables the optional auto-quote support; when false only
         *        carets inside an existing `"..."` parameter produce a context.
         */
        fun atCaret(
            element: PsiElement,
            caretOffset: Int,
            allowOutsideQuotes: Boolean,
        ): GaugeParameterContext? {
            val step = GaugeStepAdapter.findStep(element)
            if (step == null) {
                GtpLog.info("3. SpecStep NOT found for caret element - not inside a step")
                return null
            }
            GtpLog.info("3. SpecStep found: '${GaugeStepAdapter.normalize(step.text)}'")

            val arg = PsiTreeUtil.getParentOfType(element, SpecArg::class.java, false)

            val insideQuotes: Boolean
            when {
                arg != null -> {
                    if (!GaugeStepAdapter.isStaticArg(arg)) {
                        GtpLog.info("4. SpecArg found but it is a <dynamic> arg - not completable")
                        return null
                    }
                    GtpLog.info("4. SpecStaticArg found: value='${GaugeStepAdapter.staticArgValue(arg)}'")
                    insideQuotes = true
                }

                allowOutsideQuotes && GaugeStepAdapter.isStepTextToken(element) -> {
                    GtpLog.info("4. no SpecArg - caret is in plain step text (auto-quote path)")
                    insideQuotes = false
                }

                else -> {
                    GtpLog.info(
                        "4. no SpecArg and not a STEP text token" +
                            " (elementType=${element.node?.elementType}) - not completable",
                    )
                    return null
                }
            }

            val template = GaugeStepAdapter.buildTemplate(
                step,
                caretElement = element,
                caretOffset = caretOffset,
                treatCaretWordAsParameter = !insideQuotes,
            )
            if (template.caretPlaceholderIndex < 0) {
                GtpLog.info(
                    "5. placeholder index NOT determined | template='${template.text}'" +
                        " | placeholders=${template.placeholderCount}",
                )
                return null
            }
            GtpLog.info(
                "5. placeholder index=${template.caretPlaceholderIndex}" +
                    " of ${template.placeholderCount} | template='${template.text}'" +
                    " | prefix='${template.caretPrefix.orEmpty()}'",
            )

            return GaugeParameterContext(
                step = step,
                template = template,
                placeholderIndex = template.caretPlaceholderIndex,
                prefix = template.caretPrefix.orEmpty(),
                insideQuotes = insideQuotes,
                valueLeaf = arg?.let { GaugeStepAdapter.staticArgValueLeaf(it) },
            )
        }

        /** Builds the context for the [argIndex]-th parameter of [step], caret independent. */
        fun forArg(step: SpecStep, argIndex: Int): GaugeParameterContext? {
            val args = GaugeStepAdapter.argsOf(step)
            val arg = args.getOrNull(argIndex) ?: return null
            if (!GaugeStepAdapter.isStaticArg(arg)) return null
            val template = GaugeStepAdapter.buildTemplate(step)
            return GaugeParameterContext(
                step = step,
                template = template,
                placeholderIndex = argIndex,
                prefix = GaugeStepAdapter.staticArgValue(arg),
                insideQuotes = true,
                valueLeaf = GaugeStepAdapter.staticArgValueLeaf(arg),
            )
        }
    }
}
