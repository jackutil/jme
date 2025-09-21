package github.jackutil.compiler.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import github.jackutil.compiler.diagnostics.MappingException;

import github.jackutil.compiler.ir.FunctionDef;
import github.jackutil.compiler.ir.enums.FunctionKind;
import github.jackutil.compiler.ir.enums.ValueType;
import github.jackutil.compiler.ir.resolved.ResolvedVariable;

public class VariableResolverTest {

    @Test
    public void returnsPayloadValue() {
        VariableResolver resolver = resolver(List.of(variable("id", ValueType.STRING, true, false, new int[0], null, null)));
        resolver.bindPayload(Map.of("id", "ABC"));
        assertEquals("ABC", resolver.valueOf(0));
    }

    @Test
    public void usesDefaultWhenMissing() {
        ResolvedVariable variable = new ResolvedVariable(0, "id", ValueType.STRING, false, false, new int[0], null, List.of(), "DEF");
        VariableResolver resolver = resolver(List.of(variable));
        resolver.bindPayload(Map.of());
        assertEquals("DEF", resolver.valueOf(0));
    }

    @Test
    public void derivesValueWhenMissing() {
        FunctionExecutor executor = new FunctionExecutor(List.of(new FunctionDef(0, "const", FunctionKind.BUILTIN, "concat", List.of("A"), null)));
        ResolvedVariable variable = new ResolvedVariable(0, "id", ValueType.STRING, false, false, new int[0], 0, List.of("B"), null);
        VariableResolver resolver = new VariableResolver(List.of(variable), executor);
        resolver.bindPayload(Map.of());
        assertEquals("BA", resolver.valueOf(0));
    }

    @Test
    public void validatesConstraints() {
        FunctionExecutor executor = new FunctionExecutor(List.of(new FunctionDef(0, "regex", FunctionKind.REGEX, Pattern.compile("^[A-Z]+$"), List.of(), null)));
        ResolvedVariable variable = new ResolvedVariable(0, "id", ValueType.STRING, false, false, new int[]{0}, null, List.of(), null);
        VariableResolver resolver = new VariableResolver(List.of(variable), executor);
        resolver.bindPayload(Map.of("id", "abc"));
        assertThrows(MappingException.class, () -> resolver.valueOf(0));
    }

    @Test
    public void throwsWhenRequiredMissing() {
        VariableResolver resolver = resolver(List.of(variable("id", ValueType.STRING, true, false, new int[0], null, null)));
        resolver.bindPayload(Map.of());
        assertThrows(MappingException.class, () -> resolver.valueOf(0));
    }

    private VariableResolver resolver(List<ResolvedVariable> variables) {
        FunctionExecutor executor = new FunctionExecutor(List.of(new FunctionDef(0, "noop", FunctionKind.BUILTIN, "concat", List.of(), null)));
        return new VariableResolver(variables, executor);
    }

    private ResolvedVariable variable(String name, ValueType type, boolean required, boolean nullable,
                                      int[] constraintIds, Integer deriveFunction, Object defaultValue) {
        return new ResolvedVariable(0, name, type, required, nullable, constraintIds, deriveFunction, List.of(), defaultValue);
    }
}
