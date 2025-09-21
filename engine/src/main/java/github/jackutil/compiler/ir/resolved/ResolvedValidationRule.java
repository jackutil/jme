package github.jackutil.compiler.ir.resolved;

import github.jackutil.compiler.ir.enums.ValidationPhase;

public record ResolvedValidationRule(int id, String name, int schemaId, ValidationPhase phase) {
}
