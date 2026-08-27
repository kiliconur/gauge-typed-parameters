package com.intellij.openapi.module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
public interface Module extends UserDataHolder {
  Project getProject();
  boolean isDisposed();
  String getName();
}
