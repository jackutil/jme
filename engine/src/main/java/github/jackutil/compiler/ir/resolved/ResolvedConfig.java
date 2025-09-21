package github.jackutil.compiler.ir.resolved;

import java.util.List;

import github.jackutil.compiler.ir.EngineConfig;
import github.jackutil.compiler.ir.FunctionDef;
import github.jackutil.compiler.ir.Meta;
import github.jackutil.compiler.ir.SchemaDef;

public record ResolvedConfig(Meta meta,
                             EngineConfig engine,
                             List<SchemaDef> schemas,
                             List<FunctionDef> functions,
                             List<ResolvedInput> inputs,
                             List<ResolvedVariable> variables,
                             List<ResolvedMapping> mappings,
                             List<ResolvedValidationRule> validations) {
}
