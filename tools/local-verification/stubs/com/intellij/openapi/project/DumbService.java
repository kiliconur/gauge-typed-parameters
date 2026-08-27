package com.intellij.openapi.project;
public abstract class DumbService {
  public static boolean isDumb(Project project) { return false; }
}
