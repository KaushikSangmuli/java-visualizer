package com.example.demo.tracer;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import com.example.demo.model.ExceptionInfo;
import com.example.demo.model.HeapFieldSnapshot;
import com.example.demo.model.HeapObjectSnapshot;
import com.example.demo.model.HeapSnapshot;
import com.example.demo.model.MemoryValue;
import com.example.demo.model.StaticFieldSnapshot;
import com.example.demo.model.FrameSnapshot;
import com.example.demo.model.TraceStep;
import com.example.demo.model.VariableSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Runs a compiled class in a SEPARATE JVM (the "debuggee") and records one TraceStep per executed
 * source line, using the Java Debug Interface (JDI).
 *
 * TWO JVMs:
 *   - this one (the "debugger") runs our Spring Boot app and this class
 *   - the debuggee runs the user's program:  java -cp <classesDir> -Xmx128m -Xss1m <mainClass>
 * They talk through the JDWP debug protocol. A crash or infinite loop in the user's code can
 * never take our API down, and we can always kill the debuggee.
 *
 * HOW JDI EVENTS MAP TO THE OUTPUT (Stage 2):
 *
 *   VMStartEvent          -> nothing recorded. The debuggee starts paused; we just resume it.
 *   ClassPrepareEvent     -> the main class is loaded but nothing has run yet. We set a BREAKPOINT
 *                            on the first line of main().
 *   BreakpointEvent       -> TraceStep #0, event "call". We delete the breakpoint and create the
 *                            first StepRequest.
 *   StepEvent             -> TraceStep #n, event "step". The JVM paused because execution reached a
 *                            NEW source line (STEP_LINE); "line" = the line about to execute.
 *                            A StepRequest with count filter 1 dies after one event, so we delete it
 *                            and create a new one every time.
 *   ExceptionEvent        -> TraceStep with event "exception" + ExceptionInfo (uncaught only).
 *   VMDeathEvent          -> the program finished normally. Stop.
 *   deadline passed       -> truncated=true, reason "timeout"   (we kill the debuggee)
 *   steps >= maxSteps     -> truncated=true, reason "max_steps" (we kill the debuggee)
 *
 * Stepping works on BYTECODE, not on the text file: the compiled class carries a
 * LineNumberTable (thanks to javac -g) that maps bytecode positions to source lines. STEP_LINE
 * means "run bytecode until the table says we are on a different line".
 *
 * Classes java.*, javax.*, jdk.*, sun.*, com.sun.* are excluded from stepping, so a call to
 * System.out.println is one step and we never walk through JDK internals.
 */
@org.springframework.stereotype.Service
public class Tracer {

    private static final String[] EXCLUDED_CLASSES = {"java.*", "javax.*", "jdk.*", "sun.*", "com.sun.*"};
    private static final int DEFAULT_MAX_DISPLAYED_FRAMES = 50;
    private static final int MAX_CAPTURE_BYTES = 200_000;
    /** System properties user programs may read inside the sandbox (no paths, users or secrets). */
    private static final List<String> SANDBOX_READABLE_PROPERTIES = List.of(
            "java.version", "java.vendor", "java.specification.version", "line.separator",
            "file.separator", "path.separator", "file.encoding", "stdout.encoding", "native.encoding",
            "os.name", "os.arch", "java.vm.name", "user.language", "user.country");

    /** What trace() returns. truncatedReason is "max_steps", "timeout" or null. */
    public record TraceResult(List<TraceStep> steps, boolean truncated, String truncatedReason,
                              ExceptionInfo exception, String stdout) {}

    public TraceResult trace(Path classesDir, String mainClass, int maxSteps, int timeoutMs,
                             int maxStdoutChars, int maxDisplayedFrames,
                             int maxHeapObjects, int maxArrayElements, int maxHeapDepth)
            throws Exception {
        return new Run(classesDir, mainClass, maxSteps, timeoutMs, maxStdoutChars,
                Math.min(Math.max(maxDisplayedFrames, 1), DEFAULT_MAX_DISPLAYED_FRAMES),
                Math.max(maxHeapObjects, 1), Math.max(maxArrayElements, 0), Math.max(maxHeapDepth, 0)).execute();
    }

    // =====================================================================================
    // One tracing session. Fields hold the state of this run only, so Tracer itself is stateless.
    // =====================================================================================
    private static final class Run {
        private final Path classesDir;
        private final String mainClass;
        private final int maxSteps;
        private final int timeoutMs;
        private final int maxStdoutChars;
        private final int maxDisplayedFrames;
        private final int maxHeapObjects;
        private final int maxArrayElements;
        private final int maxHeapDepth;

        private final List<TraceStep> steps = new ArrayList<>();
        private boolean truncated;
        private String truncatedReason;
        private ExceptionInfo exceptionInfo;
        private OutputPump pumpOut;
        private OutputPump pumpErr;
        private long traceThreadId = -1;
        private VirtualMachine debuggeeVm;

        Run(Path classesDir, String mainClass, int maxSteps, int timeoutMs, int maxStdoutChars,
            int maxDisplayedFrames, int maxHeapObjects, int maxArrayElements, int maxHeapDepth) {
            this.classesDir = classesDir;
            this.mainClass = mainClass;
            this.maxSteps = maxSteps;
            this.timeoutMs = timeoutMs;
            this.maxStdoutChars = maxStdoutChars;
            this.maxDisplayedFrames = maxDisplayedFrames;
            this.maxHeapObjects = maxHeapObjects;
            this.maxArrayElements = maxArrayElements;
            this.maxHeapDepth = maxHeapDepth;
        }

        TraceResult execute() throws Exception {
            VirtualMachine vm = launch();
            debuggeeVm = vm;
            Process process = vm.process();

            // Close the program's stdin at once: Scanner / readLine get end-of-file instead of hanging.
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // already closed
            }
            pumpOut = new OutputPump(process.getInputStream(), true, maxStdoutChars);   // program's System.out
            pumpErr = new OutputPump(process.getErrorStream(), false, 0);  // System.err: drained and dropped

            EventRequestManager erm = vm.eventRequestManager();

            // Ask to be told when the main class is loaded (so we can put a breakpoint in it).
            ClassPrepareRequest prepare = erm.createClassPrepareRequest();
            prepare.addClassFilter(mainClass);
            prepare.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            prepare.enable();

            // Ask to be told about exceptions that nobody catches (notifyCaught=false, notifyUncaught=true).
            // No class exclusion filter here on purpose: an exception thrown INSIDE the JDK
            // (e.g. Integer.parseInt) must still be reported; we find the user's line ourselves.
            ExceptionRequest uncaught = erm.createExceptionRequest(null, false, true);
            uncaught.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            uncaught.enable();

            // Method events expose call boundaries and return values; JDK internals stay filtered.
            MethodEntryRequest methodEntries = erm.createMethodEntryRequest();
            addUserCodeExclusions(methodEntries::addClassExclusionFilter);
            methodEntries.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            methodEntries.enable();

            MethodExitRequest methodExits = erm.createMethodExitRequest();
            addUserCodeExclusions(methodExits::addClassExclusionFilter);
            methodExits.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            methodExits.enable();

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            boolean finished = false;
            boolean vmEnded = false;

            try {
                EventQueue queue = vm.eventQueue();
                while (!finished) {
                    // The program's output goes through a pipe with a small buffer. If we never read it,
                    // a program that prints a lot would block forever, so we empty the pipe often.
                    pumpOut.drain();
                    pumpErr.drain();

                    if (System.nanoTime() > deadline) {
                        truncate("timeout");     // e.g. `while (true) {}` on ONE line never produces new steps
                        break;
                    }

                    EventSet set = queue.remove(100);   // wait up to 100 ms for the next event
                    if (set == null) continue;

                    for (Event ev : set) {
                        if (ev instanceof ClassPrepareEvent e) {
                            onClassPrepare(e, erm);
                        } else if (ev instanceof BreakpointEvent e) {
                            onBreakpoint(e, erm);
                        } else if (ev instanceof MethodEntryEvent e) {
                            if (!onMethodEntry(e)) finished = true;
                        } else if (ev instanceof MethodExitEvent e) {
                            if (!onMethodExit(e, vm)) finished = true;
                        } else if (ev instanceof StepEvent e) {
                            if (!onStep(e, erm)) finished = true;
                        } else if (ev instanceof ExceptionEvent e) {
                            onException(e);
                            finished = true;
                        } else if (ev instanceof VMDeathEvent || ev instanceof VMDisconnectEvent) {
                            finished = true;
                            vmEnded = true;
                        }
                        // VMStartEvent and anything else: nothing to record, just resume below.
                        if (finished) break;
                    }

                    if (vmEnded) {
                        try {
                            set.resume();    // let the VM finish dying cleanly
                        } catch (VMDisconnectedException ignored) {
                            // already gone
                        }
                    } else if (!finished) {
                        set.resume();        // let the program run until the next event
                    }
                }
            } catch (VMDisconnectedException e) {
                vmEnded = true;              // the program ended while we were talking to it
            } finally {
                shutdown(vm, process, vmEnded);
            }

            return new TraceResult(steps, truncated, truncatedReason, exceptionInfo, pumpOut.text());
        }

        // ---------------------------------------------------------------- launching

        private VirtualMachine launch() throws Exception {
            LaunchingConnector connector = null;
            for (LaunchingConnector c : Bootstrap.virtualMachineManager().launchingConnectors()) {
                if (c.name().equals("com.sun.jdi.CommandLineLaunch")) connector = c;
            }
            if (connector == null) throw new IllegalStateException("JDI CommandLineLaunch connector not found.");

            Map<String, Connector.Argument> args = connector.defaultArguments();
            args.get("main").setValue(mainClass);
            args.get("suspend").setValue("true");   // start paused so we can set up requests first
            // The policy URL has no spaces (they are %20-encoded), so it needs no quoting on Windows either.
            String policy = writeSandboxPolicy().toUri().toString();
            args.get("options").setValue("-cp \"" + classesDir.toAbsolutePath() + "\""
                    + " -Xmx128m -Xss1m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
                    + " -Djava.security.manager=default -Djava.security.policy==" + policy);
            return connector.launch(args);
        }

        /**
         * Sandbox for the user's program. The debuggee JVM runs with the Java Security Manager and
         * ONLY this policy ("==" ignores the JDK's default policy files), so user code may read its own
         * compiled classes and a few harmless system properties, and nothing else: no environment
         * variables (which hold the backend's database password and signing secrets), no other files,
         * no network, no new processes, no System.exit, no reflection on private members.
         * The Security Manager is deprecated but fully functional in JDK 21, which the Docker image pins.
         */
        private Path writeSandboxPolicy() throws IOException {
            String classes = classesDir.toAbsolutePath().toString().replace("\\", "\\\\");
            StringBuilder policy = new StringBuilder("grant {\n")
                    .append("  permission java.io.FilePermission \"").append(classes).append("\", \"read\";\n")
                    .append("  permission java.io.FilePermission \"").append(classes).append(java.io.File.separator.replace("\\", "\\\\")).append("-\", \"read\";\n");
            for (String property : SANDBOX_READABLE_PROPERTIES) {
                policy.append("  permission java.util.PropertyPermission \"").append(property).append("\", \"read\";\n");
            }
            policy.append("};\n");
            Path file = classesDir.toAbsolutePath().getParent().resolve("sandbox.policy");
            java.nio.file.Files.writeString(file, policy.toString(), StandardCharsets.UTF_8);
            return file;
        }

        private void shutdown(VirtualMachine vm, Process process, boolean vmEnded) {
            if (!vmEnded) {
                try {
                    vm.exit(1);              // kill the debuggee (timeout / max steps / exception)
                } catch (Exception ignored) {
                    // already dead
                }
            }
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            // Once the process is dead the pipe ends with EOF, so we can safely read the rest of the output.
            if (process.isAlive()) pumpOut.drain();
            else pumpOut.drainToEof();
        }

        // ---------------------------------------------------------------- event handlers

        /** Main class is loaded: put a breakpoint on the first executable line of main(). */
        private void onClassPrepare(ClassPrepareEvent ev, EventRequestManager erm) {
            erm.deleteEventRequest(ev.request());
            List<Method> mains = ev.referenceType().methodsByName("main", "([Ljava/lang/String;)V");
            if (mains.isEmpty()) throw new IllegalStateException("No main(String[]) found in " + mainClass);

            Location first = mains.get(0).location();   // bytecode index 0 = first line of the body
            BreakpointRequest bp = erm.createBreakpointRequest(first);
            bp.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            bp.enable();
        }

        /** Reached the first line of main(): record step 0 and start single-stepping. */
        private void onBreakpoint(BreakpointEvent ev, EventRequestManager erm) {
            erm.deleteEventRequest(ev.request());       // one-time breakpoint (also safe for recursive main)
            traceThreadId = ev.thread().uniqueID();
            record("call", ev.location().lineNumber(), ev.thread(), "Entered method main.", null);
            requestStep(erm, ev.thread());
        }

        /** Captures user method entry while the new frame and its arguments are available. */
        private boolean onMethodEntry(MethodEntryEvent ev) {
            if (!isTraceThread(ev.thread()) || !isUserClass(ev.method().declaringType().name())) return true;
            if (steps.size() >= maxSteps) {
                truncate("max_steps");
                return false;
            }
            String method = ev.method().name();
            record("call", ev.location().lineNumber(), ev.thread(), "Entered method " + method + ".", null);
            return true;
        }

        /** Captures the return value directly from JDI without invoking code in the debuggee. */
        private boolean onMethodExit(MethodExitEvent ev, VirtualMachine vm) {
            if (!isTraceThread(ev.thread()) || !isUserClass(ev.method().declaringType().name())) return true;
            if (steps.size() >= maxSteps) {
                truncate("max_steps");
                return false;
            }
            Value value = null;
            if (!"void".equals(ev.method().returnTypeName()) && vm.canGetMethodReturnValues()) {
                try {
                    value = ev.returnValue();
                } catch (UnsupportedOperationException ignored) {
                    // Some debuggee VMs can report the exit event but not mirror its return value.
                }
            }
            String method = ev.method().name();
            String formatted = value == null ? null : displayValue(value);
            String explanation = "Returned from method " + method
                    + (formatted == null ? "." : " with value " + formatted + ".");
            record("return", ev.location().lineNumber(), ev.thread(), explanation, formatted);
            return true;
        }

        /** A new source line is about to run. Returns false if we must stop (step limit). */
        private boolean onStep(StepEvent ev, EventRequestManager erm) {
            if (steps.size() >= maxSteps) {
                truncate("max_steps");
                return false;
            }
            erm.deleteEventRequest(ev.request());       // the old request is used up
            record("step", ev.location().lineNumber(), ev.thread(),
                    "Execution reached source line " + ev.location().lineNumber() + ".", null);
            requestStep(erm, ev.thread());              // ask for the next line
            return true;
        }

        /** Uncaught exception: record a step on the user's line and remember type + message. */
        private void onException(ExceptionEvent ev) {
            ObjectReference ex = ev.exception();
            int line = ev.location().lineNumber();
            try {
                // The exception may have been thrown deep inside the JDK; walk up to the first user frame.
                for (StackFrame f : ev.thread().frames()) {
                    Location loc = f.location();
                    if (isUserClass(loc.declaringType().name())) {
                        line = loc.lineNumber();
                        break;
                    }
                }
            } catch (IncompatibleThreadStateException ignored) {
                // keep the throw location
            }
            exceptionInfo = new ExceptionInfo(ex.referenceType().name(), line, exceptionMessage(ex));
            record("exception", line, ev.thread(), "An uncaught exception stopped execution.", null);
        }

        // ---------------------------------------------------------------- helpers

        private void requestStep(EventRequestManager erm, ThreadReference thread) {
            StepRequest sr = erm.createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
            for (String pattern : EXCLUDED_CLASSES) sr.addClassExclusionFilter(pattern);
            sr.addCountFilter(1);                        // report exactly one event, then expire
            sr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            sr.enable();
        }

        private void record(String event, int line, ThreadReference thread,
                            String explanation, String returnValue) {
            pumpOut.drain();                             // include everything printed up to now
            List<FrameSnapshot> frames = snapshotFrames(thread);
            HeapSnapshot heap = snapshotHeap(thread);
            steps.add(new TraceStep(steps.size(), line, event, pumpOut.text(),
                    frames, explanation, returnValue, heap, null, List.of(), List.of()));
        }

        private List<FrameSnapshot> snapshotFrames(ThreadReference thread) {
            try {
                List<StackFrame> allFrames = thread.frames();
                List<FrameSnapshot> snapshots = new ArrayList<>();
                for (int i = 0; i < allFrames.size() && i < maxDisplayedFrames; i++) {
                    StackFrame frame = allFrames.get(i);
                    Location location = frame.location();
                    if (!isUserClass(location.declaringType().name())) continue;

                    Method method = location.method();
                    List<VariableSnapshot> variables = new ArrayList<>();
                    try {
                        for (LocalVariable local : frame.visibleVariables()) {
                            try {
                                Value value = frame.getValue(local);
                                variables.add(new VariableSnapshot(local.name(), local.typeName(),
                                        value == null ? "null" : displayValue(value),
                                        local.isArgument() ? "parameter" : "local", referenceId(value)));
                            } catch (IllegalArgumentException ignored) {
                                // A local can be in its source scope before its bytecode slot is assigned.
                            }
                        }
                    } catch (com.sun.jdi.AbsentInformationException ignored) {
                        // The build uses -g; retain the frame if a particular method lacks local metadata.
                    }

                    ObjectReference thisObject = frame.thisObject();
                    if (thisObject != null) {
                        variables.add(0, new VariableSnapshot("this", thisObject.referenceType().name(),
                                displayValue(thisObject), "this", Long.toString(thisObject.uniqueID())));
                    }
                    snapshots.add(new FrameSnapshot(method.name(), location.declaringType().name(),
                            method.returnTypeName(), location.lineNumber(), List.copyOf(variables)));
                }
                return List.copyOf(snapshots);
            } catch (IncompatibleThreadStateException | VMDisconnectedException ignored) {
                return Collections.emptyList();
            }
        }

        /**
         * Roots a bounded object-graph walk at visible frame values and user-class statics.
         * JDI field/array mirrors are read directly; no method in the debuggee is invoked.
         */
        private HeapSnapshot snapshotHeap(ThreadReference thread) {
            HeapBuilder heap = new HeapBuilder();
            // Read the same suspended frames again to retain actual JDI object mirrors as graph roots.
            try {
                for (StackFrame frame : thread.frames()) {
                    for (LocalVariable local : frame.visibleVariables()) {
                        Value value = frame.getValue(local);
                        if (value instanceof ObjectReference object) heap.enqueue(object, 0);
                    }
                    ObjectReference thisObject = frame.thisObject();
                    if (thisObject != null) heap.enqueue(thisObject, 0);
                }
            } catch (IncompatibleThreadStateException | com.sun.jdi.AbsentInformationException ignored) {
                // Keep any roots already collected; the snapshot may be partial if metadata is unavailable.
                heap.markTruncated("unavailable_frame_roots");
            } catch (RuntimeException ignored) {
                heap.markTruncated("unavailable_frame_roots");
            }

            List<StaticFieldSnapshot> statics = new ArrayList<>();
            // allClasses() contains only classes loaded by this debuggee; keep static roots to user code.
            for (ReferenceType type : debuggeeVm.allClasses()) {
                if (!isUserClass(type.name())) continue;
                for (Field field : type.fields()) {
                    if (!field.isStatic() || field.isSynthetic()) continue;
                    try {
                        Value value = type.getValue(field);
                        MemoryValue memoryValue = heap.value(value, 0);
                        statics.add(new StaticFieldSnapshot(type.name(), field.name(), field.typeName(), memoryValue));
                    } catch (RuntimeException ignored) {
                        // A class may not have completed initialization, or a field may be unavailable in this VM.
                        heap.markTruncated("unavailable_static_field");
                    }
                }
            }
            heap.expand();
            return new HeapSnapshot(List.copyOf(heap.objects.values()), List.copyOf(statics),
                    heap.truncated, heap.truncatedReason);
        }

        /** Per-event builder: identity IDs preserve aliases/cycles while limits bound the walk. */
        private final class HeapBuilder {
            private final Queue<PendingObject> pending = new ArrayDeque<>();
            private final Map<Long, HeapObjectSnapshot> objects = new LinkedHashMap<>();
            private boolean truncated;
            private String truncatedReason;

            private void enqueue(ObjectReference object, int depth) {
                if (object == null || object instanceof StringReference) return;
                pending.add(new PendingObject(object, depth));
            }

            private MemoryValue value(Value value, int depth) {
                if (value == null) return new MemoryValue("null", "null", null);
                if (value instanceof StringReference string) {
                    return new MemoryValue("java.lang.String", string.value(), null);
                }
                if (value instanceof ObjectReference object) {
                    String id = Long.toString(object.uniqueID());
                    enqueue(object, depth);
                    return new MemoryValue(object.referenceType().name(), displayValue(object), id);
                }
                return new MemoryValue(value.type().name(), value.toString(), null);
            }

            private void expand() {
                while (!pending.isEmpty()) {
                    PendingObject item = pending.remove();
                    ObjectReference object = item.object();
                    long id = object.uniqueID();
                    if (objects.containsKey(id)) continue; // repeated references and cycles share one node
                    if (objects.size() >= maxHeapObjects) {
                        markTruncated("max_heap_objects");
                        continue;
                    }

                    String className = object.referenceType().name();
                    if (item.depth() > maxHeapDepth) {
                        objects.put(id, new HeapObjectSnapshot(Long.toString(id), className,
                                kind(object), List.of(), List.of(), object instanceof ArrayReference array ? array.length() : -1,
                                false));
                        markTruncated("max_heap_depth");
                        continue;
                    }

                    if (object instanceof ArrayReference array) {
                        int length = array.length();
                        int captured = Math.min(length, maxArrayElements);
                        List<MemoryValue> elements = new ArrayList<>(captured);
                        try {
                            for (Value element : array.getValues(0, captured)) elements.add(value(element, item.depth() + 1));
                        } catch (RuntimeException ignored) {
                            markTruncated("unavailable_array_elements");
                        }
                        boolean complete = captured == length;
                        if (!complete) markTruncated("max_array_elements");
                        objects.put(id, new HeapObjectSnapshot(Long.toString(id), className, "array",
                                List.of(), List.copyOf(elements), length, complete));
                        continue;
                    }

                    List<HeapFieldSnapshot> fields = new ArrayList<>();
                    boolean complete = true;
                    for (Field field : object.referenceType().allFields()) {
                        if (field.isStatic()) continue;
                        try {
                            fields.add(new HeapFieldSnapshot(field.name(), field.typeName(),
                                    value(object.getValue(field), item.depth() + 1)));
                        } catch (RuntimeException ignored) {
                            complete = false; // unavailable VM/private-field metadata is reported as a partial node
                            markTruncated("unavailable_fields");
                        }
                    }
                    objects.put(id, new HeapObjectSnapshot(Long.toString(id), className, kind(object),
                            List.copyOf(fields), List.of(), -1, complete));
                }
            }

            private void markTruncated(String reason) {
                truncated = true;
                if (truncatedReason == null) truncatedReason = reason;
            }

            private String kind(ObjectReference object) {
                if (object instanceof ArrayReference) return "array";
                String name = object.referenceType().name();
                return name.startsWith("java.util.") || name.startsWith("java.util.concurrent.")
                        ? "collection" : "object";
            }
        }

        private record PendingObject(ObjectReference object, int depth) {}

        private static String referenceId(Value value) {
            return value instanceof ObjectReference object && !(value instanceof StringReference)
                    ? Long.toString(object.uniqueID()) : null;
        }

        private boolean isTraceThread(ThreadReference thread) {
            return traceThreadId >= 0 && thread.uniqueID() == traceThreadId;
        }

        private static void addUserCodeExclusions(java.util.function.Consumer<String> addFilter) {
            for (String pattern : EXCLUDED_CLASSES) addFilter.accept(pattern);
        }

        /** Converts mirrored values without calling methods on objects in the debuggee. */
        private static String displayValue(Value value) {
            if (value instanceof StringReference string) return string.value();
            if (value instanceof ObjectReference object) {
                return object.referenceType().name() + "@" + object.uniqueID();
            }
            return value.toString();
        }

        private void truncate(String reason) {
            truncated = true;
            truncatedReason = reason;
        }

        private static boolean isUserClass(String name) {
            if (name.contains("$$Lambda")) return false;     // JVM-generated lambda helper, no source lines
            for (String p : EXCLUDED_CLASSES) {
                if (name.startsWith(p.substring(0, p.length() - 1))) return false;   // "java.*" -> "java."
            }
            return true;
        }

        /** Reads Throwable.detailMessage directly (no method call needed on the paused program). */
        private static String exceptionMessage(ObjectReference ex) {
            ReferenceType t = ex.referenceType();
            while (t instanceof ClassType ct && !t.name().equals("java.lang.Throwable")) {
                t = ct.superclass();
            }
            if (t == null) return null;
            Field f = t.fieldByName("detailMessage");
            if (f == null) return null;
            Value v = ex.getValue(f);
            return v instanceof StringReference s ? s.value() : null;
        }
    }

    // =====================================================================================
    // Reads the program's stdout/stderr pipe WITHOUT blocking (only what is already available).
    // =====================================================================================
    private static final class OutputPump {
        private final InputStream in;
        private final boolean keep;
        private final int maxChars;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private String cache = "";
        private boolean dirty;

        OutputPump(InputStream in, boolean keep, int maxChars) {
            this.in = in;
            this.keep = keep;
            this.maxChars = maxChars;
        }

        /** Read whatever is available right now, never wait. */
        void drain() {
            try {
                int n;
                while ((n = in.available()) > 0) {
                    if (!readChunk(Math.min(n, 8192))) break;
                }
            } catch (IOException ignored) {
                // pipe closed
            }
        }

        /** Read until end of stream. Only call this after the process has exited. */
        void drainToEof() {
            try {
                while (readChunk(8192)) {
                    // keep reading
                }
            } catch (IOException ignored) {
                // pipe closed
            }
        }

        private boolean readChunk(int size) throws IOException {
            byte[] b = new byte[size];
            int r = in.read(b);
            if (r < 0) return false;
            if (keep && buffer.size() < MAX_CAPTURE_BYTES) {
                buffer.write(b, 0, Math.min(r, MAX_CAPTURE_BYTES - buffer.size()));
                dirty = true;
            }
            return true;
        }

        /** Everything captured so far, cut to the configured stdout character limit. */
        String text() {
            if (dirty) {
                String s = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                cache = s.length() > maxChars ? s.substring(0, maxChars) : s;
                dirty = false;
            }
            return cache;
        }
    }
}
