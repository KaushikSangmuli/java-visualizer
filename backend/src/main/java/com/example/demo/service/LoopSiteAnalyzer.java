package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds loops in the submitted source with their condition, line layout and the variables they declare. */
final class LoopSiteAnalyzer {
    /**
     * @param kind           for, for-each, while or do-while
     * @param callerMethod   enclosing method ({@code <init>} for constructors)
     * @param callerClass    simple name of the enclosing class, or null when unknown
     * @param conditionFirst first line of the condition (do-while: the {@code while (...)} line)
     * @param conditionLast  last line of the condition/header
     * @param bodyFirst      first line of the body's statements, or -1 when the body shares the header line
     * @param bodyLast       last line of the body's statements
     * @param condition      condition expression; null for {@code for (;;)} and for-each
     * @param conditionText  condition as written; for-each: {@code x : arr}
     * @param iterable       for-each iterable expression, otherwise null
     * @param variables      variables declared by this loop's header and body, excluding nested loops
     */
    record LoopSite(int id, String kind, String callerMethod, String callerClass, int startLine, int endLine,
                    int conditionFirst, int conditionLast, int bodyFirst, int bodyLast,
                    Expression condition, String conditionText, Expression iterable, List<String> variables) {

        boolean contains(int line) {
            return line >= startLine && line <= endLine;
        }

        boolean inBody(int line) {
            return bodyFirst > 0 && line >= bodyFirst && line <= bodyLast
                    && !(line >= conditionFirst && line <= conditionLast);
        }

        boolean atCondition(int line) {
            return line >= conditionFirst && line <= conditionLast;
        }

        /** Body written on the header line, e.g. {@code for (...) s += i;}: iterations are counted by header visits. */
        boolean oneLine() {
            return bodyFirst <= 0;
        }
    }

    private LoopSiteAnalyzer() {}

    static List<LoopSite> analyze(String source) {
        if (source == null || source.isBlank()) return List.of();
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (RuntimeException parseFailure) {
            return List.of();
        }
        List<LoopSite> sites = new ArrayList<>();
        unit.walk(Node.TreeTraversal.PREORDER, node -> {
            if (node instanceof ForStmt loop) {
                Set<String> declared = new LinkedHashSet<>();
                loop.getInitialization().forEach(init -> {
                    if (init instanceof VariableDeclarationExpr declaration) {
                        declaration.getVariables().forEach(variable -> declared.add(variable.getNameAsString()));
                    }
                });
                Expression compare = loop.getCompare().orElse(null);
                // The header runs from `for (` to the line before the body (or the body's `{` line).
                int bodyBegin = line(loop.getBody(), true);
                int headerEnd = loop.getBody() instanceof BlockStmt ? bodyBegin : bodyBegin - 1;
                add(sites, loop, "for", line(loop, true), Math.max(line(loop, true), headerEnd),
                        loop.getBody(), compare, compare == null ? "(no condition)" : compare.toString(), null, declared);
            } else if (node instanceof ForEachStmt loop) {
                Set<String> declared = new LinkedHashSet<>();
                loop.getVariable().getVariables().forEach(variable -> declared.add(variable.getNameAsString()));
                String text = loop.getVariable().getVariables().get(0).getNameAsString() + " : " + loop.getIterable();
                add(sites, loop, "for-each", line(loop, true), line(loop.getIterable(), false),
                        loop.getBody(), null, text, loop.getIterable(), declared);
            } else if (node instanceof WhileStmt loop) {
                add(sites, loop, "while", line(loop, true), line(loop.getCondition(), false),
                        loop.getBody(), loop.getCondition(), loop.getCondition().toString(), null, new LinkedHashSet<>());
            } else if (node instanceof DoStmt loop) {
                add(sites, loop, "do-while", line(loop.getCondition(), true), line(loop, false),
                        loop.getBody(), loop.getCondition(), loop.getCondition().toString(), null, new LinkedHashSet<>());
            }
        });
        return List.copyOf(sites);
    }

    private static void add(List<LoopSite> sites, Statement loop, String kind, int conditionFirst, int conditionLast,
                            Statement body, Expression condition, String conditionText, Expression iterable,
                            Set<String> declared) {
        if (loop.getBegin().isEmpty() || loop.getEnd().isEmpty()) return;
        CallSiteAnalyzer.Caller caller = CallSiteAnalyzer.caller(loop);
        if (caller == null) return;
        // Variables declared anywhere in the body belong to this loop unless a nested loop or class owns them.
        body.findAll(VariableDeclarator.class).forEach(variable -> {
            if (owner(variable) == loop) declared.add(variable.getNameAsString());
        });
        int[] bodyLines = bodyLines(body);
        int startLine = loop.getBegin().get().line;
        boolean sharesHeader = bodyLines[0] >= conditionFirst && bodyLines[0] <= conditionLast;
        sites.add(new LoopSite(sites.size(), kind, caller.method(), caller.className(), startLine,
                loop.getEnd().get().line, conditionFirst, conditionLast,
                sharesHeader ? -1 : bodyLines[0], sharesHeader ? -1 : bodyLines[1],
                condition, conditionText, iterable, List.copyOf(declared)));
    }

    /** First/last line of the statements the body runs; a block uses its statements, not its braces. */
    private static int[] bodyLines(Statement body) {
        if (body instanceof BlockStmt block) {
            if (block.getStatements().isEmpty()) return new int[] {-1, -1};
            return new int[] {line(block.getStatements().get(0), true),
                    line(block.getStatements().get(block.getStatements().size() - 1), false)};
        }
        return new int[] {line(body, true), line(body, false)};
    }

    /** Nearest enclosing loop of a declaration; null when a lambda or class sits in between. */
    private static Node owner(Node node) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent()) {
            Node parent = current.get();
            if (parent instanceof ForStmt || parent instanceof ForEachStmt || parent instanceof WhileStmt
                    || parent instanceof DoStmt) {
                return parent;
            }
            if (parent instanceof LambdaExpr || parent instanceof TypeDeclaration<?> || parent instanceof ObjectCreationExpr) {
                return null;
            }
            current = parent.getParentNode();
        }
        return null;
    }

    private static int line(Node node, boolean begin) {
        return (begin ? node.getBegin() : node.getEnd()).map(position -> position.line).orElse(-1);
    }
}
