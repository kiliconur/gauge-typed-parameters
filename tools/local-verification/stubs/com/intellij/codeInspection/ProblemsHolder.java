package com.intellij.codeInspection;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
public class ProblemsHolder {
  public Project getProject() { return null; }
  public void registerProblem(PsiElement psiElement, String descriptionTemplate, ProblemHighlightType highlightType, LocalQuickFix... fixes) {}
}
