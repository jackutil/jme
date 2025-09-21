package github.jackutil.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import github.jackutil.compiler.ir.DerivedValue;
import github.jackutil.compiler.ir.EngineConfig;
import github.jackutil.compiler.ir.FunctionDef;
import github.jackutil.compiler.ir.MapNode;
import github.jackutil.compiler.ir.MappingDef;
import github.jackutil.compiler.ir.InputDef;
import github.jackutil.compiler.ir.Meta;
import github.jackutil.compiler.ir.SchemaDef;
import github.jackutil.compiler.ir.ValidationRule;
import github.jackutil.compiler.ir.VariableDef;
import github.jackutil.compiler.ir.enums.FunctionKind;
import github.jackutil.compiler.ir.enums.ValidationPhase;
import github.jackutil.compiler.ir.enums.ValueType;

final class SectionParsers {
    private SectionParsers() {
    }

    static void meta(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "META must be an object");

        String dslVersion = null;
        String name = null;
        String targetAspect = null;
        String description = null;
        String owner = null;
        String lastUpdated = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "META requires field names");
            String field = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            switch (field) {
                case "dslVersion" -> dslVersion = expectString(parser, valueToken, "META.dslVersion");
                case "name" -> name = expectString(parser, valueToken, "META.name");
                case "targetAspect" -> targetAspect = expectString(parser, valueToken, "META.targetAspect");
                case "description" -> description = expectString(parser, valueToken, "META.description");
                case "owner" -> owner = expectString(parser, valueToken, "META.owner");
                case "lastUpdated" -> lastUpdated = expectString(parser, valueToken, "META.lastUpdated");
                default -> throw unexpectedField("META", field);
            }
        }

        context.meta = new Meta(dslVersion, name, targetAspect, description, owner, lastUpdated);
    }

    static void engine(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "ENGINE must be an object");
        String api = null;
        String output = null;
        String validation = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "ENGINE requires field names");
            String field = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            switch (field) {
                case "api" -> api = expectString(parser, valueToken, "ENGINE.api");
                case "output" -> output = expectString(parser, valueToken, "ENGINE.output");
                case "validation" -> validation = expectString(parser, valueToken, "ENGINE.validation");
                default -> throw unexpectedField("ENGINE", field);
            }
        }

        context.engine = new EngineConfig(api, output, validation);
    }

    static void schema(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "SCHEMA must be an object");
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "SCHEMA requires identifiers");
            String name = parser.getCurrentName();
            if (context.schemaIndex.containsKey(name)) {
                throw new IllegalStateException("Duplicate schema identifier: " + name);
            }
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "SCHEMA." + name + " must be an object");
            String ref = null;
            String dialect = null;
            boolean strict = false;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "SCHEMA entry requires field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "ref" -> ref = expectString(parser, valueToken, "SCHEMA." + name + ".ref");
                    case "dialect" -> dialect = expectString(parser, valueToken, "SCHEMA." + name + ".dialect");
                    case "strict" -> strict = expectBoolean(parser, valueToken, "SCHEMA." + name + ".strict");
                    default -> throw unexpectedField("SCHEMA." + name, field);
                }
            }

            int id = context.schemas.size();
            context.schemaIndex.put(name, id);
            context.schemas.add(new SchemaDef(id, name, ref, dialect, strict));
        }
    }

    static void functions(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "FUNCTIONS must be an object");
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "FUNCTIONS requires identifiers");
            String name = parser.getCurrentName();
            if (context.functionIndex.containsKey(name)) {
                throw new IllegalStateException("Duplicate function identifier: " + name);
            }
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "FUNCTIONS." + name + " must be an object");

            FunctionKind kind = null;
            Object payload = null;
            List<Object> args = List.of();
            String description = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "FUNCTIONS entry requires field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "type" -> {
                        String type = expectString(parser, valueToken, "FUNCTIONS." + name + ".type");
                        kind = switch (type) {
                            case "regex" -> FunctionKind.REGEX;
                            case "builtin" -> FunctionKind.BUILTIN;
                            default -> throw new IllegalStateException("Unknown function type: " + type);
                        };
                    }
                    case "pattern" -> payload = expectRegex(parser, valueToken, "FUNCTIONS." + name + ".pattern");
                    case "fn" -> payload = expectString(parser, valueToken, "FUNCTIONS." + name + ".fn");
                    case "args" -> args = readArray(parser, valueToken, "FUNCTIONS." + name + ".args");
                    case "description" -> description = expectString(parser, valueToken, "FUNCTIONS." + name + ".description");
                    default -> throw unexpectedField("FUNCTIONS." + name, field);
                }
            }

            if (kind == null) {
                throw new IllegalStateException("FUNCTIONS." + name + " missing type");
            }

            int id = context.functions.size();
            context.functionIndex.put(name, id);
            context.functions.add(new FunctionDef(id, name, kind, payload, args, description));
        }
    }

    static void inputs(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "INPUT must be an object");
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "INPUT requires identifiers");
            String name = parser.getCurrentName();
            if (context.inputIndex.containsKey(name)) {
                throw new IllegalStateException("Duplicate input identifier: " + name);
            }
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "INPUT." + name + " must be an object");

            ValueType type = null;
            boolean required = false;
            boolean nullable = false;
            Object defaultValue = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "INPUT entry requires field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "type" -> {
                        String typeText = expectString(parser, valueToken, "INPUT." + name + ".type");
                        type = ValueType.valueOf(typeText.toUpperCase());
                    }
                    case "required" -> required = expectBoolean(parser, valueToken, "INPUT." + name + ".required");
                    case "nullable" -> nullable = expectBoolean(parser, valueToken, "INPUT." + name + ".nullable");
                    case "default" -> defaultValue = readAny(parser, valueToken, "INPUT." + name + ".default");
                    case "description" -> skipValue(parser, valueToken);
                    default -> throw unexpectedField("INPUT." + name, field);
                }
            }

            if (type == null) {
                throw new IllegalStateException("INPUT." + name + " missing type");
            }

            int id = context.inputs.size();
            context.inputIndex.put(name, id);
            context.inputs.add(new InputDef(id, name, type, required, nullable, defaultValue));
        }
    }
    static void variables(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "VARIABLES must be an object");
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VARIABLES requires identifiers");
            String name = parser.getCurrentName();
            if (context.variableIndex.containsKey(name)) {
                throw new IllegalStateException("Duplicate variable identifier: " + name);
            }
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "VARIABLES." + name + " must be an object");

            ValueType type = null;
            boolean required = false;
            boolean nullable = false;
            List<String> constraints = List.of();
            DerivedValue derive = null;
            Object defaultValue = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VARIABLES entry requires field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "type" -> {
                        String typeText = expectString(parser, valueToken, "VARIABLES." + name + ".type");
                        type = ValueType.valueOf(typeText.toUpperCase());
                    }
                    case "required" -> required = expectBoolean(parser, valueToken, "VARIABLES." + name + ".required");
                    case "nullable" -> nullable = expectBoolean(parser, valueToken, "VARIABLES." + name + ".nullable");
                    case "constraints" -> constraints = readStringArray(parser, valueToken, "VARIABLES." + name + ".constraints");
                    case "default" -> defaultValue = readAny(parser, valueToken, "VARIABLES." + name + ".default");
                    case "derive" -> derive = readDerived(parser, valueToken, name);
                    case "description", "maxLength", "minLength", "minItems", "maxItems", "enum", "items", "fields", "requiredFields", "source", "fallback" -> {
                        skipValue(parser, valueToken);
                        
                    }
                    default -> throw unexpectedField("VARIABLES." + name, field);
                }
            }

            if (type == null) {
                throw new IllegalStateException("VARIABLES." + name + " missing type");
            }

            int id = context.variables.size();
            context.variableIndex.put(name, id);
            context.variables.add(new VariableDef(id, name, type, required, nullable, constraints, derive, defaultValue));
        }
    }

    static void mappings(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "MAPPINGS must be an object");
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "MAPPINGS requires identifiers");
            String name = parser.getCurrentName();
            if (context.mappingIndex.containsKey(name)) {
                throw new IllegalStateException("Duplicate mapping identifier: " + name);
            }
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "MAPPINGS." + name + " must be an object");
            String ref = null;
            MapNode root = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "MAPPINGS entry requires field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "REF" -> ref = expectString(parser, valueToken, "MAPPINGS." + name + ".REF");
                    case "MAP" -> root = parseNode(parser, valueToken, "MAPPINGS." + name + ".MAP");
                    default -> throw unexpectedField("MAPPINGS." + name, field);
                }
            }

            if (ref == null || root == null) {
                throw new IllegalStateException("MAPPINGS." + name + " requires REF and MAP");
            }

            int id = context.mappings.size();
            context.mappingIndex.put(name, id);
            context.mappings.add(new MappingDef(id, name, ref, root));
        }
    }

    static void validation(JsonParser parser, CompilerContext context) throws IOException {
        requireToken(parser.getCurrentToken(), JsonToken.START_OBJECT, "VALIDATION must be an object");
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VALIDATION requires identifiers");
            String name = parser.getCurrentName();
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "VALIDATION." + name + " must be an object");
            String schemaRef = null;
            ValidationPhase phase = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VALIDATION entry requires field names");
                String field = parser.getCurrentName();
                JsonToken valueToken = parser.nextToken();
                switch (field) {
                    case "schema" -> schemaRef = expectString(parser, valueToken, "VALIDATION." + name + ".schema");
                    case "phase" -> {
                        String phaseText = expectString(parser, valueToken, "VALIDATION." + name + ".phase");
                        phase = switch (phaseText) {
                            case "pre-map" -> ValidationPhase.PRE_MAP;
                            case "post-map" -> ValidationPhase.POST_MAP;
                            default -> throw new IllegalStateException("Unknown validation phase: " + phaseText);
                        };
                    }
                    default -> throw unexpectedField("VALIDATION." + name, field);
                }
            }

            if (schemaRef == null || phase == null) {
                throw new IllegalStateException("VALIDATION." + name + " requires schema and phase");
            }

            int id = context.validations.size();
            context.validations.add(new ValidationRule(id, name, schemaRef, phase));
        }
    }

    private static MapNode parseNode(JsonParser parser, JsonToken token, String contextPath) throws IOException {
        return switch (token) {
            case VALUE_STRING -> parseStringNode(parser.getValueAsString());
            case VALUE_NUMBER_INT -> new MapNode.LiteralNode(readNumber(parser));
            case VALUE_NUMBER_FLOAT -> new MapNode.LiteralNode(parser.getDecimalValue());
            case VALUE_TRUE -> new MapNode.LiteralNode(Boolean.TRUE);
            case VALUE_FALSE -> new MapNode.LiteralNode(Boolean.FALSE);
            case VALUE_NULL -> new MapNode.LiteralNode(null);
            case START_OBJECT -> parseObjectNode(parser, contextPath);
            case START_ARRAY -> parseArrayNode(parser, contextPath);
            default -> throw new IllegalStateException("Unsupported token " + token + " at " + contextPath);
        };
    }

    private static MapNode parseObjectNode(JsonParser parser, String contextPath) throws IOException {
        List<MapNode.ObjectNode.Field> fields = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, contextPath + " requires field names");
            String fieldName = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            fields.add(new MapNode.ObjectNode.Field(fieldName, parseNode(parser, valueToken, contextPath + "." + fieldName)));
        }
        return new MapNode.ObjectNode(fields);
    }

    private static MapNode parseArrayNode(JsonParser parser, String contextPath) throws IOException {
        List<MapNode> elements = new ArrayList<>();
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            JsonToken token = parser.getCurrentToken();
            elements.add(parseNode(parser, token, contextPath + "[" + index + "]"));
            index++;
        }
        return new MapNode.ArrayNode(elements);
    }

    private static MapNode parseStringNode(String value) {
        if (value.startsWith("$VARIABLES.")) {
            return new MapNode.VariableRefNode(value);
        }
        if (value.startsWith("$INPUT.")) {
            return new MapNode.InputRefNode(value);
        }
        if (value.startsWith("$MAPPINGS.")) {
            return new MapNode.MappingRefNode(value);
        }
        return new MapNode.LiteralNode(value);
    }

    private static DerivedValue readDerived(JsonParser parser, JsonToken token, String variableName) throws IOException {
        requireToken(token, JsonToken.START_OBJECT, "VARIABLES." + variableName + ".derive must be an object");
        String functionRef = null;
        List<Object> args = List.of();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, "VARIABLES." + variableName + ".derive requires field names");
            String field = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            switch (field) {
                case "function" -> functionRef = expectString(parser, valueToken, "VARIABLES." + variableName + ".derive.function");
                case "args" -> args = readArray(parser, valueToken, "VARIABLES." + variableName + ".derive.args");
                default -> throw unexpectedField("VARIABLES." + variableName + ".derive", field);
            }
        }
        if (functionRef == null) {
            throw new IllegalStateException("VARIABLES." + variableName + ".derive requires function");
        }
        return new DerivedValue(functionRef, args);
    }

    private static List<String> readStringArray(JsonParser parser, JsonToken token, String contextPath) throws IOException {
        requireToken(token, JsonToken.START_ARRAY, contextPath + " must be an array");
        List<String> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.getCurrentToken() != JsonToken.VALUE_STRING) {
                throw new IllegalStateException(contextPath + " must contain strings");
            }
            values.add(parser.getValueAsString());
        }
        return values;
    }

    private static List<Object> readArray(JsonParser parser, JsonToken token, String contextPath) throws IOException {
        requireToken(token, JsonToken.START_ARRAY, contextPath + " must be an array");
        List<Object> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(readAny(parser, parser.getCurrentToken(), contextPath));
        }
        return values;
    }

    private static Object readAny(JsonParser parser, JsonToken token, String contextPath) throws IOException {
        return switch (token) {
            case VALUE_STRING -> parser.getValueAsString();
            case VALUE_NUMBER_INT -> readNumber(parser);
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            case START_OBJECT -> readObject(parser, contextPath);
            case START_ARRAY -> readList(parser, contextPath);
            default -> throw new IllegalStateException("Unsupported token " + token + " at " + contextPath);
        };
    }

    private static Map<String, Object> readObject(JsonParser parser, String contextPath) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.getCurrentToken(), JsonToken.FIELD_NAME, contextPath + " requires field names");
            String field = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            values.put(field, readAny(parser, valueToken, contextPath + "." + field));
        }
        return values;
    }

    private static List<Object> readList(JsonParser parser, String contextPath) throws IOException {
        List<Object> values = new ArrayList<>();
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(readAny(parser, parser.getCurrentToken(), contextPath + "[" + index + "]"));
            index++;
        }
        return values;
    }

    private static Pattern expectRegex(JsonParser parser, JsonToken token, String contextPath) throws IOException {
        String pattern = expectString(parser, token, contextPath);
        return Pattern.compile(pattern);
    }

    private static String expectString(JsonParser parser, JsonToken token, String contextPath) throws IOException {
        requireToken(token, JsonToken.VALUE_STRING, contextPath + " must be a string");
        return parser.getValueAsString();
    }

    private static boolean expectBoolean(JsonParser parser, JsonToken token, String contextPath) {
        requireToken(token, JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE, contextPath + " must be boolean");
        return token == JsonToken.VALUE_TRUE;
    }

    private static Number readNumber(JsonParser parser) throws IOException {
        if (parser.getNumberType() == JsonParser.NumberType.BIG_INTEGER) {
            return parser.getBigIntegerValue();
        }
        if (parser.getNumberType() == JsonParser.NumberType.BIG_DECIMAL) {
            return parser.getDecimalValue();
        }
        if (parser.getNumberType() == JsonParser.NumberType.INT) {
            return parser.getIntValue();
        }
        if (parser.getNumberType() == JsonParser.NumberType.LONG) {
            return parser.getLongValue();
        }
        if (parser.getNumberType() == JsonParser.NumberType.FLOAT) {
            return parser.getFloatValue();
        }
        if (parser.getNumberType() == JsonParser.NumberType.DOUBLE) {
            return parser.getDoubleValue();
        }
        // fallback
        return parser.getNumberValue();
    }

    private static void skipValue(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
        }
    }

    private static IllegalStateException unexpectedField(String context, String field) {
        return new IllegalStateException("Unexpected field in " + context + ": " + field);
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String message) {
        if (actual != expected) {
            throw new IllegalStateException(message + " (got " + actual + ")");
        }
    }

    private static void requireToken(JsonToken actual, JsonToken expectedA, JsonToken expectedB, String message) {
        if (actual != expectedA && actual != expectedB) {
            throw new IllegalStateException(message + " (got " + actual + ")");
        }
    }
}




