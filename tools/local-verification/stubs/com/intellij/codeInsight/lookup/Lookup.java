package com.intellij.codeInsight.lookup;
public interface Lookup {
  char NORMAL_SELECT_CHAR = '\n';
  char REPLACE_SELECT_CHAR = '\t';
  LookupElement getCurrentItem();
}
