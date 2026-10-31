package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides how a double-ended collection (ArrayDeque, LinkedList) should be drawn from how the source
 * uses each variable: {@code push}/{@code pop} mean a stack (vertical), {@code offer}/{@code poll} or a
 * {@code Queue} type mean a queue (horizontal). Names come from local variables, parameters and fields.
 */
final class CollectionUsageAnalyzer {
    private static final Set<String> STACK_CALLS = Set.of("push", "pop");
    private static final Set<String> QUEUE_CALLS = Set.of("offer", "poll", "offerLast", "pollFirst");
    private static final Set<String> QUEUE_TYPES = Set.of("Queue", "BlockingQueue", "AbstractQueue");

    private CollectionUsageAnalyzer() {}

    /** Variable/field name to "stack" or "queue"; names without a clear signal are absent. */
    static Map<String, String> analyze(String source) {
        if (source == null || source.isBlank()) return Map.of();
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (RuntimeException parseFailure) {
            return Map.of();
        }
        Map<String, String> declaredTypes = new HashMap<>();
        unit.findAll(VariableDeclarator.class).forEach(variable -> declaredTypes.put(variable.getNameAsString(), simpleType(variable.getType())));
        unit.findAll(Parameter.class).forEach(parameter -> declaredTypes.put(parameter.getNameAsString(), simpleType(parameter.getType())));

        Set<String> stackNames = new HashSet<>();
        Set<String> queueNames = new HashSet<>();
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            String target = call.getScope().map(CollectionUsageAnalyzer::targetName).orElse(null);
            if (target == null) continue;
            if (STACK_CALLS.contains(call.getNameAsString())) stackNames.add(target);
            if (QUEUE_CALLS.contains(call.getNameAsString())) queueNames.add(target);
        }

        Map<String, String> views = new HashMap<>();
        declaredTypes.forEach((name, type) -> {
            if (QUEUE_TYPES.contains(type)) views.put(name, "queue");
        });
        queueNames.forEach(name -> views.put(name, "queue"));
        stackNames.forEach(name -> views.put(name, "stack"));          // push/pop is the strongest signal
        return Map.copyOf(views);
    }

    /** {@code stack.push(..)} and {@code this.stack.push(..)} both target "stack". */
    private static String targetName(Expression scope) {
        if (scope instanceof NameExpr name) return name.getNameAsString();
        if (scope instanceof FieldAccessExpr field) return field.getNameAsString();
        return null;
    }

    private static String simpleType(Type type) {
        return type instanceof ClassOrInterfaceType named ? named.getNameAsString() : type.asString();
    }
}
