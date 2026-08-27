package com.intellij.psi.search;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
public abstract class GlobalSearchScope {
  public static GlobalSearchScope allScope(Project p) { return null; }
  public static GlobalSearchScope projectScope(Project p) { return null; }
  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(Module m, boolean tests) { return null; }
}
