package com.intellij.psi;
public interface PsiParameterList extends PsiElement {
  PsiParameter[] getParameters();
  int getParametersCount();
}
