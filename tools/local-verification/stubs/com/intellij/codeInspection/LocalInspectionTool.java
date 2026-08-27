package com.intellij.codeInspection;
import com.intellij.psi.PsiElementVisitor;
public abstract class LocalInspectionTool extends InspectionProfileEntry {
  public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) { return PsiElementVisitor.EMPTY_VISITOR; }
}
