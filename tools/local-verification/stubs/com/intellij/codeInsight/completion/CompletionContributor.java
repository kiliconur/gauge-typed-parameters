package com.intellij.codeInsight.completion;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
public abstract class CompletionContributor {
  public void extend(CompletionType type, ElementPattern<? extends PsiElement> place, CompletionProvider<CompletionParameters> provider) {}
}
