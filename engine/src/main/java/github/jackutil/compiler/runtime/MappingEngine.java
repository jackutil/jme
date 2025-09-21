package github.jackutil.compiler.runtime;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;

import github.jackutil.compiler.CompiledMapping;

public final class MappingEngine {
    private final ExecutionContext context;
    private final MappingInterpreter interpreter = new MappingInterpreter();

    public MappingEngine(CompiledMapping compiledMapping) {
        this.context = new ExecutionContext(compiledMapping);
    }

    public void execute(String mappingName,
                        Map<String, Object> inputs,
                        Map<String, Object> payload,
                        JsonGenerator generator) throws IOException {
        context.bind(generator, inputs, payload);
        int index = context.mappingIndex(mappingName);
        interpreter.execute(context, index);
    }

    public Map<String, Object> variablesSnapshot() {
        return context.variableResolver().snapshotValues();
    }
}
