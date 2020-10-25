package com.github.taymindis.paas.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mixin is Param and Attribute either one will get value, attribute will take priority
 * Priority by
 * Attribute key
 * Json key
 * Parameter key
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface any {
    String value();
}
