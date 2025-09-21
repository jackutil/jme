package github.jackutil.compiler.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import github.jackutil.compiler.ir.FunctionDef;
import github.jackutil.compiler.ir.enums.FunctionKind;

public class BuiltinFunctionRuntimeTest {

    @Test
    public void generatesUuid() {
        BuiltinFunctionRuntime runtime = runtime("uuid");
        Object value = runtime.derive(List.of());
        assertTrue(value instanceof String);
        assertTrue(Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matcher((String) value).matches());
    }

    @Test
    public void generatesPosIntWithLength() {
        BuiltinFunctionRuntime runtime = runtime("pos_int");
        Object value = runtime.derive(List.of(3));
        assertEquals(3, String.valueOf(value).length());
    }

    @Test
    public void concatenatesStrings() {
        BuiltinFunctionRuntime runtime = runtime("concat");
        Object value = runtime.derive(List.of("a", "b", 1));
        assertEquals("ab1", value);
    }

    @Test
    public void lookupReturnsDefault() {
        BuiltinFunctionRuntime runtime = runtime("lookup", List.of(Map.of("A", "Alpha"), "Unknown"));
        Object value = runtime.derive(List.of("B"));
        assertEquals("Unknown", value);
    }

    @Test
    public void lookupThrowsOnBadMap() {
        BuiltinFunctionRuntime runtime = runtime("lookup");
        assertThrows(IllegalArgumentException.class, () -> runtime.derive(List.of("A", "not-map")));
    }

    @Test
    public void addHandlesNumbers() {
        BuiltinFunctionRuntime runtime = runtime("add");
        Object value = runtime.derive(List.of(1, 2.5, "3"));
        assertEquals(6.5, ((Number) value).doubleValue(), 0.0001);
    }

    @Test
    public void divideRequiresNonZero() {
        BuiltinFunctionRuntime runtime = runtime("divide");
        assertThrows(ArithmeticException.class, () -> runtime.derive(List.of(4, 0)));
    }

    private BuiltinFunctionRuntime runtime(String name) {
        return runtime(name, List.of());
    }

    private BuiltinFunctionRuntime runtime(String name, List<Object> args) {
        FunctionDef def = new FunctionDef(0, name, FunctionKind.BUILTIN, name, args, null);
        return new BuiltinFunctionRuntime(def);
    }
}
