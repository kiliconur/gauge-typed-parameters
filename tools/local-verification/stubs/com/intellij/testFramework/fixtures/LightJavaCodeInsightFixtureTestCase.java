package com.intellij.testFramework.fixtures;
import com.intellij.testFramework.UsefulTestCase;
public abstract class LightJavaCodeInsightFixtureTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  protected void setUp() throws Exception {}
}
