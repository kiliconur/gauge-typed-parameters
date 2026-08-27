package com.intellij.codeInsight.lookup;
import com.intellij.codeInsight.completion.InsertHandler;
public class LookupElementBuilder extends LookupElement {
  public static LookupElementBuilder create(String lookupString) { return null; }
  public LookupElementBuilder withTypeText(String typeText, boolean grayed) { return null; }
  public LookupElementBuilder withCaseSensitivity(boolean caseSensitive) { return null; }
  public LookupElementBuilder withInsertHandler(InsertHandler<LookupElement> handler) { return null; }
  public String getLookupString() { return null; }
}
