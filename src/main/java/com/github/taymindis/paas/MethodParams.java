package com.github.taymindis.paas;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class MethodParams {
   private String className;
   private int hashCode;
   private Method m;
   private Annotation[][] paramAnnotation;
//   private Class<?>[] parameterTypes;

    public MethodParams(String className, int hashCode) {
        this.className = className;
        this.hashCode = hashCode;
    }

    public Method getM() {
        return m;
    }

    public void setM(Method m) {
        this.m = m;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getHashCode() {
        return hashCode;
    }

    public void setHashCode(int hashCode) {
        this.hashCode = hashCode;
    }

    public Annotation[][] getParamAnnotation() {
        return paramAnnotation;
    }

    public void setParamAnnotation(Annotation[][] paramAnnotation) {
        this.paramAnnotation = paramAnnotation;
    }

}
