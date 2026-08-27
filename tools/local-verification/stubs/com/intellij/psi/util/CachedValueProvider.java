package com.intellij.psi.util;
public interface CachedValueProvider<T> {
  Result<T> compute();
  class Result<T> {
    public static <T> Result<T> create(T value, Object... dependencies) { return new Result<T>(); }
  }
}
