package com.intellij.openapi.diagnostic;
public abstract class Logger {
  private static final Logger NOOP = new Logger() {};
  public static Logger getInstance(Class<?> cl) { return NOOP; }
  public static Logger getInstance(String category) { return NOOP; }
  public void info(String msg) {}
  public void info(String msg, Throwable t) {}
  public void debug(String msg) {}
  public void debug(Throwable t) {}
  public void debug(String msg, Throwable t) {}
  public void warn(String msg) {}
  public void warn(String msg, Throwable t) {}
}
