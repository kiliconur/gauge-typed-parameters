package com.intellij.psi;
public interface PsiFile extends PsiElement {
  PsiFile getOriginalFile();
  PsiElement findElementAt(int offset);
  String getText();
}
