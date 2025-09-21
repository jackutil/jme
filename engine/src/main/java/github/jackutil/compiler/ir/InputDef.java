package github.jackutil.compiler.ir;

import github.jackutil.compiler.ir.enums.ValueType;

/**
 * Declarative definition of an input value supplied by the host system.
 */
public record InputDef(int id,
                       String name,
                       ValueType type,
                       boolean required,
                       boolean nullable,
                       Object defaultValue) {
}
