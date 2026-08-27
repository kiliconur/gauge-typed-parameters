package com.intellij.openapi.project;
import com.intellij.openapi.util.UserDataHolder;
public interface Project extends UserDataHolder {
  boolean isDisposed();
}
