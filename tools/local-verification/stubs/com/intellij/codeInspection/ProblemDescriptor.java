package com.intellij.codeInspection;
import com.intellij.psi.PsiElement;
public interface ProblemDescriptor extends CommonProblemDescriptor { PsiElement getPsiElement(); }
