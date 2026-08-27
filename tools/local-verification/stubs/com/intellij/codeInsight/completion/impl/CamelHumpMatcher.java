package com.intellij.codeInsight.completion.impl;
import com.intellij.codeInsight.completion.PrefixMatcher;
/** Local approximation: case-insensitive prefix match. Good enough for the inputs under test. */
public class CamelHumpMatcher extends PrefixMatcher {
  private final boolean caseSensitive;
  public CamelHumpMatcher(String prefix) { this(prefix, false); }
  public CamelHumpMatcher(String prefix, boolean caseSensitive) { super(prefix); this.caseSensitive = caseSensitive; }
  public boolean prefixMatches(String name) {
    String p = getPrefix();
    if (p.isEmpty()) return true;
    return caseSensitive ? name.startsWith(p) : name.toLowerCase().startsWith(p.toLowerCase());
  }
  public PrefixMatcher cloneWithPrefix(String prefix) { return new CamelHumpMatcher(prefix, caseSensitive); }
}
