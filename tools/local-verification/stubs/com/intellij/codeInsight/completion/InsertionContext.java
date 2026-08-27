package com.intellij.codeInsight.completion;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
public class InsertionContext {
  public Document getDocument() { return null; }
  public Editor getEditor() { return null; }
  public int getStartOffset() { return 0; }
  public int getTailOffset() { return 0; }
  public void commitDocument() {}
}
