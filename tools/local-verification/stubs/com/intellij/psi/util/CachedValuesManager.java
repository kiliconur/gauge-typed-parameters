package com.intellij.psi.util;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
public abstract class CachedValuesManager {
  public static CachedValuesManager getManager(Project p) { return null; }
  public abstract <T> T getCachedValue(UserDataHolder holder, Key<CachedValue<T>> key, CachedValueProvider<T> provider, boolean trackValue);
}
