package com.intellij.util;
import java.util.Collection;
public interface Query<T> {
  Collection<T> findAll();
  boolean forEach(Processor<? super T> consumer);
}
