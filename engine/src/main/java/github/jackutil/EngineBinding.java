package github.jackutil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.ConfigCompiler;
import github.jackutil.compiler.ConfigValidationException;
import github.jackutil.compiler.ConfigValidator;
import github.jackutil.compiler.ir.resolved.ResolvedInput;
import github.jackutil.compiler.runtime.MappingEngine;
import github.jackutil.compiler.runtime.validation.ResultValidator;

public final class EngineBinding {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final CompiledMapping compiled;
    private final MappingEngine engine;
    private final ResultValidator resultValidator;

    private EngineBinding(CompiledMapping compiled) {
        this.compiled = compiled;
        this.engine = new MappingEngine(compiled);
        this.resultValidator = ResultValidator.create(compiled.config(), MAPPER);
    }

    public static EngineBinding fromPath(Path configPath) throws IOException {
        Objects.requireNonNull(configPath, "configPath");
        return fromBytes(Files.readAllBytes(configPath));
    }

    public static EngineBinding fromStream(InputStream stream) throws IOException {
        Objects.requireNonNull(stream, "stream");
        return fromBytes(stream.readAllBytes());
    }

    public static EngineBinding fromBytes(byte[] configBytes) {
        Objects.requireNonNull(configBytes, "configBytes");
        byte[] copy = configBytes.clone();
        validateConfig(copy);
        CompiledMapping compiled = compileConfig(copy);
        return new EngineBinding(compiled);
    }

    public ExecutionResult execute(String mappingName,
                                   Map<String, Object> inputs,
                                   Map<String, Object> payload) throws IOException {
        return execute(mappingName, inputs, payload, ValidationMode.ENABLED);
    }

    public ExecutionResult execute(String mappingName,
                                   Map<String, Object> inputs,
                                   Map<String, Object> payload,
                                   ValidationMode validationMode) throws IOException {
        Objects.requireNonNull(mappingName, "mappingName");
        ValidationMode mode = validationMode != null ? validationMode : ValidationMode.ENABLED;
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();
        Map<String, Object> safePayload = payload != null ? payload : Map.of();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JsonGenerator generator = MAPPER.getFactory().createGenerator(buffer)) {
            engine.execute(mappingName, safeInputs, safePayload, generator);
            generator.flush();
        }
        Map<String, Object> output = buffer.size() == 0
            ? Map.of()
            : Map.copyOf(MAPPER.readValue(buffer.toByteArray(), MAP_TYPE));
        if (mode == ValidationMode.ENABLED) {
            resultValidator.validate(output);
        }
        String variablesJson = MAPPER.writeValueAsString(engine.variablesSnapshot());
        return new ExecutionResult(compiled, output, variablesJson);
    }

    public CompiledMapping compiled() {
        return compiled;
    }

    public Set<String> inputFields() {
        List<ResolvedInput> inputs = compiled.config().inputs();
        if (inputs == null || inputs.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>(inputs.size());
        for (ResolvedInput input : inputs) {
            names.add(input.name());
        }
        return Set.copyOf(names);
    }

    private static void validateConfig(byte[] configBytes) {
        try (InputStream in = new ByteArrayInputStream(configBytes)) {
            ConfigValidator.validate(in);
        } catch (IllegalArgumentException | UncheckedIOException | IOException ex) {
            throw new ConfigValidationException(ex.getMessage());
        }
    }

    private static CompiledMapping compileConfig(byte[] configBytes) {
        try (InputStream in = new ByteArrayInputStream(configBytes)) {
            return ConfigCompiler.compile(in);
        } catch (IOException e){
            throw new RuntimeException("Unable to compile mapping: ", e);
        }
    }

    public record ExecutionResult(CompiledMapping compiledMapping,
                                  Map<String, Object> output,
                                  String variablesJson) {

        public String prettyOutput() {
            try {
                return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to pretty print execution output", ex);
            }
        }
    }

    public enum ValidationMode {
        ENABLED,
        DISABLED
    }
}

