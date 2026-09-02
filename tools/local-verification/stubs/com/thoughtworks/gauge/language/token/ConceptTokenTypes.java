package com.thoughtworks.gauge.language.token;
import com.intellij.psi.tree.IElementType;
public interface ConceptTokenTypes {
  IElementType ARG = new IElementType();
  IElementType ARG_END = new IElementType();
  IElementType ARG_START = new IElementType();
  IElementType COMMENT = new IElementType();
  IElementType CONCEPT = new IElementType();
  IElementType CONCEPT_HEADING = new IElementType();
  IElementType NEW_LINE = new IElementType();
  IElementType STEP = new IElementType();
  IElementType STEP_IDENTIFIER = new IElementType();
}
