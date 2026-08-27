package com.company.gauge.typed.completion

import com.company.gauge.typed.GtpLog
import com.company.gauge.typed.gauge.GaugeParameterContext
import com.company.gauge.typed.model.TypedParameterResolver
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.thoughtworks.gauge.language.Specification

/**
 * Offers values for Gauge step parameters based on the Java type of the corresponding
 * step implementation parameter.
 *
 * The Gauge spec syntax is untouched: candidates are inserted as plain text inside the
 * existing `"..."` parameter.
 */
class GaugeTypedParameterCompletionContributor : CompletionContributor() {

    init {
        // If this line is absent from idea.log the extension was never registered, i.e. the
        // plugin descriptor did not load - not a resolution problem.
        GtpLog.info("contributor constructed - extension registered")
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(Specification.INSTANCE),
            TypedParameterCompletionProvider(),
        )
    }

    private class TypedParameterCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            try {
                doAddCompletions(parameters, result)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: IndexNotReadyException) {
                // Indexing started mid-completion - simply offer nothing.
                GtpLog.info("aborted: index not ready mid-completion")
            } catch (e: Throwable) {
                GtpLog.warn("completion failed with an exception", e)
            }
        }

        private fun doAddCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
            val position = parameters.position
            val project = position.project
            GtpLog.info(
                "1/2. invoked | psi=${position.javaClass.name}" +
                    " | elementType=${position.node?.elementType}" +
                    " | language=${position.language.id}" +
                    " | file=${position.containingFile?.name}" +
                    " | offset=${parameters.offset}",
            )
            if (project.isDisposed) {
                GtpLog.info("aborted: project disposed")
                return
            }
            if (DumbService.isDumb(project)) {
                GtpLog.info("aborted: project is indexing (dumb mode) - retry after indexing")
                return
            }

            val parameterContext = GaugeParameterContext.atCaret(
                element = position,
                caretOffset = parameters.offset,
                allowOutsideQuotes = true,
            )
            if (parameterContext == null) {
                GtpLog.info("aborted: caret is not in a completable Gauge parameter")
                return
            }

            val resolved = TypedParameterResolver.resolve(project, parameterContext)
            if (resolved == null) {
                GtpLog.info("aborted: no typed parameter resolved (see stages above)")
                return
            }

            val provider = PROVIDERS.firstOrNull { it.supports(resolved.kind) }
            if (provider == null) {
                GtpLog.info("10. no candidates: kind ${resolved.kind} has no completion provider")
                return
            }
            val values = provider.values(resolved.kind)
            if (values.isEmpty()) {
                GtpLog.info("10. no candidates: provider returned none for ${resolved.kind}")
                return
            }

            val matcher = GaugeValuePrefixMatcher.forCandidates(
                parameterContext.prefix,
                values.map { it.value },
            )
            val typedResult = result.withPrefixMatcher(matcher)

            for (value in values) {
                typedResult.addElement(lookupElement(value, parameterContext.insideQuotes))
            }
            GtpLog.info(
                "10. added ${values.size} candidate(s): ${values.joinToString(", ") { it.value }}" +
                    " | prefix='${parameterContext.prefix}' | matcher='${matcher.prefix}'",
            )

            // The Java type is known and closed: nothing a later contributor could add is a
            // valid value here. (Contributors that already ran - Gauge's own static argument
            // provider among them - are unaffected.)
            if (parameterContext.insideQuotes) {
                result.stopHere()
            }
        }

        private fun lookupElement(value: TypedValue, insideQuotes: Boolean): LookupElement {
            // Case sensitivity stays ON: matching is case-insensitive (see GaugeValuePrefixMatcher)
            // but the inserted text must be the constant exactly as declared - typing "lo" and
            // picking LOGIN_BUTTON must not insert "login_button".
            var builder = LookupElementBuilder.create(value.value)
            if (value.typeText != null) {
                builder = builder.withTypeText(value.typeText, true)
            }
            if (!insideQuotes) {
                builder = builder.withInsertHandler(QuoteWrappingInsertHandler)
            }
            return PrioritizedLookupElement.withPriority(builder, value.priority)
        }

        private companion object {
            val PROVIDERS: List<TypedValueProvider> =
                listOf(EnumCompletionProvider(), BooleanCompletionProvider())
        }
    }

    /**
     * Optional auto-quote support: when the value was typed outside quotes and the position
     * could still be identified as a parameter, wrap the inserted value in the quotes Gauge
     * expects, turning `* LO<caret> elementine tiklanir` into
     * `* "LOGIN_BUTTON"<caret> elementine tiklanir`.
     */
    private object QuoteWrappingInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val document = context.document
            val start = context.startOffset
            val end = context.tailOffset
            if (start < 0 || end > document.textLength || start > end) return

            val alreadyQuoted =
                start > 0 && document.charsSequence[start - 1] == '"' &&
                    end < document.textLength && document.charsSequence[end] == '"'
            if (alreadyQuoted) return

            document.insertString(end, "\"")
            document.insertString(start, "\"")
            context.editor.caretModel.moveToOffset(end + 2)
            context.commitDocument()
        }
    }
}
