package com.intellij.lang;
public abstract class Language {
  private final String id;
  protected Language(String id) { this.id = id; }
  public String getID() { return id; }
  public boolean is(Language another) { return this == another; }
  public boolean isKindOf(Language another) { return this == another; }
}
