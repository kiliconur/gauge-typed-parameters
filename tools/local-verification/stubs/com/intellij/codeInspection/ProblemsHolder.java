package com.intellij.codeInspection;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
public class ProblemsHolder {
  public Project getProject() { return null; }
  public PsiFile getFile() { return null; }
  public void registerProblem(PsiElement psiElement, String descriptionTemplate, ProblemHighlightType highlightType, LocalQuickFix... fixes) {}
}
