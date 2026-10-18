# Java Code Memory Visualizer — backend (stages 1–8)

A Spring Boot API that parses and checks submitted Java source, compiles it with the JDK compiler, launches it in a separate JVM, and returns enriched source-line events, stack frames, variables, bounded heap snapshots, change deltas, stdout, and handled errors. The frontend is a sibling React project.

## Prerequisites

- Windows 10, JDK 21 (full JDK; source/bytecode target is Java 17), and Maven 3.8+
- Verify with `java -version`, `javac -version`, `mvn -version`, and `java --list-modules | findstr jdk.jdi`.

## Structure

This Maven project is in the monorepo's `backend/` folder. `src/main/java/com/example/demo` contains the Spring entry point; `controller/` contains the HTTP endpoints, `model/` DTO records, `service/` validation/compilation/pipeline helpers, and `tracer/` JDI execution. Keep these as physical nested directories (`com/example/demo/...`), never one folder named `com.example.demo`.

## Build and run

From this directory:

```powershell
mvn -q clean compile
mvn spring-boot:run
```

The application uses port 8080. Stop it with Ctrl+C. `ToolProvider.getSystemJavaCompiler()` requires Maven and the server to run on a full JDK.

## Eclipse 2026-09

1. Open Eclipse.
2. Select **File → Import → Maven → Existing Maven Projects**.
3. Select the `java-visualizer/backend` folder containing `pom.xml`, then finish.
4. Set JDK 21 under **Window → Preferences → Java → Installed JREs**.
5. Ensure project compiler compliance is Java 17.
6. Right-click the project and choose **Maven → Update Project**.
7. Run `VisualizerApplication.java` as a Spring Boot application.

## API

`GET /health` returns `{"status":"ok"}`.

`POST /api/visualize`, JSON body:

```json
{"code":"public class Main { public static void main(String[] args) { System.out.println(\"Hello\"); } }"}
```

The response uses camelCase and returns a handled outcome with HTTP 200. Status is `ok`, `compile_error`, `runtime_error`, `blocked_api`, `busy`, or `error`. Trace events include heap snapshots, line types, variable/heap changes, and a final `end` event. Successful responses also include AST-derived method, loop, and branch metadata. Restricted API responses include `blockedApis`. Object references use `referenceId` to link to heap entries, so cycles and repeated references do not recursively duplicate JSON. Example:

```json
{"status":"ok","message":null,"errors":null,"trace":[{"step":0,"line":1,"event":"call","stdout":"","frames":[{"method":"main","className":"Main","returnType":"void","line":1,"variables":[{"name":"args","type":"java.lang.String[]","value":"[Ljava.lang.String;@<id>","kind":"parameter"}]}],"explanation":"Entered method main.","returnValue":null}],"truncated":false,"truncatedReason":null,"exception":null,"stdout":"Hello\n"}
```

Submit a sample while the server is running:

```powershell
curl.exe -X POST http://localhost:8080/api/visualize -H "Content-Type: application/json" --data-binary "@samples/largest.json"
```

## Functionality and settings

The pipeline validates source, uses JavaParser for method/loop/branch metadata and an AST-aware API blocklist, compiles user code in-process with debug metadata targeting Java 17, and traces in a separate JDI debuggee JVM. Stack frames include parameters, initialized visible locals, `this`, call/return events, explanations, and return values when supported. The bounded heap follows arrays, user objects, collection internals, static fields, repeated references, and cycles without invoking debuggee methods. Trace enrichment classifies source lines and emits variable changes, heap object/field/array before-and-after deltas, and a final `end` event. Per-client request limits and a concurrent-debuggee semaphore return the handled `busy` status when admission fails. Temporary work and debuggee processes are cleaned after each request.

Limits and deployment settings are configured in `src/main/resources/application.properties`: source length 20,000, steps 2,000, stdout 20,000 characters, timeout 10,000 ms, displayed frames 50, heap objects 500, array elements per array 100, heap depth 8, concurrency 4, and 60 requests per IP per 60 seconds. CORS defaults to the local Vite origins and can be configured using `visualizer.cors.allowed-origins`. Partial graph captures report `heap.truncated` and `heap.truncatedReason`.

The `samples/` files are illustrative requests. Run `mvn -q test` for the unit and integration suite. The full six-sample matrix and a browser-to-backend walkthrough remain separate acceptance checks.

## Limitations and safety

The blocklist is defense in depth only. Arbitrary Java execution is unsafe without proper OS/container sandboxing. The provided container runs the app as a non-root user with a full JDK because the service needs both `javac` and `jdk.jdi`; production deployments must additionally restrict network egress, CPU, memory, process count, and writable paths. CORS and rate limits do not make arbitrary code safe.

## Container build and run

From `backend/`, build the image with `docker build -t java-visualizer-backend .`, then run it with `docker run --rm -p 8080:8080 --read-only --tmpfs /tmp:rw,noexec,nosuid,size=256m --memory=1g --cpus=2 --pids-limit=128 --cap-drop=ALL --security-opt=no-new-privileges java-visualizer-backend`. The debuggee JVM is a child of this process, so resource caps apply to the whole container. Configure network policy at the host/orchestrator so user programs cannot reach untrusted services; do not treat the image alone as a secure sandbox.

## Tests

`mvn -q test` runs source-helper, AST/blocklist, trace-enrichment, request-admission, and Spring MVC/JDI integration tests. The integration suite needs a full JDK 21.

## Project documentation

See [document.markdown](documentation/document.markdown) for backend product and technical documentation with an append-only change history, and [plan.markdown](documentation/plan.markdown) for backend stages. Frontend documentation is in ../frontend/documentation/.
