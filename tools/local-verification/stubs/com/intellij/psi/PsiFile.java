package com.intellij.psi;
public interface PsiFile extends PsiNamedElement {
  PsiFile getOriginalFile();
  PsiElement findElementAt(int offset);
  String getText();
}
