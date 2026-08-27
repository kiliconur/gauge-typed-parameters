package com.thoughtworks.gauge.language.psi;
import com.intellij.psi.PsiElement;
public interface SpecArg extends PsiElement {
  SpecDynamicArg getDynamicArg();
  SpecStaticArg getStaticArg();
}
