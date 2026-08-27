package com.intellij.codeInspection;
import com.intellij.openapi.project.Project;
public interface QuickFix<D extends CommonProblemDescriptor> {
  String getName();
  String getFamilyName();
  void applyFix(Project project, D descriptor);
}
