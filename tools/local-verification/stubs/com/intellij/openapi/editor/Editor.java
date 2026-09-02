package com.intellij.openapi.editor;
import com.intellij.openapi.util.UserDataHolder;
public interface Editor extends UserDataHolder { CaretModel getCaretModel(); Document getDocument(); }
