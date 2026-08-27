package com.company.gauge.typed.gauge

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.thoughtworks.gauge.language.psi.SpecArg
import com.thoughtworks.gauge.language.psi.SpecStep
import com.thoughtworks.gauge.language.psi.SpecTable
import com.thoughtworks.gauge.language.token.SpecTokenTypes

/**
 * The single place in this plugin that knows about Gauge's own PSI classes
 * ([SpecStep], [SpecArg], [SpecTokenTypes], ...).
 *
 * Everything above this layer works with plain IntelliJ PSI plus the small value
 * objects declared here, so that a future incompatible change in the Gauge plugin
 * only has to be absorbed in this file (and in [GaugeStepResolver]).
 */
object GaugeStepAdapter {

    /** Fully qualified name of the Gauge Java step annotation. */
    const val STEP_ANNOTATION_FQN: String = "com.thoughtworks.gauge.Step"

    /**
     * Canonical placeholder used to compare a spec step invocation with a
     * `@Step("...")` annotation value. Matches the shape Gauge itself uses for
     * `StepValue.getStepText()`.
     */
    const val PLACEHOLDER: String = "{}"

    private val WHITESPACE = Regex("\\s+")

    /** `<name>` in a `@Step` annotation value. */
    private val ANNOTATION_PLACEHOLDER = Regex("<[^<>]*>")

    /**
     * The parameterised form of a step, e.g.
     * `* "CHROME" ile "LOGIN_BUTTON" elementine tiklanir` -> `{} ile {} elementine tiklanir`.
     *
     * @param placeholderCount number of Gauge parameters in the step
     * @param caretPlaceholderIndex 0-based index of the parameter the caret is in, or -1
     * @param caretPrefix text of the parameter from its start up to the caret, or null
     */
    data class StepTemplate(
        val text: String,
        val placeholderCount: Int,
        val caretPlaceholderIndex: Int,
        val caretPrefix: String? = null,
    )

    /** The nearest enclosing Gauge spec step, or `null` when [element] is not inside one. */
    fun findStep(element: PsiElement): SpecStep? =
        PsiTreeUtil.getParentOfType(element, SpecStep::class.java, false)

    /** All Gauge parameters of [step], static and dynamic, in source order. */
    fun argsOf(step: SpecStep): List<SpecArg> = step.argList ?: emptyList()

    /** True when [arg] is a quoted static parameter (`"value"`), false for `<dynamic>` ones. */
    fun isStaticArg(arg: SpecArg): Boolean = arg.staticArg != null

    /**
     * The `ARG` token that carries the raw text between the quotes of a static parameter.
     *
     * Returns `null` for `""` (Gauge's grammar makes the token optional) and for
     * dynamic parameters.
     */
    fun staticArgValueLeaf(arg: SpecArg): PsiElement? {
        val staticArg = arg.staticArg ?: return null
        return staticArg.node?.findChildByType(SpecTokenTypes.ARG)?.psi
    }

    /** Raw text of a static parameter, without the surrounding quotes. */
    fun staticArgValue(arg: SpecArg): String = staticArgValueLeaf(arg)?.text ?: ""

    /** True when [element] is the `ARG` token of a static parameter. */
    fun isArgValueToken(element: PsiElement): Boolean =
        element.node?.elementType == SpecTokenTypes.ARG &&
            PsiTreeUtil.getParentOfType(element, SpecArg::class.java, false) != null

    /** True when [element] is a plain text token of a step (i.e. outside any parameter). */
    fun isStepTextToken(element: PsiElement): Boolean =
        element.node?.elementType == SpecTokenTypes.STEP && element.firstChild == null

    /**
     * Builds the parameterised template of [step].
     *
     * Which child owns the caret is decided by PSI ancestry, never by offset containment:
     * [com.intellij.openapi.util.TextRange.containsOffset] is inclusive at both ends, so a caret
     * sitting right after a closing quote is "contained" in that parameter *and* in the step text
     * that follows it. Using the caret leaf removes that ambiguity.
     *
     * @param caretElement the leaf element under the caret, or `null` when no caret is involved
     * @param caretOffset absolute offset of the caret; only read when [caretElement] is given
     * @param treatCaretWordAsParameter when true and the caret sits in plain step text, the
     *        whitespace-delimited word under the caret is treated as if it were a parameter.
     *        This powers the optional "auto quote" completion.
     */
    fun buildTemplate(
        step: SpecStep,
        caretElement: PsiElement? = null,
        caretOffset: Int = -1,
        treatCaretWordAsParameter: Boolean = false,
    ): StepTemplate {
        val sb = StringBuilder()
        var count = 0
        var caretIndex = -1
        var caretPrefix: String? = null

        var next: PsiElement? = step.firstChild
        while (next != null) {
            val child: PsiElement = next
            next = child.nextSibling
            val type = child.node?.elementType

            if (child is SpecArg) {
                if (ownsCaret(child, caretElement)) {
                    caretIndex = count
                    caretPrefix = argPrefix(child, caretOffset)
                }
                sb.append(PLACEHOLDER)
                count++
                continue
            }

            if (child is SpecTable) {
                // An inline table is passed to the implementation as a trailing parameter.
                sb.append(' ').append(PLACEHOLDER)
                count++
                continue
            }

            when (type) {
                SpecTokenTypes.STEP_IDENTIFIER, SpecTokenTypes.COMMENT -> continue
                SpecTokenTypes.NEW_LINE -> {
                    sb.append(' ')
                    continue
                }
            }

            val word = if (treatCaretWordAsParameter && caretIndex < 0 && ownsCaret(child, caretElement)) {
                wordAroundCaret(child.text, caretOffset - child.textRange.startOffset)
            } else {
                null
            }

            if (word == null) {
                sb.append(stripDummy(child.text))
            } else {
                sb.append(word.textBefore).append(PLACEHOLDER).append(word.textAfter)
                caretIndex = count
                caretPrefix = word.prefix
                count++
            }
        }

        return StepTemplate(normalize(sb.toString()), count, caretIndex, caretPrefix)
    }

    /** True when [caretElement] is [child] itself or lives inside it. */
    private fun ownsCaret(child: PsiElement, caretElement: PsiElement?): Boolean =
        caretElement != null && PsiTreeUtil.isAncestor(child, caretElement, false)

    /** Converts a `@Step` annotation value into the same canonical template form. */
    fun templateFromAnnotationValue(value: String): StepTemplate {
        val replaced = value.replace(ANNOTATION_PLACEHOLDER, PLACEHOLDER)
        val count = ANNOTATION_PLACEHOLDER.findAll(value).count()
        return StepTemplate(normalize(replaced), count, -1)
    }

    /** Text of a static parameter from its opening quote up to [caretOffset]. */
    private fun argPrefix(arg: SpecArg, caretOffset: Int): String {
        val leaf = staticArgValueLeaf(arg) ?: return ""
        val local = caretOffset - leaf.textRange.startOffset
        if (local <= 0) return ""
        val raw = leaf.text
        val safe = local.coerceAtMost(raw.length)
        return stripDummy(raw.substring(0, safe))
    }

    private data class CaretWord(val textBefore: String, val textAfter: String, val prefix: String)

    /**
     * Splits [text] around the whitespace-delimited word that contains the caret at [local].
     * Returns `null` when there is no word there (caret sits on whitespace).
     */
    private fun wordAroundCaret(text: String, local: Int): CaretWord? {
        val dummyLength = dummyLengthAt(text, local)
        val cleaned = if (dummyLength > 0) text.removeRange(local, local + dummyLength) else stripDummy(text)
        val caret = local.coerceIn(0, cleaned.length)

        var start = caret
        while (start > 0 && !cleaned[start - 1].isWhitespace()) start--
        var end = caret
        while (end < cleaned.length && !cleaned[end].isWhitespace()) end++

        if (start == end) return null
        return CaretWord(
            textBefore = cleaned.substring(0, start),
            textAfter = cleaned.substring(end),
            prefix = cleaned.substring(start, caret),
        )
    }

    private fun dummyLengthAt(text: String, local: Int): Int = when {
        local < 0 || local > text.length -> 0
        text.startsWith(CompletionUtilCore.DUMMY_IDENTIFIER, local) -> CompletionUtilCore.DUMMY_IDENTIFIER.length
        text.startsWith(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, local) ->
            CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED.length
        else -> 0
    }

    fun stripDummy(text: String): String =
        if (text.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
            text.replace(CompletionUtilCore.DUMMY_IDENTIFIER, "")
                .replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")
        } else {
            text
        }

    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim()
}
