package com.intellij.codeInspection;
public interface LocalQuickFix extends QuickFix<ProblemDescriptor> {
  default boolean startInWriteAction() { return true; }
}
