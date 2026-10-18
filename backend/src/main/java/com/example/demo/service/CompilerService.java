package com.example.demo.service;

import com.example.demo.model.CompileError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles one .java file using the compiler that is built into the JDK (javax.tools).
 * It is the same as running on the command line:
 *
 *     javac -g -encoding UTF-8 -proc:none -d <classesDir> <sourceFile>
 *
 *  -g          keep debug info (line numbers + local variable NAMES). The tracer needs this.
 *  -proc:none  never run annotation processors (user code must not execute at compile time).
 *
 * INPUT : a .java file on disk
 * OUTPUT: .class files inside classesDir  (success)  OR  a list of {line, message}  (failure)
 */
@org.springframework.stereotype.Service
public class CompilerService {

    public record Result(boolean success, List<CompileError> errors) {}

    public Result compile(Path sourceFile, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // ToolProvider returns null when the app runs on a JRE instead of a full JDK.
            throw new IllegalStateException(
                    "No Java compiler available. Start the backend with a full JDK (17+), not a JRE.");
        }
        Files.createDirectories(classesDir);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fm =
                     compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(sourceFile);
            List<String> options = List.of(
                    "-g", "-encoding", "UTF-8", "-proc:none", "--release", "17", "-d", classesDir.toString());
            ok = Boolean.TRUE.equals(compiler.getTask(null, fm, diagnostics, options, null, units).call());
        }

        List<CompileError> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) continue;
            int line = d.getLineNumber() == Diagnostic.NOPOS ? 0 : (int) d.getLineNumber();
            errors.add(new CompileError(line, d.getMessage(Locale.ENGLISH).trim()));
        }
        return new Result(ok && errors.isEmpty(), errors);
    }
}
