package github.jackutil.compiler.runtime;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.ir.enums.ValueType;
import github.jackutil.compiler.ir.resolved.ResolvedVariable;

final class VariableResolver {
    private final List<ResolvedVariable> variables;
    private final FunctionExecutor functions;
    private final Object[] values;
    private final boolean[] resolved;
    private Map<String, Object> payload;

    VariableResolver(List<ResolvedVariable> variables, FunctionExecutor functions) {
        this.variables = variables;
        this.functions = functions;
        this.values = new Object[variables.size()];
        this.resolved = new boolean[variables.size()];
    }

    void bindPayload(Map<String, Object> payload) {
        this.payload = payload;
        Arrays.fill(values, null);
        Arrays.fill(resolved, false);
    }

    Object valueOf(int variableId) {
        if (variableId < 0 || variableId >= variables.size()) {
            throw MappingException.of("VARIABLE_UNKNOWN_ID", "Unknown variable id: " + variableId, "/VARIABLES");
        }
        if (!resolved[variableId]) {
            values[variableId] = resolve(variables.get(variableId));
            resolved[variableId] = true;
        }
        return values[variableId];
    }

    Map<String, Object> snapshotValues() {
        Map<String, Object> snapshot = new LinkedHashMap<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            ResolvedVariable variable = variables.get(i);
            snapshot.put(variable.name(), valueOf(i));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    private Object resolve(ResolvedVariable variable) {
        Object value = payload != null ? payload.get(variable.name()) : null;
        if (value == null && variable.defaultValue() != null) {
            value = variable.defaultValue();
        }
        if (value == null && variable.deriveFunctionId() != null) {
            try {
                value = functions.derive(variable.deriveFunctionId(), variable.deriveArgs());
            } catch (RuntimeException ex) {
                throw MappingException.of("VARIABLE_DERIVE", ex.getMessage(), pointer(variable));
            }
        }
        if (value == null) {
            if (variable.required()) {
                throw MappingException.of("VARIABLE_REQUIRED", "Missing required variable: " + variable.name(), pointer(variable));
            }
            return null;
        }
        Object coerced = coerce(variable.type(), value, variable);
        for (int functionId : variable.constraintFunctionIds()) {
            try {
                functions.validate(functionId, coerced);
            } catch (RuntimeException ex) {
                throw MappingException.of("VARIABLE_CONSTRAINT", ex.getMessage(), pointer(variable));
            }
        }
        return coerced;
    }

    private Object coerce(ValueType type, Object value, ResolvedVariable variable) {
        try {
            return switch (type) {
                case STRING -> value instanceof String ? value : String.valueOf(value);
                case NUMBER -> toBigDecimal(value);
                case INTEGER -> toBigDecimal(value).longValueExact();
                case BOOLEAN -> toBoolean(value);
                case ARRAY -> ensureType(value, List.class, "array", variable);
                case OBJECT -> ensureType(value, Map.class, "object", variable);
            };
        } catch (ArithmeticException | IllegalArgumentException ex) {
            throw MappingException.of("VARIABLE_TYPE", ex.getMessage(), pointer(variable));
        }
    }

    private Object ensureType(Object value, Class<?> expected, String name, ResolvedVariable variable) {
        if (!expected.isInstance(value)) {
            throw MappingException.of("VARIABLE_TYPE", "Expected " + name + " for variable " + variable.name(), pointer(variable));
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

    private String pointer(ResolvedVariable variable) {
        return "/VARIABLES/" + variable.name();
    }
}
