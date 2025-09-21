package github.jackutil.compiler.ir;

import github.jackutil.compiler.ir.enums.ValidationPhase;

public record ValidationRule(int id, String name, String schemaRef, ValidationPhase phase) {
}
