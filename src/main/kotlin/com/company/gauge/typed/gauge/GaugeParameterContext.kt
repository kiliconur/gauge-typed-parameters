package com.company.gauge.typed.gauge

import com.company.gauge.typed.GtpLog
import com.intellij.psi.PsiElement

/**
 * Everything the typed-parameter machinery needs to know about "the Gauge parameter the
 * caret is in".
 *
 * Produced identically from `.spec` and from `.cpt` PSI - see [GaugeDialect].
 *
 * @param step the step invocation PSI element (`SpecStep` or `ConceptStep`)
 * @param dialect which Gauge language [step] came from
 * @param template canonical, parameterised form of that invocation
 * @param placeholderIndex 0-based index of the parameter under the caret
 * @param prefix the already typed text of that parameter, up to the caret
 * @param valueStartOffset document offset where the parameter value starts, or -1
 * @param insideQuotes true for `* "LOG<caret>" ...`, false for the optional
 *        auto-quote case `* LOG<caret> ...`
 * @param valueLeaf the `ARG` token holding the current value, when it exists
 */
data class GaugeParameterContext(
    val step: PsiElement,
    val dialect: GaugeDialect,
    val template: GaugeStepAdapter.StepTemplate,
    val placeholderIndex: Int,
    val prefix: String,
    val valueStartOffset: Int,
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
            val dialect = GaugeDialect.of(element)
            if (dialect == null) {
                GtpLog.info("3. not Gauge PSI (language=${element.language.id}) - nothing to do")
                return null
            }

            val step = dialect.findStep(element)
            if (step == null) {
                GtpLog.info("3. step NOT found for caret element in ${dialect.id} file - not inside a step")
                return null
            }
            GtpLog.info("3. ${dialect.id} step found: '${GaugeStepAdapter.normalize(step.text)}'")

            val arg = enclosingArg(dialect, element)

            val insideQuotes: Boolean
            when {
                arg != null -> {
                    if (!GaugeStepAdapter.isStaticArg(arg)) {
                        GtpLog.info("4. parameter found but it is a <dynamic> arg - not completable")
                        return null
                    }
                    GtpLog.info("4. static parameter found: value='${GaugeStepAdapter.staticArgValue(arg)}'")
                    insideQuotes = true
                }

                allowOutsideQuotes && GaugeStepAdapter.isStepTextToken(element) -> {
                    GtpLog.info("4. no parameter - caret is in plain step text (auto-quote path)")
                    insideQuotes = false
                }

                else -> {
                    GtpLog.info(
                        "4. no parameter and not a step text token" +
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
            val prefix = template.caretPrefix.orEmpty()
            GtpLog.info(
                "5. placeholder index=${template.caretPlaceholderIndex}" +
                    " of ${template.placeholderCount} | template='${template.text}'" +
                    " | prefix='$prefix'",
            )

            return GaugeParameterContext(
                step = step,
                dialect = dialect,
                template = template,
                placeholderIndex = template.caretPlaceholderIndex,
                prefix = prefix,
                // The value starts exactly `prefix` characters before the caret. Offsets before
                // the caret are identical in the completion copy and in the real document, so
                // this is a valid document offset for an insert handler to work with.
                valueStartOffset = caretOffset - prefix.length,
                insideQuotes = insideQuotes,
                valueLeaf = arg?.let { GaugeStepAdapter.staticArgValueLeaf(it) },
            )
        }

        /** Builds the context for the [argIndex]-th parameter of [step], caret independent. */
        fun forArg(step: PsiElement, argIndex: Int): GaugeParameterContext? {
            val dialect = GaugeDialect.ofStep(step) ?: return null
            val args = GaugeStepAdapter.argsOf(step)
            val arg = args.getOrNull(argIndex) ?: return null
            if (!GaugeStepAdapter.isStaticArg(arg)) return null
            val template = GaugeStepAdapter.buildTemplate(step)
            val leaf = GaugeStepAdapter.staticArgValueLeaf(arg)
            return GaugeParameterContext(
                step = step,
                dialect = dialect,
                template = template,
                placeholderIndex = argIndex,
                prefix = GaugeStepAdapter.staticArgValue(arg),
                valueStartOffset = leaf?.textRange?.startOffset ?: -1,
                insideQuotes = true,
                valueLeaf = leaf,
            )
        }

        /** The `SpecArg` / `ConceptArg` [element] sits in, or `null`. */
        private fun enclosingArg(dialect: GaugeDialect, element: PsiElement): PsiElement? {
            var current: PsiElement? = element
            while (current != null) {
                if (dialect.isArg(current)) return current
                if (dialect.isStep(current)) return null
                current = current.parent
            }
            return null
        }
    }
}
