package com.intellij.openapi.util;
public interface UserDataHolder {
  <T> T getUserData(Key<T> key);
  <T> void putUserData(Key<T> key, T value);
}
