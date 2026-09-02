package com.company.gauge.typed.gauge

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.psi.PsiElement

/**
 * The single place in this plugin that knows about Gauge's own PSI
 * (through [GaugeDialect], which holds the `Spec*` and `Concept*` classes and token types).
 *
 * Everything above this layer works with plain IntelliJ PSI plus the small value objects
 * declared here, so that a future incompatible change in the Gauge plugin only has to be
 * absorbed in these two files (and in [GaugeStepResolver]).
 *
 * Every function accepts a step / parameter from either dialect: `.spec` and `.cpt` share
 * one pipeline.
 */
object GaugeStepAdapter {

    /** Fully qualified name of the Gauge Java step annotation. */
    const val STEP_ANNOTATION_FQN: String = "com.thoughtworks.gauge.Step"

    /**
     * Canonical placeholder used to compare a step invocation with a `@Step("...")` annotation
     * value. Matches the shape Gauge itself uses for `StepValue.getStepText()`.
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

    /** The nearest enclosing Gauge step (`.spec` or `.cpt`), or `null`. */
    fun findStep(element: PsiElement): PsiElement? = GaugeDialect.of(element)?.findStep(element)

    /** All Gauge parameters of [step], static and dynamic, in source order. */
    fun argsOf(step: PsiElement): List<PsiElement> =
        GaugeDialect.ofStep(step)?.argsOf(step) ?: emptyList()

    /** True when [arg] is a quoted static parameter (`"value"`), false for `<dynamic>` ones. */
    fun isStaticArg(arg: PsiElement): Boolean =
        GaugeDialect.of(arg)?.staticArgOf(arg) != null

    /**
     * The `ARG` token that carries the raw text between the quotes of a static parameter.
     *
     * Returns `null` for `""` (Gauge's grammar makes the token optional) and for dynamic
     * parameters.
     */
    fun staticArgValueLeaf(arg: PsiElement): PsiElement? {
        val dialect = GaugeDialect.of(arg) ?: return null
        val staticArg = dialect.staticArgOf(arg) ?: return null
        return staticArg.node?.findChildByType(dialect.argToken)?.psi
    }

    /** Raw text of a static parameter, without the surrounding quotes. */
    fun staticArgValue(arg: PsiElement): String = staticArgValueLeaf(arg)?.text ?: ""

    /** True when [element] is a plain text token of a step (i.e. outside any parameter). */
    fun isStepTextToken(element: PsiElement): Boolean {
        val dialect = GaugeDialect.of(element) ?: return false
        return element.node?.elementType == dialect.stepTextToken && element.firstChild == null
    }

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
        step: PsiElement,
        caretElement: PsiElement? = null,
        caretOffset: Int = -1,
        treatCaretWordAsParameter: Boolean = false,
    ): StepTemplate {
        val dialect = GaugeDialect.ofStep(step) ?: GaugeDialect.of(step)
            ?: return StepTemplate("", 0, -1)

        val sb = StringBuilder()
        var count = 0
        var caretIndex = -1
        var caretPrefix: String? = null

        var next: PsiElement? = step.firstChild
        while (next != null) {
            val child: PsiElement = next
            next = child.nextSibling
            val type = child.node?.elementType

            if (dialect.isArg(child)) {
                if (ownsCaret(child, caretElement)) {
                    caretIndex = count
                    caretPrefix = argPrefix(child, caretOffset)
                }
                sb.append(PLACEHOLDER)
                count++
                continue
            }

            if (dialect.isTable(child)) {
                // An inline table is passed to the implementation as a trailing parameter.
                sb.append(' ').append(PLACEHOLDER)
                count++
                continue
            }

            if (type == dialect.stepIdentifierToken || type == dialect.commentToken) continue
            if (type == dialect.newLineToken) {
                sb.append(' ')
                continue
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
    private fun ownsCaret(child: PsiElement, caretElement: PsiElement?): Boolean {
        if (caretElement == null) return false
        var current: PsiElement? = caretElement
        while (current != null) {
            if (current === child) return true
            current = current.parent
        }
        return false
    }

    /** Converts a `@Step` annotation value into the same canonical template form. */
    fun templateFromAnnotationValue(value: String): StepTemplate {
        val replaced = value.replace(ANNOTATION_PLACEHOLDER, PLACEHOLDER)
        val count = ANNOTATION_PLACEHOLDER.findAll(value).count()
        return StepTemplate(normalize(replaced), count, -1)
    }

    /** Text of a static parameter from its opening quote up to [caretOffset]. */
    private fun argPrefix(arg: PsiElement, caretOffset: Int): String {
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
