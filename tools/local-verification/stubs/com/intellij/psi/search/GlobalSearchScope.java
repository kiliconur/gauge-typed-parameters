package com.intellij.psi.search;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
public abstract class GlobalSearchScope implements SearchScope {
  public static GlobalSearchScope allScope(Project p) { return null; }
  public static GlobalSearchScope projectScope(Project p) { return null; }
  public static GlobalSearchScope moduleScope(Module m) { return null; }
  public static GlobalSearchScope moduleWithDependenciesScope(Module m) { return null; }
  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(Module m, boolean tests) { return null; }
  public GlobalSearchScope intersectWith(GlobalSearchScope scope) { return null; }
}
