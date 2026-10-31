package com.example.demo.service;

import org.junit.jupiter.api.Test;
import com.example.demo.model.MethodMetadata;

import static org.assertj.core.api.Assertions.assertThat;

class CodeInspectorTest {
    private final CodeInspector inspector = new CodeInspector();

    @Test
    void extractsMethodsLoopsAndBranchesFromAst() {
        var result = inspector.inspect("""
                package sample;
                public class Demo {
                  static int sum(int a, int b) { return a + b; }
                  public static void main(String[] args) {
                    for (int i = 0; i < 2; i++) {
                      if (i == 1) System.out.println(sum(i, 2));
                    }
                  }
                }
                """);

        assertThat(result.blockedApis()).isEmpty();
        assertThat(result.metadata().methods()).extracting(MethodMetadata::name).contains("main", "sum");
        assertThat(result.metadata().loops()).hasSize(1);
        assertThat(result.metadata().branches()).hasSize(1);
    }

    @Test
    void blocksDangerousCallsButIgnoresCommentsAndStringContents() {
        var allowed = inspector.inspect("""
                public class Demo {
                  // Runtime.getRuntime().exec(command);
                  public static void main(String[] args) {
                    String text = "System.exit(0)";
                    System.out.println(text);
                  }
                }
                """);
        var blocked = inspector.inspect("""
                public class Demo {
                  public static void main(String[] args) {
                    Runtime.getRuntime().exec("echo blocked");
                  }
                }
                """);

        assertThat(allowed.blockedApis()).isEmpty();
        assertThat(blocked.blockedApis()).anySatisfy(api -> {
            assertThat(api.line()).isEqualTo(3);
            assertThat(api.api()).contains("exec");
        });
    }

    @Test
    void blocksEnvironmentAccessAndMethodReferences() {
        var result = inspector.inspect("""
                public class Demo {
                  public static void main(String[] args) {
                    String secret = System.getenv("DB_PASSWORD");
                    java.util.function.Function<String, String> env = System::getenv;
                    java.util.function.IntConsumer stop = System::exit;
                  }
                }
                """);

        assertThat(result.blockedApis()).extracting(api -> api.api())
                .contains("System.getenv", "System::getenv", "System::exit");
    }
}
