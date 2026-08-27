package com.intellij.openapi.components;
public interface ComponentManager {
  <T> T getService(Class<T> serviceClass);
}
