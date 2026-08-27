package com.intellij.codeInsight.completion;
import com.intellij.util.ProcessingContext;
public abstract class CompletionProvider<P extends CompletionParameters> {
  protected abstract void addCompletions(P parameters, ProcessingContext context, CompletionResultSet result);
}
