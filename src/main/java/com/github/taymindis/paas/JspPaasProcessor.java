package com.github.taymindis.paas;

import com.github.taymindis.paas.annotation.hook;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@SupportedAnnotationTypes("com.github.taymindis.paas.annotation.hook")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class JspPaasProcessor extends AbstractProcessor {

    private static final String JSP_MAIN_METHOD_BLOCK = "_jspService";
    private Trees trees;
    private Elements elementUtils;
    private Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        trees = Trees.instance(processingEnv);
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        TypeMirror requestType = elementUtils.getTypeElement("javax.servlet.http.HttpServletRequest").asType();
        TypeMirror respType = elementUtils.getTypeElement("javax.servlet.http.HttpServletResponse").asType();

        for (final Element element : roundEnv.getRootElements()) {
            boolean shouldCheck = false;
            MethodTree methodPendingCheck = null;
            ExecutableElement executableElPendingToCheck = null;

            for (ExecutableElement executableElement :
                    ElementFilter.methodsIn(element.getEnclosedElements())) {

                if (executableElement.getAnnotation(hook.class) != null) {
                    shouldCheck = true;
                    if (methodPendingCheck != null) {
                        break;
                    }
                }

                if (executableElement.getSimpleName().toString().equals(JSP_MAIN_METHOD_BLOCK)) {
                    if (shouldCheck) {
                        if (executableElement.getReturnType().getKind().compareTo(TypeKind.VOID) != 0) {
                            showError(element, " return type is not void");
                            return true;
                        }
                        if (!executableElement.getModifiers().contains(Modifier.PUBLIC)) {
                            showError(element);
                            return true;
                        }
                        MethodScanner methodScanner = new MethodScanner();
                        methodPendingCheck = methodScanner.scan(executableElement, this.trees);
                        executableElPendingToCheck = executableElement;
                        break;
                    }
                }
            }

            if (shouldCheck && methodPendingCheck != null) {
                if (!isFinalClass((TypeElement) element)) {
                    showError(element);
                    return true;
                }

                List<VariableElement> parameters = (List<VariableElement>) executableElPendingToCheck.getParameters();
                if (parameters.size() != 10) {
                    showError(element);
                }

                for (VariableElement variableElement : parameters) {
                    if (!variableElement.getModifiers().contains(Modifier.FINAL)) {
                        showError(element);
                    }
                }


                if (!typeUtils.isSameType(parameters.get(0).asType(), requestType) &&
                        !(typeUtils.isSameType(parameters.get(1).asType(), respType))
                ) {
                    showError(element);
                }
                return true;
            } else if(shouldCheck) {
                showError(element, " entry point not found");
            }
        }
        return true;
    }


    private static class MethodScanner extends TreePathScanner<List<MethodTree>, Trees> {
        private List<MethodTree> methodTrees = new ArrayList<>();

        public MethodTree scan(ExecutableElement methodElement, Trees trees) {
            assert methodElement.getKind() == ElementKind.METHOD;

            List<MethodTree> methodTrees = this.scan(trees.getPath(methodElement), trees);
            assert methodTrees.size() == 1;

            return methodTrees.get(0);
        }

        @Override
        public List<MethodTree> scan(TreePath treePath, Trees trees) {
            super.scan(treePath, trees);
            return this.methodTrees;
        }

        @Override
        public List<MethodTree> visitMethod(MethodTree methodTree, Trees trees) {
            this.methodTrees.add(methodTree);
            return super.visitMethod(methodTree, trees);
        }
    }

    private boolean hasDefaultConstructor(TypeElement type) {
        for (ExecutableElement cons :
                ElementFilter.constructorsIn(type.getEnclosedElements())) {
            if (cons.getParameters().isEmpty())
                return true;
        }

        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR, String.format("%s is missing a default constructor", type.getSimpleName()),
                type);
        return false;
    }

    private boolean isFinalClass(TypeElement type) {
        return type.getModifiers().contains(Modifier.FINAL);
    }


    private void showError(Element element) {
        showError(element, "");
    }

    private void showError(Element element, String happenedOn) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR, String.format("@hook should only in jsp but %s is not, " +
                                " cause by %s",
                        ((TypeElement) element).getQualifiedName() + "", happenedOn),
                element);
    }
}