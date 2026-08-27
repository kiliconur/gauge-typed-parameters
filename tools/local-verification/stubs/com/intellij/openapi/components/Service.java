package com.intellij.openapi.components;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
  Level[] value() default Level.APP;
  enum Level { APP, PROJECT }
}
