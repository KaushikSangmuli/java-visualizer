package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceHelperTest {
    @Test
    void detectsNestedMainAndRetainsPublicFilenameAcrossPackageRemoval() {
        String source = "package demo;\npublic class Outer {\n  static class Inner {\n    public static void main(String... args) {}\n  }\n}";
        var main = SourceHelper.findMain(SourceHelper.strip(source));
        String processed = SourceHelper.blankPackageLine(source, SourceHelper.strip(source));

        assertThat(main.mainClass()).isEqualTo("Outer$Inner");
        assertThat(main.fileName()).isEqualTo("Outer");
        assertThat(processed.lines().count()).isEqualTo(source.lines().count());
        assertThat(processed.lines().findFirst().orElseThrow()).doesNotContain("package");
    }
}
