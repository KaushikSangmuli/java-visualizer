package com.example.demo.service;

import com.example.demo.model.BlockedApi;
import com.example.demo.model.CodeMetadata;
import com.example.demo.model.MethodMetadata;
import com.example.demo.model.SourceRange;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** AST-based source metadata and a deliberately conservative API blocklist. */
@Service
public class CodeInspector {
    public record Inspection(CodeMetadata metadata, List<BlockedApi> blockedApis, boolean parseable) {}

    public Inspection inspect(String source) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(source);
            List<BlockedApi> blocked = new ArrayList<>();
            inspectImports(unit, blocked);
            inspectCalls(unit, blocked);
            inspectObjectCreation(unit, blocked);
            return new Inspection(metadata(unit), List.copyOf(blocked), true);
        } catch (ParseProblemException malformed) {
            // Let javac produce its familiar diagnostics; malformed partial ASTs are not trusted.
            return new Inspection(new CodeMetadata(List.of(), List.of(), List.of(), List.of(), List.of()), List.of(), false);
        }
    }

    private static void inspectImports(CompilationUnit unit, List<BlockedApi> blocked) {
        unit.getImports().forEach(importDecl -> {
            String name = importDecl.getNameAsString();
            String denied = deniedImport(name);
            if (denied == null && importDecl.isStatic()
                    && (name.endsWith(".System.exit") || name.endsWith(".Runtime.exec")
                    || name.endsWith(".ProcessBuilder.start"))) denied = "process control";
            if (denied != null) blocked.add(new BlockedApi(line(importDecl.getBegin()), name,
                    "Import provides access to " + denied + " APIs."));
        });
    }

    private static String deniedImport(String name) {
        if (name.equals("java.lang.reflect") || name.startsWith("java.lang.reflect.")) return "reflection";
        if (name.equals("java.net") || name.startsWith("java.net.")) return "network";
        if (name.equals("java.nio.file") || name.startsWith("java.nio.file.")) return "filesystem";
        if (name.equals("java.io.File") || name.startsWith("java.io.FileInputStream")
                || name.startsWith("java.io.FileOutputStream") || name.startsWith("java.io.RandomAccessFile")
                || name.startsWith("java.io.FileReader") || name.startsWith("java.io.FileWriter")) return "filesystem";
        return null;
    }

    private static void inspectCalls(CompilationUnit unit, List<BlockedApi> blocked) {
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            String scope = call.getScope().map(Object::toString).orElse("");
            String reason = blockedReason(name, scope);
            if (reason != null) blocked.add(new BlockedApi(line(call.getBegin()), scope.isBlank() ? name : scope + "." + name,
                    "Blocked " + reason + " API."));
        }
        // Method references (System::getenv, Runtime::exec) reach the same APIs without a call expression.
        for (MethodReferenceExpr reference : unit.findAll(MethodReferenceExpr.class)) {
            String name = reference.getIdentifier();
            String scope = reference.getScope().toString();
            String reason = blockedReason(name, scope);
            if (reason != null) blocked.add(new BlockedApi(line(reference.getBegin()), scope + "::" + name,
                    "Blocked " + reason + " API."));
        }
    }

    private static String blockedReason(String name, String scope) {
            String reason = null;
            if (name.equals("getenv")) reason = "environment variable";
            else if (name.equals("exit") && scope.endsWith("System")) reason = "process termination";
            else if (name.equals("exec")) reason = "process creation";
            else if (name.equals("start") || name.equals("runAsync") || name.equals("supplyAsync")
                    || name.equals("newVirtualThreadPerTaskExecutor") || name.equals("newThreadPerTaskExecutor")) reason = "thread or process creation";
            else if (name.equals("load") || name.equals("loadLibrary")) reason = "native library loading";
            else if (name.equals("forName") || name.equals("loadClass") || name.equals("invoke") || name.equals("setAccessible")
                    || name.equals("getDeclaredMethod") || name.equals("getDeclaredField")) reason = "reflection or dynamic code access";
            return reason;
    }

    private static void inspectObjectCreation(CompilationUnit unit, List<BlockedApi> blocked) {
        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            String qualifiedName = creation.getType().getNameWithScope();
            String name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
            if (List.of("ProcessBuilder", "FileInputStream", "FileOutputStream", "RandomAccessFile",
                    "FileReader", "FileWriter", "PrintWriter", "File", "Socket", "ServerSocket",
                    "URL", "URLClassLoader").contains(name)) {
                blocked.add(new BlockedApi(line(creation.getBegin()), name,
                        "Blocked process, filesystem, network, or class-loading API."));
            }
        }
    }

    private static CodeMetadata metadata(CompilationUnit unit) {
        String packageName = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        List<MethodMetadata> methods = unit.findAll(MethodDeclaration.class).stream().map(method -> {
            int start = line(method.getBegin());
            int end = line(method.getEnd());
            String className = method.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                    .map(type -> qualifiedClassName(type, packageName))
                    .orElse(packageName);
            List<String> parameters = method.getParameters().stream()
                    .map(parameter -> parameter.getTypeAsString() + " " + parameter.getNameAsString()).toList();
            return new MethodMetadata(method.getNameAsString(), className, method.getTypeAsString(), start, end, parameters);
        }).toList();
        List<SourceRange> loops = new ArrayList<>();
        unit.findAll(ForStmt.class).forEach(node -> loops.add(range(node, "loop", "for")));
        unit.findAll(WhileStmt.class).forEach(node -> loops.add(range(node, "loop", "while")));
        unit.findAll(DoStmt.class).forEach(node -> loops.add(range(node, "loop", "do-while")));
        List<SourceRange> branches = new ArrayList<>();
        unit.findAll(IfStmt.class).forEach(node -> branches.add(range(node, "branch", "if")));
        unit.findAll(SwitchStmt.class).forEach(node -> branches.add(range(node, "branch", "switch")));
        List<SourceRange> declarations = unit.findAll(VariableDeclarationExpr.class).stream()
                .map(node -> range(node, "declaration", "variable declaration")).toList();
        List<SourceRange> returns = unit.findAll(ReturnStmt.class).stream()
                .map(node -> range(node, "return", "return statement")).toList();
        return new CodeMetadata(List.copyOf(methods), List.copyOf(loops), List.copyOf(branches),
                List.copyOf(declarations), List.copyOf(returns));
    }

    private static SourceRange range(com.github.javaparser.ast.Node node, String kind, String label) {
        return new SourceRange(line(node.getBegin()), line(node.getEnd()), kind, label);
    }

    private static String qualifiedClassName(com.github.javaparser.ast.body.TypeDeclaration<?> type, String packageName) {
        List<String> names = new ArrayList<>();
        Node cursor = type;
        while (cursor != null) {
            if (cursor instanceof com.github.javaparser.ast.body.TypeDeclaration<?> declaration) {
                names.add(0, declaration.getNameAsString());
            }
            cursor = cursor.getParentNode().orElse(null);
        }
        String simpleName = String.join(".", names);
        return packageName.isBlank() ? simpleName : packageName + "." + simpleName;
    }

    private static int line(Optional<com.github.javaparser.Position> position) {
        return position.map(p -> p.line).orElse(0);
    }
}
