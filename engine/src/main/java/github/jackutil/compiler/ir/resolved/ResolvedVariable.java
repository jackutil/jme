package github.jackutil.compiler.ir.resolved;

import java.util.List;

import github.jackutil.compiler.ir.enums.ValueType;

public record ResolvedVariable(int id,
                               String name,
                               ValueType type,
                               boolean required,
                               boolean nullable,
                               int[] constraintFunctionIds,
                               Integer deriveFunctionId,
                               List<Object> deriveArgs,
                               Object defaultValue) {
}
