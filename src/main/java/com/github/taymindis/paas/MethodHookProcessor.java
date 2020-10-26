package com.github.taymindis.paas;


import com.github.taymindis.paas.annotation.hook;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes({"com.github.taymindis.paas.annotation.hook"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class MethodHookProcessor extends AbstractProcessor {

    private Messager messager;

    public static String m = "";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // get elements annotated with the @hook annotation
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(hook.class);

        for (Element element : annotatedElements) {
            if (element.getKind() == ElementKind.METHOD) {
                // only handle methods as targets
                checkMethod((ExecutableElement) element);
            }
        }

        // don't claim annotations to allow other processors to process them
        return false;
    }

    private void checkMethod(ExecutableElement method) {
        // check for valid name
        String name = method.getSimpleName().toString();
        if (!name.startsWith("stub")) {
            printError(method, "stub name must start with \"stub\"");
        } else if (name.length() > 4 && Character.isLowerCase(name.charAt(4))) {
            printError(method, "character following \"stub\" must be upper case");
        }

//        // cannot be public
//        if (method.getModifiers().contains(Modifier.PUBLIC)) {
//            printError(method, "method must not be public");
//        }

        // check, if method is static
        if (method.getModifiers().contains(Modifier.STATIC)) {
            printError(method, "method must not be static");
        }
    }

    private void printError(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    @Override
    public void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        // get messager for printing errors
        messager = processingEnvironment.getMessager();
    }
//    @Override
//    public Set<String> getSupportedAnnotationTypes() {
//        // Return the set of annotations supported
//    }
//    @Override
//    public SourceVersion getSupportedSourceVersion() {
//        // Return the Java version supported
//    }
}