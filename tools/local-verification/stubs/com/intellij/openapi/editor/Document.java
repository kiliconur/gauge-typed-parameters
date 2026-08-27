package com.intellij.openapi.editor;
public interface Document {
  int getTextLength();
  CharSequence getCharsSequence();
  void insertString(int offset, CharSequence s);
  void replaceString(int start, int end, CharSequence s);
}
