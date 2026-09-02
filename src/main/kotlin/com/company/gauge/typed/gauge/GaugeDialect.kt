package com.company.gauge.typed.gauge

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.thoughtworks.gauge.language.Concept
import com.thoughtworks.gauge.language.Specification
import com.thoughtworks.gauge.language.psi.ConceptArg
import com.thoughtworks.gauge.language.psi.ConceptStep
import com.thoughtworks.gauge.language.psi.ConceptTable
import com.thoughtworks.gauge.language.psi.SpecArg
import com.thoughtworks.gauge.language.psi.SpecStep
import com.thoughtworks.gauge.language.psi.SpecTable
import com.thoughtworks.gauge.language.token.ConceptTokenTypes
import com.thoughtworks.gauge.language.token.SpecTokenTypes

/**
 * The two Gauge languages this plugin understands: `.spec` files (`Specification`) and
 * `.cpt` concept files (`Concept`).
 *
 * Gauge models both with two structurally identical PSI hierarchies - `SpecStep`/`SpecArg`/
 * `SpecStaticArg` on one side, `ConceptStep`/`ConceptArg`/`ConceptStaticArg` on the other -
 * generated from two grammars that share the same `step ::= STEP_IDENTIFIER (arg|STEP)+ table?`
 * shape. Gauge itself exploits that: `ConceptReference.resolve()` wraps the concept step's AST
 * node in a `SpecStepImpl` and hands it to `StepUtil.findStepImpl`.
 *
 * This class is that same observation expressed once, so everything above
 * [GaugeStepAdapter] works on plain [PsiElement]s and has no idea which file type it is in.
 */
sealed class GaugeDialect(
    val id: String,
    val language: Language,
    private val stepClass: Class<out PsiElement>,
    private val argClass: Class<out PsiElement>,
    private val tableClass: Class<out PsiElement>,
    /** The token that carries the raw text between the quotes of a static parameter. */
    val argToken: IElementType,
    /** The token that carries plain (non-parameter) step text. */
    val stepTextToken: IElementType,
    val stepIdentifierToken: IElementType,
    val commentToken: IElementType,
    val newLineToken: IElementType,
) {

    /** The `staticArg` child of a parameter, or `null` for `<dynamic>` parameters. */
    abstract fun staticArgOf(arg: PsiElement): PsiElement?

    fun isStep(element: PsiElement): Boolean = stepClass.isInstance(element)

    fun isArg(element: PsiElement): Boolean = argClass.isInstance(element)

    fun isTable(element: PsiElement): Boolean = tableClass.isInstance(element)

    /** The nearest enclosing step invocation, or `null` when [element] is not inside one. */
    fun findStep(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (isStep(current)) return current
            current = current.parent
        }
        return null
    }

    /** All parameters of [step], static and dynamic, in source order. */
    fun argsOf(step: PsiElement): List<PsiElement> {
        val result = ArrayList<PsiElement>(4)
        var child: PsiElement? = step.firstChild
        while (child != null) {
            if (isArg(child)) result.add(child)
            child = child.nextSibling
        }
        return result
    }

    /** `.spec` files. */
    object Spec : GaugeDialect(
        id = "spec",
        language = Specification.INSTANCE,
        stepClass = SpecStep::class.java,
        argClass = SpecArg::class.java,
        tableClass = SpecTable::class.java,
        argToken = SpecTokenTypes.ARG,
        stepTextToken = SpecTokenTypes.STEP,
        stepIdentifierToken = SpecTokenTypes.STEP_IDENTIFIER,
        commentToken = SpecTokenTypes.COMMENT,
        newLineToken = SpecTokenTypes.NEW_LINE,
    ) {
        override fun staticArgOf(arg: PsiElement): PsiElement? = (arg as? SpecArg)?.staticArg
    }

    /** `.cpt` concept files. */
    object Cpt : GaugeDialect(
        id = "cpt",
        language = Concept.INSTANCE,
        stepClass = ConceptStep::class.java,
        argClass = ConceptArg::class.java,
        tableClass = ConceptTable::class.java,
        argToken = ConceptTokenTypes.ARG,
        stepTextToken = ConceptTokenTypes.STEP,
        stepIdentifierToken = ConceptTokenTypes.STEP_IDENTIFIER,
        commentToken = ConceptTokenTypes.COMMENT,
        newLineToken = ConceptTokenTypes.NEW_LINE,
    ) {
        override fun staticArgOf(arg: PsiElement): PsiElement? = (arg as? ConceptArg)?.staticArg
    }

    companion object {

        private val ALL = listOf(Spec, Cpt)

        /** The dialect [element] belongs to, or `null` when it is not Gauge PSI at all. */
        fun of(element: PsiElement): GaugeDialect? {
            forLanguage(element.language)?.let { return it }
            val fileLanguage = element.containingFile?.language ?: return null
            return forLanguage(fileLanguage)
        }

        /** The dialect that owns [step], based on its PSI class rather than its language. */
        fun ofStep(step: PsiElement): GaugeDialect? = ALL.firstOrNull { it.isStep(step) }

        /** True when [element] is a step invocation in either dialect. */
        fun isStepElement(element: PsiElement): Boolean = ofStep(element) != null

        private fun forLanguage(language: Language): GaugeDialect? =
            ALL.firstOrNull { it.language.`is`(language) }
    }
}
