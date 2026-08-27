package com.company.gauge.typed.inspection

import com.company.gauge.typed.GaugeTypedParametersBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

/**
 * Replaces the text of a Gauge static parameter with a valid value.
 *
 * The value token is a plain lexer leaf in Gauge's PSI, so the replacement is done on the
 * document (inside the write action the platform already provides) instead of building a
 * synthetic PSI element.
 */
class ReplaceParameterValueFix(private val replacement: String) : LocalQuickFix {

    override fun getFamilyName(): String = GaugeTypedParametersBundle.message("quickfix.family.name")

    override fun getName(): String = GaugeTypedParametersBundle.message("quickfix.replace.with", replacement)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        if (!element.isValid) return
        val file = element.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return

        val range = element.textRange
        document.replaceString(range.startOffset, range.endOffset, replacement)
        documentManager.commitDocument(document)
    }
}
