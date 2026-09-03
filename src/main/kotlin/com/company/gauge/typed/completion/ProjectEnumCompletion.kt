package com.company.gauge.typed.completion

import com.company.gauge.typed.GtpLog
import com.company.gauge.typed.enums.ProjectEnumBrowser
import com.company.gauge.typed.enums.ProjectEnumCandidates
import com.company.gauge.typed.enums.ProjectEnumStage
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.company.gauge.typed.gauge.GaugeParameterContext

/**
 * Renders the two stages of the project enum browser into a completion result set.
 *
 * Offered on `String` parameters, as assistance only - the value stays free text and is never
 * validated. Stage 1 offers enum CLASS names (`PageItems`, `PageItems2`, ...), stage 2 - after
 * the user typed `PageItems2.` - offers only that class's constants. The class name is a
 * temporary browsing namespace: selecting a constant replaces `PageItems2.LO` with
 * `LOGIN_BUTTON`, so the Gauge file never keeps the class name.
 */
internal class ProjectEnumCompletionProvider(private val browser: ProjectEnumBrowser) {

    /** @return true when candidates were added. */
    fun addCompletions(
        parameters: CompletionParameters,
        result: CompletionResultSet,
        context: GaugeParameterContext,
    ): Boolean {
        val anchor = parameters.originalFile
        val stage = ProjectEnumStage.parse(context.prefix)
        val preferred = when (stage) {
            is ProjectEnumStage.Constant ->
                ProjectEnumSelectionContext.preferred(
                    parameters.editor,
                    stage.className.substringAfterLast('.'),
                )

            is ProjectEnumStage.ClassName -> null
        }

        return when (val candidates = browser.candidatesFor(anchor, context.prefix, preferred)) {
            is ProjectEnumCandidates.Classes -> addClassNames(result, context, candidates)
            is ProjectEnumCandidates.Constants -> addConstants(result, context, candidates)
            ProjectEnumCandidates.None -> false
        }
    }

    private fun addClassNames(
        result: CompletionResultSet,
        context: GaugeParameterContext,
        candidates: ProjectEnumCandidates.Classes,
    ): Boolean {
        // No match-all fallback here, unlike the closed-type completion: the parameter is free
        // text, so text that matches no enum class name is simply the user writing something
        // else ("custom value", "abc123") and must not pop up the whole enum catalogue.
        val matcher = GaugeValuePrefixMatcher(candidates.prefix, matchAll = candidates.prefix.isEmpty())
        val matching = candidates.classes.filter { it.name?.let(matcher::prefixMatches) == true }
        if (matching.isEmpty()) {
            GtpLog.info(
                "10. Stage1 no enum class matches '${candidates.prefix}'" +
                    " (${candidates.classes.size} in the project) - free text, nothing offered",
            )
            return false
        }

        val out = result.withPrefixMatcher(matcher)

        for (psiClass in matching) {
            val name = psiClass.name ?: continue
            // Package/type information goes into the presentation only - never into the
            // inserted text, which stays the bare class name.
            val element = LookupElementBuilder.create(psiClass, name)
                .withTypeText("enum", true)
                .withTailText("  " + packageOf(psiClass), true)
                .withInsertHandler(EnumClassInsertHandler(psiClass, wrapInQuotes = !context.insideQuotes))
            out.addElement(PrioritizedLookupElement.withPriority(element, CLASS_PRIORITY))
        }

        // Typing the dot switches the browser to stage 2; without a restart the lookup would
        // just keep filtering class names by "PageItems2." and show nothing.
        out.restartCompletionOnAnyPrefixChange()

        GtpLog.info(
            "10. Stage1 offered ${matching.size} enum class name(s)" +
                " of ${candidates.classes.size} | prefix='${candidates.prefix}'",
        )
        return true
    }

    private fun addConstants(
        result: CompletionResultSet,
        context: GaugeParameterContext,
        candidates: ProjectEnumCandidates.Constants,
    ): Boolean {
        val matcher = GaugeQualifiedValuePrefixMatcher.forCandidates(context.prefix, candidates.names)
        val out = result.withPrefixMatcher(matcher)
        val ownerName = candidates.owner.name

        for (constant in candidates.names) {
            val element = LookupElementBuilder.create(constant)
                .withTypeText(ownerName, true)
                .withInsertHandler(
                    ProjectEnumConstantInsertHandler(
                        valueStartOffset = context.valueStartOffset,
                        constant = constant,
                        wrapInQuotes = !context.insideQuotes,
                    ),
                )
            out.addElement(PrioritizedLookupElement.withPriority(element, CONSTANT_PRIORITY))
        }

        GtpLog.info(
            "10. Stage2 offered ${candidates.names.size} constant(s) of ${candidates.owner.qualifiedName}" +
                " | prefix='${context.prefix}' | matcher='${matcher.prefix}'",
        )
        return true
    }

    /** `com.foo.web.PageItems` -> `com.foo.web`; nested enums keep their outer class. */
    private fun packageOf(psiClass: PsiClass): String {
        val qualified = psiClass.qualifiedName ?: return ""
        val name = psiClass.name ?: return ""
        return qualified.removeSuffix(".$name")
    }

    private companion object {
        const val CLASS_PRIORITY = 100.0
        const val CONSTANT_PRIORITY = 100.0
    }
}

/**
 * Prefix matcher for stage 2.
 *
 * The inherited [prefix] is the WHOLE typed text (`PageItems2.LO`), because that is what
 * defines the document range a selected lookup element replaces - which is exactly the
 * behaviour the feature needs: the temporary `PageItems2.` namespace disappears together with
 * the typed constant prefix. Filtering, on the other hand, only ever looks at the part after
 * the dot.
 */
class GaugeQualifiedValuePrefixMatcher(
    fullPrefix: String,
    private val matchAll: Boolean,
) : PrefixMatcher(fullPrefix) {

    private val delegate = CamelHumpMatcher(fullPrefix.substringAfterLast('.'), false)

    override fun prefixMatches(name: String): Boolean = matchAll || delegate.prefixMatches(name)

    override fun isStartMatch(name: String): Boolean = matchAll || delegate.isStartMatch(name)

    override fun cloneWithPrefix(prefix: String): PrefixMatcher =
        if (prefix == this.prefix) this else GaugeQualifiedValuePrefixMatcher(prefix, matchAll)

    companion object {
        /** Falls back to "show everything" when the typed suffix matches no constant (TEST 7). */
        @JvmStatic
        fun forCandidates(
            fullPrefix: String,
            candidates: Collection<String>,
        ): GaugeQualifiedValuePrefixMatcher {
            val probe = CamelHumpMatcher(fullPrefix.substringAfterLast('.'), false)
            val anyMatch = candidates.any { probe.prefixMatches(it) }
            return GaugeQualifiedValuePrefixMatcher(fullPrefix, !anyMatch)
        }
    }
}

/**
 * Remembers, per editor, which enum class the user picked in stage 1.
 *
 * Two project enums can share a short name (`com.foo.web.PageItems` and
 * `com.foo.mobile.PageItems`). Stage 2 must not guess between them - but when the user picked
 * one from the stage 1 list moments ago, that choice is known and no guessing is involved.
 * Deliberately transient: it lives on the editor, holds a [SmartPsiElementPointer], and is
 * dropped as soon as a constant has been inserted.
 */
internal object ProjectEnumSelectionContext {

    private val KEY = Key.create<Selection>("gauge.typed.parameters.genericEnumSelection")

    private class Selection(val shortName: String, val pointer: SmartPsiElementPointer<PsiClass>)

    fun remember(editor: Editor?, psiClass: PsiClass) {
        val name = psiClass.name ?: return
        val target = editor ?: return
        val pointer = SmartPointerManager.getInstance(psiClass.project)
            .createSmartPsiElementPointer(psiClass)
        target.putUserData(KEY, Selection(name, pointer))
        GtpLog.info("Stage1 selected class=${psiClass.qualifiedName}")
    }

    fun preferred(editor: Editor?, shortName: String): PsiClass? {
        val selection = editor?.getUserData(KEY) ?: return null
        if (selection.shortName != shortName) return null
        val psiClass = selection.pointer.element ?: return null
        return psiClass.takeIf { it.isValid && it.name == shortName }
    }

    fun clear(editor: Editor?) {
        editor?.putUserData(KEY, null)
    }
}

/** Stage 1 insertion: leaves `PageItems2` in the file and remembers which class that was. */
internal class EnumClassInsertHandler(
    private val psiClass: PsiClass,
    private val wrapInQuotes: Boolean,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        if (wrapInQuotes) {
            val caret = GaugeQuotes.wrap(context.document, context.startOffset, context.tailOffset)
            if (caret >= 0) {
                context.editor.caretModel.moveToOffset(caret)
                context.commitDocument()
            }
        }
        ProjectEnumSelectionContext.remember(context.editor, psiClass)
    }
}

/**
 * Stage 2 insertion: replaces the WHOLE temporary value - `PageItems2.LO` - with the selected
 * constant, so the Gauge parameter ends up as `"LOGIN_BUTTON"`, never
 * `"PageItems2.LOGIN_BUTTON"` and never `"PageItems2.LOLOGIN_BUTTON"`.
 *
 * The range is calculated explicitly from the value start recorded when completion was invoked;
 * it is idempotent with whatever the platform already replaced through the prefix matcher.
 */
internal class ProjectEnumConstantInsertHandler(
    private val valueStartOffset: Int,
    private val constant: String,
    private val wrapInQuotes: Boolean,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val start = minOf(valueStartOffset, context.startOffset).coerceAtLeast(0)
        val end = context.tailOffset.coerceAtMost(document.textLength)
        if (valueStartOffset < 0 || start > end) return

        GtpLog.info("Stage2 replacing range $start..$end with $constant")
        document.replaceString(start, end, constant)

        var caret = start + constant.length
        if (wrapInQuotes) {
            val wrapped = GaugeQuotes.wrap(document, start, caret)
            if (wrapped >= 0) caret = wrapped
        }
        context.editor.caretModel.moveToOffset(caret)
        context.commitDocument()
        ProjectEnumSelectionContext.clear(context.editor)
    }
}

/** Shared auto-quote helper for values completed outside of an existing `"..."` parameter. */
internal object GaugeQuotes {

    /**
     * Wraps `[start, end)` in Gauge's quotes unless they are already there.
     *
     * @return the caret offset after the closing quote, or -1 when nothing was changed
     */
    fun wrap(document: Document, start: Int, end: Int): Int {
        if (start < 0 || end > document.textLength || start > end) return -1
        val text = document.charsSequence
        val alreadyQuoted = start > 0 && text[start - 1] == '"' &&
            end < document.textLength && text[end] == '"'
        if (alreadyQuoted) return -1

        document.insertString(end, "\"")
        document.insertString(start, "\"")
        return end + 2
    }
}
