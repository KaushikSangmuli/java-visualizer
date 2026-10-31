package com.example.demo.service;

import com.example.demo.model.VisualizeResponse;
import com.example.demo.model.CodeMetadata;
import com.example.demo.model.BlockedApi;
import com.example.demo.tracer.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The whole pipeline for one "Visualize" click:
 *
 *   1. validate the code               (not empty, not too long, has a main method)
 *   2. write it to a unique temp folder:  <tmp>/javavis-xxxx/src/<Name>.java
 *   3. compile it                      -> <tmp>/javavis-xxxx/classes/*.class   (or compile errors)
 *   4. run it under the tracer         -> list of steps
 *   5. build the response, and ALWAYS delete the temp folder (finally block)
 */
@Service
public class VisualizeService {

    private static final Logger log = LoggerFactory.getLogger(VisualizeService.class);

    private final CompilerService compiler;
    private final Tracer tracer;
    private final CodeInspector inspector;
    private final TraceEnricher enricher;
    private final RequestAdmissionService admissionService;

    private final int maxCodeLength;
    private final int maxSteps;
    private final int timeoutMs;
    private final int maxStdoutChars;
    private final int maxDisplayedFrames;
    private final int maxHeapObjects;
    private final int maxArrayElements;
    private final int maxHeapDepth;

    public VisualizeService(@Value("${visualizer.max-code-length:20000}") int maxCodeLength,
                            @Value("${visualizer.max-steps:2000}") int maxSteps,
                            @Value("${visualizer.timeout-ms:10000}") int timeoutMs,
                            @Value("${visualizer.max-stdout-chars:20000}") int maxStdoutChars,
                            @Value("${visualizer.max-displayed-frames:50}") int maxDisplayedFrames,
                            @Value("${visualizer.max-heap-objects:500}") int maxHeapObjects,
                            @Value("${visualizer.max-array-elements:100}") int maxArrayElements,
                            @Value("${visualizer.max-heap-depth:8}") int maxHeapDepth,
                            CompilerService compiler, Tracer tracer,
                            CodeInspector inspector, TraceEnricher enricher,
                            RequestAdmissionService admissionService) {
        this.maxCodeLength = maxCodeLength;
        this.maxSteps = maxSteps;
        this.timeoutMs = timeoutMs;
        this.maxStdoutChars = maxStdoutChars;
        this.maxDisplayedFrames = maxDisplayedFrames;
        this.maxHeapObjects = maxHeapObjects;
        this.maxArrayElements = maxArrayElements;
        this.maxHeapDepth = maxHeapDepth;
        this.compiler = compiler;
        this.tracer = tracer;
        this.inspector = inspector;
        this.enricher = enricher;
        this.admissionService = admissionService;
    }

    public VisualizeResponse visualize(String code) {
        return visualize(code, "local");
    }

    public VisualizeResponse visualize(String code, String clientKey) {
        // ---- 1. validate
        if (code == null || code.isBlank()) {
            return VisualizeResponse.error("error", "Code must not be empty.");
        }
        if (code.length() > maxCodeLength) {
            return VisualizeResponse.error("error", "Code is too long (max " + maxCodeLength + " characters).");
        }
        RequestAdmissionService.Admission admission = admissionService.acquire(clientKey);
        if (!admission.allowed()) return VisualizeResponse.error("busy", admission.message());

        Path workDir = null;
        try {
            String stripped = SourceHelper.strip(code);   // same text, but comments/strings blanked out
            SourceHelper.MainInfo main = SourceHelper.findMain(stripped);
            if (main == null) {
                return VisualizeResponse.error("error",
                        "No 'public static void main(String[] args)' method found.");
            }
            CodeInspector.Inspection inspection = inspector.inspect(code);
            if (!inspection.blockedApis().isEmpty()) {
                return VisualizeResponse.blocked(inspection.blockedApis(), inspection.metadata());
            }

            // ---- 2. write the source file
            workDir = Files.createTempDirectory("javavis-");
            Path srcFile = workDir.resolve("src").resolve(main.fileName() + ".java");
            Path classesDir = workDir.resolve("classes");
            Files.createDirectories(srcFile.getParent());
            Files.writeString(srcFile, SourceHelper.blankPackageLine(code, stripped), StandardCharsets.UTF_8);

            // ---- 3. compile
            CompilerService.Result compiled = compiler.compile(srcFile, classesDir);
            if (!compiled.success()) {
                return VisualizeResponse.compileError(compiled.errors());
            }
            if (!inspection.parseable()) {
                return VisualizeResponse.blocked(List.of(new BlockedApi(0, "source",
                        "Static analysis could not parse this valid program; execution was refused.")), inspection.metadata());
            }

            // ---- 4. run under the tracer
            Tracer.TraceResult result = tracer.trace(classesDir, main.mainClass(), maxSteps, timeoutMs,
                    maxStdoutChars, maxDisplayedFrames, maxHeapObjects, maxArrayElements, maxHeapDepth);

            // ---- 5. build the response
            String status = result.exception() != null ? "runtime_error" : "ok";
            String message = null;
            if (result.exception() != null) {
                message = result.exception().type() + ": " + result.exception().message();
            } else if (result.truncated()) {
                message = "Trace truncated. Possible infinite loop or too many steps.";
            }
            return new VisualizeResponse(status, message, null,
                    enricher.enrich(result.steps(), inspection.metadata(), result.truncated(), code),
                    result.truncated(), result.truncatedReason(), result.exception(), result.stdout(),
                    inspection.metadata(), null);

        } catch (Exception e) {
            log.error("Visualize failed", e);
            return VisualizeResponse.error("error", e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            deleteQuietly(workDir);
            admissionService.release();
        }
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up {}", dir);
        }
    }
}
