package github.jackutil.compiler.runtime;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.ir.enums.ValueType;
import github.jackutil.compiler.ir.resolved.ResolvedInput;

final class InputResolver {
    private final List<ResolvedInput> inputs;
    private final Object[] values;
    private final boolean[] resolved;
    private Map<String, Object> provided;

    InputResolver(List<ResolvedInput> inputs) {
        this.inputs = inputs;
        this.values = new Object[inputs.size()];
        this.resolved = new boolean[inputs.size()];
    }

    void bindInputs(Map<String, Object> inputValues) {
        this.provided = inputValues;
        Arrays.fill(values, null);
        Arrays.fill(resolved, false);
    }

    Object valueOf(int inputId) {
        if (inputId < 0 || inputId >= inputs.size()) {
            throw MappingException.of("INPUT_UNKNOWN_ID", "Unknown input id: " + inputId, "/INPUT");
        }
        if (!resolved[inputId]) {
            values[inputId] = resolve(inputs.get(inputId));
            resolved[inputId] = true;
        }
        return values[inputId];
    }

    private Object resolve(ResolvedInput input) {
        Object value = provided != null ? provided.get(input.name()) : null;
        if (value == null) {
            value = input.defaultValue();
        }
        if (value == null) {
            if (input.required()) {
                throw MappingException.of("INPUT_REQUIRED", "Missing required input: " + input.name(), pointer(input));
            }
            return null;
        }
        return coerce(input.type(), value, input);
    }

    private Object coerce(ValueType type, Object value, ResolvedInput input) {
        try {
            return switch (type) {
                case STRING -> value instanceof String ? value : String.valueOf(value);
                case NUMBER -> toBigDecimal(value);
                case INTEGER -> toBigDecimal(value).longValueExact();
                case BOOLEAN -> toBoolean(value);
                case ARRAY -> ensureType(value, List.class, "array", input);
                case OBJECT -> ensureType(value, Map.class, "object", input);
            };
        } catch (ArithmeticException | IllegalArgumentException ex) {
            throw MappingException.of("INPUT_TYPE", ex.getMessage(), pointer(input));
        }
    }

    private Object ensureType(Object value, Class<?> expected, String name, ResolvedInput input) {
        if (!expected.isInstance(value)) {
            throw MappingException.of("INPUT_TYPE", "Expected " + name + " for input " + input.name(), pointer(input));
        }
        return value;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).toLowerCase();
        if ("true".equals(text) || "false".equals(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("Value '" + value + "' cannot be coerced to boolean");
    }

    private String pointer(ResolvedInput input) {
        return "/INPUT/" + input.name();
    }
}
