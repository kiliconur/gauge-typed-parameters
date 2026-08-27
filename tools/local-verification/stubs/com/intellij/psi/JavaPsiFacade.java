package com.intellij.psi;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
public abstract class JavaPsiFacade {
  public static JavaPsiFacade getInstance(Project p) { return null; }
  public abstract PsiClass findClass(String qualifiedName, GlobalSearchScope scope);
}
