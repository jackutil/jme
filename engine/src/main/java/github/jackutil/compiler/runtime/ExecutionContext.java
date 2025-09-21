package github.jackutil.compiler.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.ir.InstructionProgram;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedMapping;

final class ExecutionContext {
    private final ResolvedConfig config;
    private final InstructionProgram program;
    private final FunctionExecutor functions;
    private final InputResolver inputResolver;
    private final VariableResolver variableResolver;
    private final Map<String, Integer> mappingIndex = new HashMap<>();

    private JsonGenerator generator;

    ExecutionContext(CompiledMapping compiledMapping) {
        this(compiledMapping.config(), compiledMapping.program().program());
    }

    ExecutionContext(ResolvedConfig config, InstructionProgram program) {
        this.config = config;
        this.program = program;
        this.functions = new FunctionExecutor(config.functions());
        this.inputResolver = new InputResolver(config.inputs());
        this.variableResolver = new VariableResolver(config.variables(), functions);
        initMappingIndex(config.mappings());
    }

    private void initMappingIndex(List<ResolvedMapping> mappings) {
        for (ResolvedMapping mapping : mappings) {
            mappingIndex.put(mapping.name(), mapping.id());
        }
    }

    void bind(JsonGenerator generator, Map<String, Object> inputs, Map<String, Object> payload) {
        this.generator = generator;
        inputResolver.bindInputs(inputs);
        variableResolver.bindPayload(payload);
    }

    JsonGenerator generator() {
        return generator;
    }

    InstructionProgram program() {
        return program;
    }

    InputResolver inputResolver() {
        return inputResolver;
    }

    VariableResolver variableResolver() {
        return variableResolver;
    }

    FunctionExecutor functions() {
        return functions;
    }

    ResolvedConfig config() {
        return config;
    }

    int mappingIndex(String name) {
        Integer idx = mappingIndex.get(name);
        if (idx == null) {
            throw MappingException.of("MAPPING_UNKNOWN", "Unknown mapping: " + name, "/MAPPINGS/" + name);
        }
        return idx;
    }
}
