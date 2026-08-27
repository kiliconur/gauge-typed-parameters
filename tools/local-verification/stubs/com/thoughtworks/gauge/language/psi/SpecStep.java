package com.thoughtworks.gauge.language.psi;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import java.util.List;
public interface SpecStep extends PsiElement {
  List<SpecArg> getArgList();
  SpecTable getInlineTable();
  PsiReference getReference();
}
