package com.intellij.codeInsight.completion;
import com.intellij.codeInsight.lookup.LookupElement;
public abstract class CompletionResultSet {
  public abstract void addElement(LookupElement element);
  public abstract CompletionResultSet withPrefixMatcher(PrefixMatcher matcher);
  public abstract CompletionResultSet withPrefixMatcher(String prefix);
  public void stopHere() {}
}
