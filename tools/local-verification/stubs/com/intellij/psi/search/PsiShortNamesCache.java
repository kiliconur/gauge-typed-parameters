package com.intellij.psi.search;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
public abstract class PsiShortNamesCache {
  public static PsiShortNamesCache getInstance(Project project) { return null; }
  public abstract PsiClass[] getClassesByName(String name, GlobalSearchScope scope);
  public abstract String[] getAllClassNames();
}
