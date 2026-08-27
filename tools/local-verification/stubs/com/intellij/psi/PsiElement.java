package com.intellij.psi;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
public interface PsiElement {
  ASTNode getNode();
  String getText();
  TextRange getTextRange();
  PsiElement getFirstChild();
  PsiElement getNextSibling();
  PsiElement getParent();
  PsiFile getContainingFile();
  Project getProject();
  boolean isValid();
  com.intellij.lang.Language getLanguage();
  PsiReference getReference();
}
