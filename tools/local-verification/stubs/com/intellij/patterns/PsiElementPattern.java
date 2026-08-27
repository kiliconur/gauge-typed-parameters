package com.intellij.patterns;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
public class PsiElementPattern<T extends PsiElement, Self extends PsiElementPattern<T, Self>> implements ElementPattern<T> {
  public Self withLanguage(Language language) { return null; }
  public static class Capture<T extends PsiElement> extends PsiElementPattern<T, Capture<T>> {}
}
