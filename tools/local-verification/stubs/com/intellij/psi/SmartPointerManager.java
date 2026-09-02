package com.intellij.psi;
import com.intellij.openapi.project.Project;
public abstract class SmartPointerManager {
  public static SmartPointerManager getInstance(Project project) { return null; }
  public abstract <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(E element);
}
