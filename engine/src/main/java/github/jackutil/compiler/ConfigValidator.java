package github.jackutil.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Streaming validator for the mapping DSL v2 configuration files.
 */
public final class ConfigValidator {
    private static final JsonFactory FACTORY = new JsonFactory();
    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "META",
            "ENGINE",
            "INPUT",
            "SCHEMA",
            "FUNCTIONS",
            "VARIABLES",
            "MAPPINGS",
            "VALIDATION");

    public static void validate(InputStream stream) {
        Objects.requireNonNull(stream, "stream");
        try (JsonParser parser = FACTORY.createParser(stream)) {
            ensureToken(parser.nextToken(), JsonToken.START_OBJECT, "root of document must be an object");

            Set<String> seen = new HashSet<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "expected field name at root");
                String key = parser.getCurrentName();
                if (!TOP_LEVEL_KEYS.contains(key)) {
                    throw error("Unknown top-level section: " + key);
                }
                if (!seen.add(key)) {
                    throw error("Duplicate top-level section: " + key);
                }

                JsonToken valueToken = parser.nextToken();
                ensureToken(valueToken, JsonToken.START_OBJECT, "Section " + key + " must be an object");

                switch (key) {
                    case "META" -> validateMeta(parser);
                    case "ENGINE" -> validateEngine(parser);
                    case "INPUT" -> validateInput(parser);
                    case "SCHEMA" -> validateSchema(parser);
                    case "FUNCTIONS" -> validateFunctions(parser);
                    case "VARIABLES" -> validateVariables(parser);
                    case "MAPPINGS" -> validateMappings(parser);
                    case "VALIDATION" -> validateValidation(parser);
                    default -> parser.skipChildren();
                }
            }

            Set<String> missing = new HashSet<>(TOP_LEVEL_KEYS);
            missing.removeAll(seen);
            if (!missing.isEmpty()) {
                throw error("Missing required sections: " + missing);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to validate configuration", ex);
        }
    }

    private static void validateMeta(JsonParser parser) throws IOException {
        Set<String> required = new HashSet<>(Set.of("dslVersion", "name", "targetAspect"));
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "META must contain field names");
            String field = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            switch (field) {
                case "dslVersion", "name", "targetAspect" -> {
                    ensureToken(valueToken, JsonToken.VALUE_STRING, "META." + field + " must be a string");
                    required.remove(field);
                }
                case "description", "owner", "lastUpdated" ->
                    ensureToken(valueToken, JsonToken.VALUE_STRING, "META." + field + " must be a string");
                default -> throw error("Unknown field in META: " + field);
            }
        }
        if (!required.isEmpty()) {
            throw error("META missing required fields: " + required);
        }
    }

    private static void validateEngine(JsonParser parser) throws IOException {
        boolean hasApi = false;
        boolean hasOutput = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "ENGINE must contain field names");
            String field = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            switch (field) {
                case "api" -> {
                    ensureToken(valueToken, JsonToken.VALUE_STRING, "ENGINE.api must be a string");
                    hasApi = true;
                }
                case "output", "validation" -> {
                    ensureToken(valueToken, JsonToken.VALUE_STRING, "ENGINE." + field + " must be a string reference");
                    if ("output".equals(field)) {
                        hasOutput = true;
                    }
                }
                default -> throw error("Unknown field in ENGINE: " + field);
            }
        }
        if (!hasApi || !hasOutput) {
            throw error("ENGINE requires api and output fields");
        }
    }

    private static void validateInput(JsonParser parser) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "INPUT must contain identifiers");
            String name = parser.getCurrentName();
            ensureIdentifier(name, "INPUT");
            ensureToken(parser.nextToken(), JsonToken.START_OBJECT, "INPUT." + name + " must be object");

            boolean hasType = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "INPUT entry must contain field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "type" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING, "INPUT." + name + ".type must be a string");
                        hasType = true;
                    }
                    case "required", "nullable" ->
                        ensureBoolean(valueToken, "INPUT." + name + "." + field + " must be boolean");
                    case "default" -> skipValue(parser, valueToken);
                    case "description" -> ensureToken(valueToken, JsonToken.VALUE_STRING,
                            "INPUT." + name + ".description must be a string");
                    default -> throw error("Unknown field in INPUT." + name + ": " + field);
                }
            }
            if (!hasType) {
                throw error("INPUT." + name + " requires type");
            }
        }
    }

    private static void validateSchema(JsonParser parser) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "SCHEMA must contain schema identifiers");
            String name = parser.getCurrentName();
            ensureIdentifier(name, "SCHEMA");
            ensureToken(parser.nextToken(), JsonToken.START_OBJECT, "SCHEMA." + name + " must be an object");
            boolean hasRef = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "SCHEMA entry must contain field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "ref", "dialect" -> ensureToken(valueToken, JsonToken.VALUE_STRING,
                            "SCHEMA." + name + "." + field + " must be a string");
                    case "strict" -> ensureBoolean(valueToken, "SCHEMA." + name + ".strict must be boolean");
                    default -> throw error("Unknown field in SCHEMA." + name + ": " + field);
                }
                if ("ref".equals(field)) {
                    hasRef = true;
                }
            }
            if (!hasRef) {
                throw error("SCHEMA." + name + " missing required field ref");
            }
        }
    }

    private static void validateFunctions(JsonParser parser) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "FUNCTIONS must contain identifiers");
            String name = parser.getCurrentName();
            ensureIdentifier(name, "FUNCTIONS");
            ensureToken(parser.nextToken(), JsonToken.START_OBJECT, "FUNCTIONS." + name + " must be an object");

            boolean hasType = false;
            boolean hasPattern = false;
            boolean hasFn = false;
            String typeValue = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "FUNCTIONS entry must contain field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "type" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING, "FUNCTIONS." + name + ".type must be string");
                        typeValue = parser.getValueAsString();
                        if (!"regex".equals(typeValue) && !"builtin".equals(typeValue)) {
                            throw error("FUNCTIONS." + name + ".type must be 'regex' or 'builtin'");
                        }
                        hasType = true;
                    }
                    case "pattern" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING,
                                "FUNCTIONS." + name + ".pattern must be string");
                        hasPattern = true;
                    }
                    case "fn" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING, "FUNCTIONS." + name + ".fn must be string");
                        hasFn = true;
                    }
                    case "args" -> ensureArray(parser, valueToken, "FUNCTIONS." + name + ".args");
                    case "description" -> ensureToken(valueToken, JsonToken.VALUE_STRING,
                            "FUNCTIONS." + name + ".description must be string");
                    default -> throw error("Unknown field in FUNCTIONS." + name + ": " + field);
                }
            }
            if (!hasType) {
                throw error("FUNCTIONS." + name + " missing required field type");
            }
            if ("regex".equals(typeValue) && !hasPattern) {
                throw error("FUNCTIONS." + name + " requires pattern for regex type");
            }
            if ("builtin".equals(typeValue) && !hasFn) {
                throw error("FUNCTIONS." + name + " requires fn for builtin type");
            }
        }
    }

    private static void validateVariables(JsonParser parser) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VARIABLES must contain identifiers");
            String name = parser.getCurrentName();
            ensureIdentifier(name, "VARIABLES");
            ensureToken(parser.nextToken(), JsonToken.START_OBJECT, "VARIABLES." + name + " must be an object");

            boolean hasType = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VARIABLES entry must contain field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "type" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING, "VARIABLES." + name + ".type must be string");
                        hasType = true;
                    }
                    case "required", "nullable" ->
                        ensureBoolean(valueToken, "VARIABLES." + name + "." + field + " must be boolean");
                    case "constraints" ->
                        ensureArrayOfStrings(parser, valueToken, "VARIABLES." + name + ".constraints");
                    case "default" -> skipValue(parser, valueToken);
                    case "derive" -> validateDerive(parser, valueToken, name);
                    case "maxLength", "minLength", "minItems", "maxItems" -> ensureToken(valueToken,
                            JsonToken.VALUE_NUMBER_INT, "VARIABLES." + name + "." + field + " must be integer");
                    case "enum" -> ensureArray(parser, valueToken, "VARIABLES." + name + ".enum");
                    case "items" -> {
                        ensureToken(valueToken, JsonToken.START_OBJECT, "VARIABLES." + name + ".items must be object");
                        parser.skipChildren();
                    }
                    case "fields" -> {
                        ensureToken(valueToken, JsonToken.START_OBJECT, "VARIABLES." + name + ".fields must be object");
                        parser.skipChildren();
                    }
                    case "requiredFields" ->
                        ensureArrayOfStrings(parser, valueToken, "VARIABLES." + name + ".requiredFields");
                    case "description" -> ensureToken(valueToken, JsonToken.VALUE_STRING,
                            "VARIABLES." + name + ".description must be string");
                    default -> throw error("Unknown field in VARIABLES." + name + ": " + field);
                }
            }
            if (!hasType) {
                throw error("VARIABLES." + name + " missing required field type");
            }
        }
    }

    private static void validateDerive(JsonParser parser, JsonToken token, String variableName) throws IOException {
        ensureToken(token, JsonToken.START_OBJECT, "VARIABLES." + variableName + ".derive must be object");
        boolean hasFunction = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME,
                    "VARIABLES." + variableName + ".derive requires field names");
            String field = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            switch (field) {
                case "function" -> {
                    ensureToken(valueToken, JsonToken.VALUE_STRING,
                            "VARIABLES." + variableName + ".derive.function must be string");
                    hasFunction = true;
                }
                case "args" -> ensureArray(parser, valueToken, "VARIABLES." + variableName + ".derive.args");
                default -> throw error("Unknown field in VARIABLES." + variableName + ".derive: " + field);
            }
        }
        if (!hasFunction) {
            throw error("VARIABLES." + variableName + ".derive missing required field function");
        }
    }

    private static void validateMappings(JsonParser parser) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "MAPPINGS must contain identifiers");
            String name = parser.getCurrentName();
            ensureIdentifier(name, "MAPPINGS");
            ensureToken(parser.nextToken(), JsonToken.START_OBJECT, "MAPPINGS." + name + " must be object");

            boolean hasRef = false;
            boolean hasMap = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "MAPPINGS entry must contain field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "REF" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING, "MAPPINGS." + name + ".REF must be string");
                        hasRef = true;
                    }
                    case "MAP" -> {
                        hasMap = true;
                        skipValue(parser, valueToken);
                    }
                    default -> throw error("Unknown field in MAPPINGS." + name + ": " + field);
                }
            }
            if (!hasRef || !hasMap) {
                throw error("MAPPINGS." + name + " requires REF and MAP");
            }
        }
    }

    private static void validateValidation(JsonParser parser) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VALIDATION must contain identifiers");
            String name = parser.getCurrentName();
            ensureIdentifier(name, "VALIDATION");
            ensureToken(parser.nextToken(), JsonToken.START_OBJECT, "VALIDATION." + name + " must be object");
            boolean hasSchema = false;
            boolean hasPhase = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                ensureToken(parser.getCurrentToken(), JsonToken.FIELD_NAME,
                        "VALIDATION entry must contain field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "schema" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING,
                                "VALIDATION." + name + ".schema must be string");
                        hasSchema = true;
                    }
                    case "phase" -> {
                        ensureToken(valueToken, JsonToken.VALUE_STRING, "VALIDATION." + name + ".phase must be string");
                        String phase = parser.getValueAsString();
                        if (!"pre-map".equals(phase) && !"post-map".equals(phase)) {
                            throw error("VALIDATION." + name + ".phase must be 'pre-map' or 'post-map'");
                        }
                        hasPhase = true;
                    }
                    default -> throw error("Unknown field in VALIDATION." + name + ": " + field);
                }
            }
            if (!hasSchema || !hasPhase) {
                throw error("VALIDATION." + name + " requires schema and phase");
            }
        }
    }

    private static void ensureIdentifier(String name, String context) {
        if (!name.matches("[A-Za-z0-9_.-]+")) {
            throw error("Invalid identifier in " + context + ": " + name);
        }
    }

    private static void ensureBoolean(JsonToken token, String message) {
        if (token != JsonToken.VALUE_FALSE && token != JsonToken.VALUE_TRUE) {
            throw error(message + " (expected boolean but got " + token + ")");
        }
    }

    private static void ensureArray(JsonParser parser, JsonToken token, String context) throws IOException {
        ensureToken(token, JsonToken.START_ARRAY, context + " must be array");
        parser.skipChildren();
    }

    private static void ensureArrayOfStrings(JsonParser parser, JsonToken token, String context) throws IOException {
        ensureToken(token, JsonToken.START_ARRAY, context + " must be array");
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            ensureToken(parser.getCurrentToken(), JsonToken.VALUE_STRING, context + " must contain strings");
        }
    }

    private static void skipValue(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
        }
    }

    private static void ensureToken(JsonToken actual, JsonToken expected, String message) {
        if (actual != expected) {
            throw error(message + " (expected " + expected + " but got " + actual + ")");
        }
    }

    private static ConfigValidationException error(String message) {
        return new ConfigValidationException(message);
    }
}
