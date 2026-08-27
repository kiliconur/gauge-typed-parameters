package com.intellij.psi;
public abstract class PsiElementVisitor {
  public static final PsiElementVisitor EMPTY_VISITOR = new PsiElementVisitor() {};
  public void visitElement(PsiElement element) {}
  public void visitFile(PsiFile file) {}
}
