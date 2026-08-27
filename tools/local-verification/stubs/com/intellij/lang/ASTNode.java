package com.intellij.lang;
import com.intellij.psi.tree.IElementType;
public interface ASTNode {
  IElementType getElementType();
  ASTNode findChildByType(IElementType type);
  com.intellij.psi.PsiElement getPsi();
  String getText();
}
