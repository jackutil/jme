package github.jackutil.compiler.ir;

import java.util.List;

import github.jackutil.compiler.ir.enums.FunctionKind;

/** Compiled representation of a reusable function. */
public record FunctionDef(int id,
                          String name,
                          FunctionKind kind,
                          Object payload,
                          List<Object> args,
                          String description) {
}
