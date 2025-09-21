package github.jackutil.schema;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.jackutil.compiler.ConfigValidationException;
import github.jackutil.compiler.ConfigValidator;

final class SchemaGenerator {
    private static final String JSON_SCHEMA_DRAFT7 = "http://json-schema.org/draft-07/schema#";

    private final ObjectMapper mapper;

    SchemaGenerator(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    ObjectNode generate(Path configPath) throws IOException {
        Objects.requireNonNull(configPath, "configPath");
        byte[] configBytes = Files.readAllBytes(configPath);
        validateConfig(configBytes);
        JsonNode root = mapper.readTree(configBytes);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Configuration root must be a JSON object");
        }

        Map<String, FunctionDescriptor> functions = FunctionDescriptor.from(root.path("FUNCTIONS"));
        JsonNode variablesNode = root.get("VARIABLES");
        if (variablesNode == null || !variablesNode.isObject()) {
            throw new IllegalArgumentException("VARIABLES section is missing or is not an object");
        }

        ObjectNode schema = mapper.createObjectNode();
        schema.put("$schema", JSON_SCHEMA_DRAFT7);
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        JsonNode metaNode = root.get("META");
        if (metaNode != null && metaNode.isObject()) {
            JsonNode nameNode = metaNode.get("name");
            if (nameNode != null && nameNode.isTextual()) {
                schema.put("title", nameNode.asText());
            }
            JsonNode descriptionNode = metaNode.get("description");
            if (descriptionNode != null && descriptionNode.isTextual()) {
                schema.put("description", descriptionNode.asText());
            }
            JsonNode targetAspectNode = metaNode.get("targetAspect");
            if (targetAspectNode != null && targetAspectNode.isTextual()) {
                schema.put("x-targetAspect", targetAspectNode.asText());
            }
        }

        ObjectNode propertiesNode = schema.putObject("properties");
        ArrayNode requiredArray = mapper.createArrayNode();

        Iterator<Map.Entry<String, JsonNode>> variables = variablesNode.fields();
        while (variables.hasNext()) {
            Map.Entry<String, JsonNode> entry = variables.next();
            String variableName = entry.getKey();
            JsonNode variableDef = entry.getValue();
            ObjectNode propertySchema = convertVariable(variableName, variableDef, functions);
            propertiesNode.set(variableName, propertySchema);
            if (isRequiredVariable(variableDef)) {
                requiredArray.add(variableName);
            }
        }

        if (requiredArray.size() > 0) {
            schema.set("required", requiredArray);
        }

        return schema;
    }

    private void validateConfig(byte[] configBytes) throws IOException {
        try (InputStream stream = new ByteArrayInputStream(configBytes)) {
            ConfigValidator.validate(stream);
        } catch (ConfigValidationException ex) {
            throw new IllegalArgumentException("Configuration invalid: " + ex.getMessage(), ex);
        } catch (UncheckedIOException ex) {
            IOException cause = ex.getCause();
            if (cause != null) {
                throw cause;
            }
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private ObjectNode convertVariable(String name,
                                       JsonNode definition,
                                       Map<String, FunctionDescriptor> functions) {
        if (definition == null || !definition.isObject()) {
            throw new IllegalArgumentException("VARIABLES." + name + " must be an object");
        }

        String rawType = definition.path("type").asText(null);
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("VARIABLES." + name + " is missing required field 'type'");
        }
        String type = rawType.trim().toLowerCase();
        boolean nullable = definition.path("nullable").asBoolean(false);

        ObjectNode schema = mapper.createObjectNode();
        String baseTypeForConstraints = type;

        switch (type) {
            case "string" -> {
                setType(schema, "string", nullable);
                applyStringFacets(schema, definition, nullable);
            }
            case "number", "integer", "boolean" -> setType(schema, type, nullable);
            case "object" -> {
                setType(schema, "object", nullable);
                JsonNode fieldsNode = definition.get("fields");
                if (fieldsNode != null && fieldsNode.isObject()) {
                    ObjectNode propertiesNode = schema.putObject("properties");
                    ArrayNode requiredNode = mapper.createArrayNode();
                    Set<String> requiredFields = readRequiredFields(definition);
                    Iterator<Map.Entry<String, JsonNode>> fields = fieldsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String fieldName = field.getKey();
                        ObjectNode fieldSchema = convertVariable(fieldName, field.getValue(), functions);
                        propertiesNode.set(fieldName, fieldSchema);
                        if (requiredFields.contains(fieldName)) {
                            requiredNode.add(fieldName);
                        }
                    }
                    schema.put("additionalProperties", false);
                    if (requiredNode.size() > 0) {
                        schema.set("required", requiredNode);
                    }
                }
            }
            case "array" -> {
                setType(schema, "array", nullable);
                if (definition.has("minItems") && definition.get("minItems").canConvertToInt()) {
                    schema.put("minItems", definition.get("minItems").asInt());
                }
                if (definition.has("maxItems") && definition.get("maxItems").canConvertToInt()) {
                    schema.put("maxItems", definition.get("maxItems").asInt());
                }
                JsonNode itemsNode = definition.get("items");
                if (itemsNode != null && !itemsNode.isMissingNode()) {
                    ObjectNode itemSchema = convertVariable(name + "[]", itemsNode, functions);
                    schema.set("items", itemSchema);
                }
            }
            case "enum" -> {
                baseTypeForConstraints = "string";
                setType(schema, "string", nullable);
                applyEnumValues(schema, definition, nullable, true);
            }
            default -> throw new IllegalArgumentException("Unsupported variable type '" + rawType + "' for " + name);
        }

        applyCommonProperties(schema, definition, functions, baseTypeForConstraints);
        return schema;
    }

    private void applyCommonProperties(ObjectNode schema,
                                       JsonNode definition,
                                       Map<String, FunctionDescriptor> functions,
                                       String baseType) {
        JsonNode descriptionNode = definition.get("description");
        if (descriptionNode != null && descriptionNode.isTextual()) {
            schema.put("description", descriptionNode.asText());
        }

        JsonNode defaultNode = definition.get("default");
        if (defaultNode != null && !defaultNode.isMissingNode()) {
            schema.set("default", defaultNode.deepCopy());
        }

        if (definition.has("derive")) {
            schema.put("readOnly", true);
        }

        applyConstraints(schema, definition, functions, baseType);

        if ("string".equals(baseType)) {
            applyEnumValues(schema, definition, definition.path("nullable").asBoolean(false), false);
        }
    }

    private void applyStringFacets(ObjectNode schema, JsonNode definition, boolean nullable) {
        if (definition.has("minLength") && definition.get("minLength").canConvertToInt()) {
            schema.put("minLength", definition.get("minLength").asInt());
        }
        if (definition.has("maxLength") && definition.get("maxLength").canConvertToInt()) {
            schema.put("maxLength", definition.get("maxLength").asInt());
        }
        JsonNode enumNode = definition.get("enum");
        if (enumNode != null && enumNode.isArray() && enumNode.size() > 0) {
            applyEnumValues(schema, enumNode, nullable);
        }
    }

    private void applyEnumValues(ObjectNode schema, JsonNode definition, boolean nullable, boolean preferValuesKey) {
        JsonNode source = preferValuesKey ? definition.get("values") : definition.get("enum");
        if ((source == null || !source.isArray() || source.isEmpty()) && preferValuesKey) {
            source = definition.get("enum");
        }
        if (source != null && source.isArray() && source.size() > 0) {
            applyEnumValues(schema, source, nullable);
        }
    }

    private void applyEnumValues(ObjectNode schema, JsonNode valuesNode, boolean nullable) {
        ArrayNode enumArray = mapper.createArrayNode();
        boolean hasNull = false;
        for (JsonNode value : valuesNode) {
            if (value.isNull()) {
                hasNull = true;
            }
            enumArray.add(value.deepCopy());
        }
        if (nullable && !hasNull) {
            enumArray.addNull();
        }
        if (enumArray.size() > 0) {
            schema.set("enum", enumArray);
        }
    }

    private void applyConstraints(ObjectNode schema,
                                  JsonNode definition,
                                  Map<String, FunctionDescriptor> functions,
                                  String baseType) {
        JsonNode constraintsNode = definition.get("constraints");
        if (constraintsNode == null || !constraintsNode.isArray() || constraintsNode.isEmpty()) {
            return;
        }

        List<String> regexPatterns = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (JsonNode constraintNode : constraintsNode) {
            if (!constraintNode.isTextual()) {
                continue;
            }
            String reference = constraintNode.asText();
            FunctionDescriptor descriptor = functions.get(reference);
            if (descriptor == null) {
                unresolved.add(reference);
                continue;
            }
            switch (descriptor.kind()) {
                case REGEX -> {
                    if ("string".equals(baseType)) {
                        regexPatterns.add(descriptor.value());
                    } else {
                        unresolved.add(reference);
                    }
                }
                default -> unresolved.add(reference);
            }
        }

        if (!regexPatterns.isEmpty()) {
            if (regexPatterns.size() == 1 && !schema.has("pattern")) {
                schema.put("pattern", regexPatterns.get(0));
            } else {
                ArrayNode allOf = schema.withArray("allOf");
                for (String pattern : regexPatterns) {
                    ObjectNode patternNode = mapper.createObjectNode();
                    patternNode.put("pattern", pattern);
                    allOf.add(patternNode);
                }
            }
        }

        if (!unresolved.isEmpty()) {
            ArrayNode extras = schema.withArray("x-constraints");
            unresolved.stream().distinct().forEach(extras::add);
        }
    }

    private boolean isRequiredVariable(JsonNode definition) {
        JsonNode requiredNode = definition.get("required");
        return requiredNode != null && requiredNode.isBoolean() && requiredNode.asBoolean();
    }

    private Set<String> readRequiredFields(JsonNode definition) {
        JsonNode requiredNode = definition.get("required");
        if (requiredNode == null || !requiredNode.isArray()) {
            requiredNode = definition.get("requiredFields");
        }
        if (requiredNode == null || !requiredNode.isArray()) {
            return Set.of();
        }
        Set<String> requiredFields = new LinkedHashSet<>();
        for (JsonNode value : requiredNode) {
            if (value.isTextual()) {
                requiredFields.add(value.asText());
            }
        }
        return requiredFields;
    }

    private void setType(ObjectNode schema, String baseType, boolean nullable) {
        if (nullable) {
            ArrayNode types = mapper.createArrayNode();
            types.add(baseType);
            types.add("null");
            schema.set("type", types);
        } else {
            schema.put("type", baseType);
        }
    }

    private record FunctionDescriptor(FunctionKind kind, String value) {
        private static Map<String, FunctionDescriptor> from(JsonNode functionsNode) {
            if (functionsNode == null || !functionsNode.isObject()) {
                return Map.of();
            }
            Map<String, FunctionDescriptor> descriptors = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> functions = functionsNode.fields();
            while (functions.hasNext()) {
                Map.Entry<String, JsonNode> entry = functions.next();
                String name = entry.getKey();
                JsonNode definition = entry.getValue();
                String type = definition.path("type").asText("");
                if (type.equalsIgnoreCase("regex")) {
                    String pattern = definition.path("pattern").asText(null);
                    if (pattern != null) {
                        FunctionDescriptor descriptor = new FunctionDescriptor(FunctionKind.REGEX, pattern);
                        descriptors.put(name, descriptor);
                        descriptors.put("$FUNCTIONS." + name, descriptor);
                    }
                } else if (type.equalsIgnoreCase("builtin")) {
                    String fn = definition.path("fn").asText(null);
                    FunctionDescriptor descriptor = new FunctionDescriptor(FunctionKind.BUILTIN, fn);
                    descriptors.put(name, descriptor);
                    descriptors.put("$FUNCTIONS." + name, descriptor);
                }
            }
            return Map.copyOf(descriptors);
        }
    }

    private enum FunctionKind {
        REGEX,
        BUILTIN
    }
}
