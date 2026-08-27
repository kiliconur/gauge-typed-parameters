package com.intellij.testFramework;
import java.util.Collection;
public abstract class UsefulTestCase extends junit.framework.TestCase {
  protected void setUp() throws Exception {}
  protected void tearDown() throws Exception {}
  public static <T> void assertContainsElements(Collection<? extends T> collection, T... expected) {}
  public static <T> void assertDoesntContain(Collection<? extends T> collection, T... notExpected) {}
  public static void assertSize(int expected, Collection<?> actual) {}
  public static void assertEmpty(Collection<?> collection) {}
}
