package com.intellij.psi.util;
import com.intellij.psi.PsiElement;
import java.util.List;
public class PsiTreeUtil {
  public static boolean isAncestor(PsiElement ancestor, PsiElement element, boolean strict) { return false; }
  public static <T extends PsiElement> T getParentOfType(PsiElement element, Class<T> cls, boolean strict) { return null; }
  public static <T extends PsiElement> List<T> getChildrenOfTypeAsList(PsiElement element, Class<T> cls) { return null; }
}
