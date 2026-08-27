package com.intellij.psi.search.searches;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Collection;
public class AnnotatedElementsSearch {
  public static Query<PsiMethod> searchPsiMethods(PsiClass ann, GlobalSearchScope scope) { return null; }
  public interface Query<T> { Collection<T> findAll(); }
}
