package github.jackutil.compiler.ir;

import java.util.List;

/** IR aggregate describing the compiled configuration metadata. */
public record ConfigModel(Meta meta,
                          EngineConfig engine,
                          List<SchemaDef> schemas,
                          List<FunctionDef> functions,
                          List<InputDef> inputs,
                          List<VariableDef> variables,
                          List<MappingDef> mappings,
                          List<ValidationRule> validations) {

    public static ConfigModel empty() {
        return new ConfigModel(
            Meta.empty(),
            EngineConfig.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }
}
