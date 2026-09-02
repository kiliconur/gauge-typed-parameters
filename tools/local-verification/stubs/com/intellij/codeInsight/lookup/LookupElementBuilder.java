package com.intellij.codeInsight.lookup;
import com.intellij.codeInsight.completion.InsertHandler;
public class LookupElementBuilder extends LookupElement {
  public static LookupElementBuilder create(String lookupString) { return null; }
  public static LookupElementBuilder create(Object lookupObject, String lookupString) { return null; }
  public LookupElementBuilder withTypeText(String typeText, boolean grayed) { return null; }
  public LookupElementBuilder withTailText(String tailText, boolean grayed) { return null; }
  public LookupElementBuilder withCaseSensitivity(boolean caseSensitive) { return null; }
  public LookupElementBuilder withInsertHandler(InsertHandler<LookupElement> handler) { return null; }
  public String getLookupString() { return null; }
}
