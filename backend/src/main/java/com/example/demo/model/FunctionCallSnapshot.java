package com.example.demo.model;

/**
 * One call to a user-written method or constructor, shown inside the caller's frame.
 *
 * @param line             first source line of the call
 * @param expression       call with argument values, e.g. {@code result = fun_add(0, 4)}, or after return
 *                         {@code result = 4 <= fun_add(0, 4)}
 * @param state            {@code evaluating} while the callee runs, {@code returned} after it returns
 * @param returnValue      returned value (constructors: the new object), null while evaluating or for void
 * @param sourceExpression call as written in the source, e.g. {@code result = fun_add(result, number)}
 */
public record FunctionCallSnapshot(int line, String expression, String state, String returnValue,
                                   String sourceExpression) {}
