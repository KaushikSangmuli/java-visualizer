package com.example.demo.model;

import java.util.List;

/**
 * A loop that is currently running in a frame, shown as a box inside that frame.
 *
 * @param kind            {@code for}, {@code for-each}, {@code while} or {@code do-while}
 * @param startLine       first line of the loop statement
 * @param endLine         last line of the loop statement
 * @param headerLine      line holding the loop condition ({@code while (...)} line for do-while)
 * @param condition       ending condition as written, e.g. {@code i < times}; for-each: {@code x : arr}
 * @param conditionValues condition with current values, e.g. {@code 1 < 2}; for-each: {@code 2 of 5}
 * @param conditionResult true/false while the condition is being checked on this step, otherwise null
 * @param iteration       1-based number of the iteration that is running; 0 before the body first runs
 * @param activationStep  trace step where this run of the loop started; identifies the run for history
 * @param variables       names of variables declared by the loop (header and body, not nested loops)
 * @param loops           loop nested inside this one that is also running
 */
public record LoopSnapshot(String kind, int startLine, int endLine, int headerLine, String condition,
                           String conditionValues, Boolean conditionResult, int iteration, int activationStep,
                           List<String> variables, List<LoopSnapshot> loops) {}
