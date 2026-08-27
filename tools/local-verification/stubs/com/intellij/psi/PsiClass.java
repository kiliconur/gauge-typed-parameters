package com.intellij.psi;
public interface PsiClass extends PsiNamedElement {
  boolean isEnum();
  String getQualifiedName();
  PsiField[] getFields();
}
