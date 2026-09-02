package com.intellij.codeInsight.completion;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.patterns.ElementPattern;
public abstract class CompletionResultSet {
  public abstract void addElement(LookupElement element);
  public abstract CompletionResultSet withPrefixMatcher(PrefixMatcher matcher);
  public abstract CompletionResultSet withPrefixMatcher(String prefix);
  public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {}
  public void restartCompletionOnAnyPrefixChange() {}
  public void stopHere() {}
}
