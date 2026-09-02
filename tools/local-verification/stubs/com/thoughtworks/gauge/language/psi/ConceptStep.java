package com.thoughtworks.gauge.language.psi;
import com.intellij.psi.PsiElement;
import java.util.List;
public interface ConceptStep extends PsiElement {
  List<ConceptArg> getArgList();
  ConceptTable getTable();
}
