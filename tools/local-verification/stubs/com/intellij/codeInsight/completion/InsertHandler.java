package com.intellij.codeInsight.completion;
import com.intellij.codeInsight.lookup.LookupElement;
public interface InsertHandler<T extends LookupElement> {
  void handleInsert(InsertionContext context, T item);
}
