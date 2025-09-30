package github.jackutil.compiler.runtime.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import github.jackutil.compiler.ConfigValidationException;
import github.jackutil.compiler.diagnostics.MappingDiagnostic;
import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.ir.EngineConfig;
import github.jackutil.compiler.ir.SchemaDef;
import github.jackutil.compiler.ir.enums.ValidationPhase;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedValidationRule;

/**
 * Validates execution results against configured JSON Schemas.
 */
public final class ResultValidator {
    private static final ResultValidator NO_OP = new ResultValidator(null, List.of());

    private final ObjectMapper mapper;
    private final List<SchemaCheck> checks;

    private ResultValidator(ObjectMapper mapper, List<SchemaCheck> checks) {
        this.mapper = mapper;
        this.checks = checks;
    }

    public static ResultValidator create(ResolvedConfig config, ObjectMapper mapper) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(mapper, "mapper");

        Map<Integer, SchemaDef> schemaById = new HashMap<>();
        Map<String, SchemaDef> schemaByName = new HashMap<>();
        for (SchemaDef schema : config.schemas()) {
            schemaById.put(schema.id(), schema);
            schemaByName.put(schema.name(), schema);
        }

        List<SchemaCheck> checks = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ResultValidator.class.getClassLoader();
        }

        EngineConfig engine = config.engine();
        if (engine != null && engine.validationRef() != null && !engine.validationRef().isBlank()) {
            SchemaDef schema = schemaByName.get(schemaNameFromRef(engine.validationRef()));
            if (schema == null) {
                throw new ConfigValidationException("ENGINE.validation references unknown schema: " + engine.validationRef());
            }
            checks.add(buildSchemaCheck(schema, "/ENGINE/validation", mapper, loader));
        }

        for (ResolvedValidationRule rule : config.validations()) {
            if (rule.phase() != ValidationPhase.POST_MAP) {
                continue;
            }
            SchemaDef schema = schemaById.get(rule.schemaId());
            if (schema == null) {
                throw new ConfigValidationException("VALIDATION." + rule.name() + " references unknown schema id: " + rule.schemaId());
            }
            checks.add(buildSchemaCheck(schema, "/VALIDATION/" + rule.name(), mapper, loader));
        }

        if (checks.isEmpty()) {
            return NO_OP;
        }
        return new ResultValidator(mapper, List.copyOf(checks));
    }

    public boolean hasChecks() {
        return !checks.isEmpty();
    }

    public void validate(Map<String, Object> output) {
        if (checks.isEmpty()) {
            return;
        }
        JsonNode node = mapper.valueToTree(output);
        for (SchemaCheck check : checks) {
            Set<ValidationMessage> messages;
            try {
                messages = check.schema().validate(node);
            } catch (RuntimeException ex) {
                throw failure(check, "Schema validation error: " + ex.getMessage(), Collections.emptySet());
            }
            if (!messages.isEmpty()) {
                throw failure(check, "Schema validation failed", messages);
            }
        }
    }

    private MappingException failure(SchemaCheck check, String message, Set<ValidationMessage> messages) {
        List<Map<String, String>> errors = new ArrayList<>(messages.size());
        messages.stream()
            .sorted(Comparator.comparing(ValidationMessage::getPath))
            .forEach(msg -> {
                Map<String, String> entry = new LinkedHashMap<>(2);
                entry.put("path", msg.getPath());
                entry.put("message", msg.getMessage());
                errors.add(entry);
            });
        Map<String, Object> details = Map.of(
            "schema", check.schemaName(),
            "ref", check.schemaRef(),
            "errors", errors
        );
        String description = message + " for schema '" + check.schemaName() + "'";
        MappingDiagnostic diagnostic = new MappingDiagnostic(
            "RESULT_SCHEMA_VALIDATION",
            description,
            check.pointer(),
            details
        );
        return new MappingException(diagnostic);
    }

    private static SchemaCheck buildSchemaCheck(SchemaDef schema,
                                                String pointer,
                                                ObjectMapper mapper,
                                                ClassLoader loader) {
        JsonNode schemaNode = readSchemaNode(schema, mapper, loader);
        JsonSchema jsonSchema = compileSchema(schemaNode, schema.dialect());
        return new SchemaCheck(schema.name(), schema.ref(), pointer, jsonSchema);
    }

    private static JsonNode readSchemaNode(SchemaDef schema, ObjectMapper mapper, ClassLoader loader) {
        try (InputStream in = openSchemaStream(schema.ref(), loader)) {
            if (in == null) {
                throw new ConfigValidationException("Unable to resolve schema reference: " + schema.ref());
            }
            return mapper.readTree(in);
        } catch (IOException ex) {
            throw new ConfigValidationException("Failed to read schema '" + schema.name() + "': " + ex.getMessage());
        }
    }

    private static InputStream openSchemaStream(String ref, ClassLoader loader) throws IOException {
        if (ref == null || ref.isBlank()) {
            throw new IOException("Schema reference is blank");
        }
        if (ref.startsWith("classpath:")) {
            String resource = ref.substring("classpath:".length());
            InputStream in = loader.getResourceAsStream(resource);
            if (in == null) {
                throw new IOException("Classpath resource not found: " + resource);
            }
            return in;
        }
        if (ref.startsWith("file:")) {
            Path path = Path.of(ref.substring("file:".length()));
            return Files.newInputStream(path);
        }
        Path path = Path.of(ref);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }
        InputStream in = loader.getResourceAsStream(ref);
        if (in != null) {
            return in;
        }
        throw new IOException("Unsupported schema reference: " + ref);
    }

    private static JsonSchema compileSchema(JsonNode schemaNode, String dialect) {
        SpecVersion.VersionFlag version = detectVersion(schemaNode, dialect);
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(version);
        return factory.getSchema(schemaNode);
    }

    private static SpecVersion.VersionFlag detectVersion(JsonNode schemaNode, String dialect) {
        String candidate = dialect;
        if (candidate == null || candidate.isBlank()) {
            JsonNode schemaField = schemaNode.get("$schema");
            if (schemaField != null && schemaField.isTextual()) {
                candidate = schemaField.asText();
            }
        }
        if (candidate == null) {
            return SpecVersion.VersionFlag.V7;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.contains("draft-04")) {
            return SpecVersion.VersionFlag.V4;
        }
        if (lower.contains("draft-06")) {
            return SpecVersion.VersionFlag.V6;
        }
        if (lower.contains("draft-07")) {
            return SpecVersion.VersionFlag.V7;
        }
        if (lower.contains("2019-09")) {
            return SpecVersion.VersionFlag.V201909;
        }
        if (lower.contains("2020-12")) {
            return SpecVersion.VersionFlag.V202012;
        }
        return SpecVersion.VersionFlag.V7;
    }

    private static String schemaNameFromRef(String reference) {
        String prefix = "$SCHEMA.";
        if (!reference.startsWith(prefix)) {
            throw new ConfigValidationException("Schema reference must start with $SCHEMA.: " + reference);
        }
        return reference.substring(prefix.length());
    }

    private record SchemaCheck(String schemaName,
                               String schemaRef,
                               String pointer,
                               JsonSchema schema) {
    }
}
