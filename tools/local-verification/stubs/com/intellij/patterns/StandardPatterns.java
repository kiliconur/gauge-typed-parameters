package com.intellij.patterns;
public class StandardPatterns {
  @SafeVarargs
  public static <T> ElementPattern<T> or(ElementPattern<? extends T>... patterns) { return null; }
  public static StringPattern string() { return null; }
}
