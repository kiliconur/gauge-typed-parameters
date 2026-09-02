package com.thoughtworks.gauge.language.psi;
import com.intellij.psi.PsiElement;
public interface ConceptArg extends PsiElement {
  ConceptStaticArg getStaticArg();
  ConceptDynamicArg getDynamicArg();
}
