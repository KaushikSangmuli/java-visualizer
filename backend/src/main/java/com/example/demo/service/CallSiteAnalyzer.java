package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;

import java.util.List;
import java.util.Optional;

/** Resolves Java method invocations to their enclosing caller and assignment target. */
final class CallSiteAnalyzer {
    record CallSite(String callerMethod, String calledMethod, int startLine, int endLine,
                    int argumentCount, String sourceExpression, String target) {}

    private CallSiteAnalyzer() {}

    static List<CallSite> analyze(String source) {
        if (source == null || source.isBlank()) return List.of();
        CompilationUnit unit = StaticJavaParser.parse(source);
        return unit.findAll(MethodCallExpr.class).stream()
                .map(CallSiteAnalyzer::callSite)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<CallSite> callSite(MethodCallExpr call) {
        MethodDeclaration caller = call.findAncestor(MethodDeclaration.class).orElse(null);
        if (caller == null || call.getBegin().isEmpty() || call.getEnd().isEmpty()) return Optional.empty();
        String target = assignmentTarget(call).orElse(null);
        return Optional.of(new CallSite(caller.getNameAsString(), call.getNameAsString(),
                call.getBegin().orElseThrow().line, call.getEnd().orElseThrow().line,
                call.getArguments().size(), call.toString(), target));
    }

    private static Optional<String> assignmentTarget(MethodCallExpr call) {
        AssignExpr assignment = call.findAncestor(AssignExpr.class).orElse(null);
        if (assignment != null && contains(assignment.getValue(), call)) {
            return Optional.of(assignment.getTarget().toString());
        }
        VariableDeclarator declaration = call.findAncestor(VariableDeclarator.class).orElse(null);
        if (declaration != null && declaration.getInitializer().filter(value -> contains(value, call)).isPresent()) {
            return Optional.of(declaration.getNameAsString());
        }
        ReturnStmt returnStatement = call.findAncestor(ReturnStmt.class).orElse(null);
        if (returnStatement != null && returnStatement.getExpression().filter(value -> contains(value, call)).isPresent()) {
            return Optional.of("return");
        }
        return Optional.empty();
    }

    private static boolean contains(Node root, MethodCallExpr call) {
        return root == call || root.findAll(MethodCallExpr.class).contains(call);
    }
}
