package com.intellij.testFramework.fixtures;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import java.util.List;
public interface CodeInsightTestFixture {
  PsiClass addClass(String classText);
  PsiFile configureByText(String fileName, String text);
  LookupElement[] completeBasic();
  List<String> getLookupElementStrings();
  LookupEx getLookup();
  LookupElement[] getLookupElements();
  void finishLookup(char completionChar);
  void checkResult(String expectedText);
  void enableInspections(InspectionProfileEntry... inspections);
  List<HighlightInfo> doHighlighting();
  List<IntentionAction> getAllQuickFixes();
  void launchAction(IntentionAction action);
  PsiFile getFile();
}
