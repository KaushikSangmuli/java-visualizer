package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds every method call and object creation in the submitted source, in Java evaluation order,
 * together with the method that contains it and the variable that receives its result.
 * The trace later matches these call sites to JDI call/return events; calls into library code
 * never produce a user frame, so they are never matched and never shown.
 */
final class CallSiteAnalyzer {
    /** Name used by the JVM (and therefore by trace frames) for constructors. */
    static final String CONSTRUCTOR = "<init>";

    /**
     * @param order          position in Java evaluation order (inner arguments before the outer call)
     * @param callerMethod   method containing the call; {@code <init>} for constructors
     * @param callerClass    simple name of the class declaring the caller, or null when unknown
     * @param calledMethod   invoked method name; {@code <init>} for {@code new Type(...)}
     * @param calledClass    created type for constructors, otherwise null (not resolved statically)
     * @param displayName    text before the argument list, e.g. {@code fun_add}, {@code list.add}, {@code new Node}
     * @param sourceExpression call exactly as written, e.g. {@code fun_add(result, number)}
     * @param target         receiving variable, {@code "return"} for a returned call, or null
     */
    record CallSite(int order, String callerMethod, String callerClass, String calledMethod, String calledClass,
                    int startLine, int endLine, int argumentCount, String displayName,
                    String sourceExpression, String target) {}

    private CallSiteAnalyzer() {}

    /** Returns an empty list when the source cannot be parsed; call boxes are optional decoration. */
    static List<CallSite> analyze(String source) {
        if (source == null || source.isBlank()) return List.of();
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (RuntimeException parseFailure) {
            return List.of();
        }
        List<CallSite> sites = new ArrayList<>();
        // Post-order visits arguments and scopes before the call that uses them, matching Java evaluation.
        unit.walk(Node.TreeTraversal.POSTORDER, node -> {
            if (node instanceof MethodCallExpr call) {
                String name = call.getScope().map(scope -> scope + "." + call.getNameAsString())
                        .orElse(call.getNameAsString());
                add(sites, call, call.getNameAsString(), null, name, call.getArguments().size());
            } else if (node instanceof ObjectCreationExpr creation && creation.getAnonymousClassBody().isEmpty()) {
                String type = creation.getType().getNameAsString();
                add(sites, creation, CONSTRUCTOR, type, "new " + type, creation.getArguments().size());
            }
        });
        return List.copyOf(sites);
    }

    private static void add(List<CallSite> sites, Expression call, String calledMethod, String calledClass,
                            String displayName, int argumentCount) {
        if (call.getBegin().isEmpty() || call.getEnd().isEmpty()) return;
        Caller caller = caller(call);
        if (caller == null) return;
        sites.add(new CallSite(sites.size(), caller.method(), caller.className(), calledMethod, calledClass,
                call.getBegin().get().line, call.getEnd().get().line, argumentCount, displayName,
                call.toString(), target(call)));
    }

    record Caller(String method, String className) {}

    /** The first enclosing method or constructor; calls inside lambdas and initializers are not attributed. */
    static Caller caller(Node call) {
        String method = null;
        Optional<Node> current = call.getParentNode();
        while (current.isPresent()) {
            Node node = current.get();
            if (method == null) {
                if (node instanceof LambdaExpr || node instanceof InitializerDeclaration
                        || node instanceof FieldDeclaration) {
                    return null;
                }
                if (node instanceof MethodDeclaration declaration) method = declaration.getNameAsString();
                else if (node instanceof ConstructorDeclaration) method = CONSTRUCTOR;
            } else {
                if (node instanceof ObjectCreationExpr) return new Caller(method, null);   // anonymous class
                if (node instanceof TypeDeclaration<?> type) return new Caller(method, type.getNameAsString());
            }
            current = node.getParentNode();
        }
        return method == null ? null : new Caller(method, null);
    }

    /** Only a call that is the whole assigned/returned value gets a target; nested argument calls do not. */
    private static String target(Expression call) {
        Node child = call;
        Optional<Node> parent = call.getParentNode();
        while (parent.isPresent() && (parent.get() instanceof EnclosedExpr || parent.get() instanceof CastExpr)) {
            child = parent.get();
            parent = child.getParentNode();
        }
        if (parent.isEmpty()) return null;
        Node node = parent.get();
        Node value = child;
        if (node instanceof AssignExpr assignment && assignment.getValue() == value) {
            return assignment.getTarget().toString();
        }
        if (node instanceof VariableDeclarator declarator
                && declarator.getInitializer().filter(initializer -> initializer == value).isPresent()) {
            return declarator.getNameAsString();
        }
        if (node instanceof ReturnStmt statement
                && statement.getExpression().filter(returned -> returned == value).isPresent()) {
            return "return";
        }
        return null;
    }
}
