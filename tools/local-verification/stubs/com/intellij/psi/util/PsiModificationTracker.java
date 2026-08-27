package com.intellij.psi.util;
import com.intellij.openapi.util.Key;
public interface PsiModificationTracker {
  Key MODIFICATION_COUNT = Key.create("MODIFICATION_COUNT");
}
