package github.jackutil.compiler.ir.resolved;

import github.jackutil.compiler.ir.enums.ValueType;

public record ResolvedInput(int id,
                            String name,
                            ValueType type,
                            boolean required,
                            boolean nullable,
                            Object defaultValue) {
}
