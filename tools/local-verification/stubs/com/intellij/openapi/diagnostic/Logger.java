package com.intellij.openapi.diagnostic;
public abstract class Logger {
  public static Logger getInstance(Class<?> cl) { return null; }
  public void debug(String msg) {}
  public void debug(Throwable t) {}
  public void debug(String msg, Throwable t) {}
  public void warn(String msg) {}
  public void warn(String msg, Throwable t) {}
}
