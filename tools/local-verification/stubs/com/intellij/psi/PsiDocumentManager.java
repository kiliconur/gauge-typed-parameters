package com.intellij.psi;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
public abstract class PsiDocumentManager {
  public static PsiDocumentManager getInstance(Project p) { return null; }
  public abstract Document getDocument(PsiFile file);
  public abstract void commitDocument(Document document);
}
