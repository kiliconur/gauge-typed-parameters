package org.jetbrains.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
public @interface PropertyKey { String resourceBundle(); }
