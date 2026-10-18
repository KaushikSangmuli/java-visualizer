package com.example.demo.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small text helpers that work on the Java source BEFORE compiling.
 * (Stage 5 adds the full CodeInspector with JavaParser; this is the minimum Stage 1 needs.)
 *
 * Key idea: strip() returns a copy of the code with comments and string contents replaced by
 * spaces. The copy has EXACTLY the same length and the same line breaks, so a position found
 * in the stripped text is the same position in the original text, and line numbers never shift.
 */
public final class SourceHelper {

    private SourceHelper() {}

    /** Result of findMain: which class to run, and what the .java file must be called. */
    public record MainInfo(String mainClass, String fileName) {}

    // ------------------------------------------------------------------ strip

    public static String strip(String src) {
        char[] out = src.toCharArray();
        int n = out.length;
        int i = 0;
        while (i < n) {
            char c = out[i];
            if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                // line comment: blank until end of line
                while (i < n && out[i] != '\n' && out[i] != '\r') blank(out, i++);
            } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                // block comment: blank everything (keep newlines) up to and including */
                blank(out, i++);
                blank(out, i++);
                while (i < n && !(out[i] == '*' && i + 1 < n && out[i + 1] == '/')) blank(out, i++);
                if (i < n) blank(out, i++);
                if (i < n) blank(out, i++);
            } else if (c == '"' && i + 2 < n && out[i + 1] == '"' && out[i + 2] == '"') {
                // text block """ ... """ : keep the delimiters, blank the content
                i += 3;
                while (i < n && !(out[i] == '"' && i + 2 < n && out[i + 1] == '"' && out[i + 2] == '"')) {
                    if (out[i] == '\\' && i + 1 < n) blank(out, i++);
                    blank(out, i++);
                }
                i = Math.min(n, i + 3);
            } else if (c == '"' || c == '\'') {
                // normal string or char literal: keep the quotes, blank the content
                char quote = c;
                i++;
                while (i < n && out[i] != quote && out[i] != '\n' && out[i] != '\r') {
                    if (out[i] == '\\' && i + 1 < n && out[i + 1] != '\n' && out[i + 1] != '\r') blank(out, i++);
                    blank(out, i++);
                }
                if (i < n && out[i] == quote) i++;
            } else {
                i++;
            }
        }
        return new String(out);
    }

    private static void blank(char[] a, int i) {
        if (a[i] != '\n' && a[i] != '\r') a[i] = ' ';
    }

    // ------------------------------------------------------------------ main class

    /**
     * One regex that finds, in order of position: "{", "}", a "public static void main(String[] x)"
     * signature, or a class/interface/enum/record declaration (with its modifiers).
     * We walk through the matches and keep a stack of the classes we are currently inside,
     * so a main() inside Outer.Inner gets the binary name "Outer$Inner".
     */
    private static final Pattern TOKEN = Pattern.compile(
            "(?<open>\\{)|(?<close>\\})"
                    + "|(?<main>\\b(?:public\\s+static|static\\s+public)\\s+void\\s+main\\s*\\(\\s*(?:final\\s+)?String"
                    + "(?:\\s*\\[\\s*\\]\\s*\\w+|\\s*\\.\\.\\.\\s*\\w+|\\s+\\w+\\s*\\[\\s*\\])\\s*\\))"
                    + "|(?<type>(?<mods>(?:\\b(?:public|protected|private|abstract|final|static|sealed|strictfp)\\s+)*)"
                    + "\\b(?:class|interface|enum|record)\\s+(?<name>[A-Za-z_$][\\w$]*))");

    private record Frame(String name, int depth) {}

    /** @param stripped output of strip(). Returns null if there is no main method. */
    public static MainInfo findMain(String stripped) {
        Matcher m = TOKEN.matcher(stripped);
        Deque<Frame> stack = new ArrayDeque<>();           // classes we are inside (top = innermost)
        Map<String, Boolean> topLevel = new LinkedHashMap<>(); // top-level class name -> is it public?
        List<String[]> candidates = new ArrayList<>();      // {binaryName, topLevelName}
        int depth = 0;
        String pending = null;                              // class name waiting for its "{"

        while (m.find()) {
            if (m.group("open") != null) {
                depth++;
                if (pending != null) {
                    stack.push(new Frame(pending, depth));
                    pending = null;
                }
            } else if (m.group("close") != null) {
                if (!stack.isEmpty() && stack.peek().depth() == depth) stack.pop();
                depth--;
            } else if (m.group("main") != null) {
                if (!stack.isEmpty()) {
                    StringBuilder binary = new StringBuilder();
                    String top = null;
                    for (var it = stack.descendingIterator(); it.hasNext(); ) {   // outermost first
                        String name = it.next().name();
                        if (top == null) top = name;
                        if (binary.length() > 0) binary.append('$');
                        binary.append(name);
                    }
                    candidates.add(new String[] {binary.toString(), top});
                }
            } else {
                pending = m.group("name");
                if (stack.isEmpty()) topLevel.put(pending, m.group("mods").contains("public"));
            }
        }

        if (candidates.isEmpty()) return null;

        // Prefer a main that lives in the public top-level class; otherwise take the first one.
        String[] chosen = candidates.get(0);
        for (String[] c : candidates) {
            if (Boolean.TRUE.equals(topLevel.get(c[1]))) {
                chosen = c;
                break;
            }
        }
        // Java requires the file to be named after the public top-level class (if there is one).
        String fileName = chosen[1];
        for (Map.Entry<String, Boolean> e : topLevel.entrySet()) {
            if (e.getValue()) {
                fileName = e.getKey();
                break;
            }
        }
        return new MainInfo(chosen[0], fileName);
    }

    // ------------------------------------------------------------------ package line

    private static final Pattern PACKAGE_LINE = Pattern.compile("^[ \\t]*package\\s+[\\w.]+\\s*;", Pattern.MULTILINE);

    /**
     * Replaces "package a.b.c;" with spaces (newlines kept) so the class compiles into the
     * default package and every line number stays the same.
     */
    public static String blankPackageLine(String original, String stripped) {
        Matcher m = PACKAGE_LINE.matcher(stripped);
        if (!m.find()) return original;
        char[] out = original.toCharArray();
        for (int i = m.start(); i < m.end(); i++) blank(out, i);
        return new String(out);
    }
}
