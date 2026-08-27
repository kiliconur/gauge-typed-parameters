package com.intellij.codeInsight.completion;
public abstract class PrefixMatcher {
  protected final String myPrefix;
  protected PrefixMatcher(String prefix) { myPrefix = prefix; }
  public abstract boolean prefixMatches(String name);
  public boolean isStartMatch(String name) { return prefixMatches(name); }
  public final String getPrefix() { return myPrefix; }
  public abstract PrefixMatcher cloneWithPrefix(String prefix);
}
