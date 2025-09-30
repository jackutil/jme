package github.jackutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import github.jackutil.EngineBinding.ValidationMode;
import github.jackutil.compiler.diagnostics.MappingException;

public class EngineBindingTest {

    @Test
    public void returnsInputFieldsForCompiledMapping() throws Exception {
        try (InputStream stream = resource("engine-binding-test-config.json")) {
            EngineBinding binding = EngineBinding.fromStream(stream);
            assertEquals(Set.of("orderId", "customerId", "priority"), binding.inputFields());
        }
    }

    @Test
    public void returnsEmptySetWhenNoInputsDeclared() throws Exception {
        try (InputStream stream = resource("valid/minimal.json")) {
            EngineBinding binding = EngineBinding.fromStream(stream);
            assertEquals(Set.of(), binding.inputFields());
        }
    }

    @Test
    public void validatesResultAgainstSchemaByDefault() throws Exception {
        try (InputStream stream = resource("valid/result-validation.json")) {
            EngineBinding binding = EngineBinding.fromStream(stream);
            EngineBinding.ExecutionResult result = binding.execute("root", Map.of(), Map.of("value", "VALID"));
            assertEquals("VALID", result.output().get("value"));
        }
    }

    @Test
    public void throwsWhenResultViolatesSchema() throws Exception {
        try (InputStream stream = resource("valid/result-validation.json")) {
            EngineBinding binding = EngineBinding.fromStream(stream);
            assertThrows(MappingException.class, () -> binding.execute("root", Map.of(), Map.of("value", "invalid")));
        }
    }

    @Test
    public void skipsValidationWhenDisabled() throws Exception {
        try (InputStream stream = resource("valid/result-validation.json")) {
            EngineBinding binding = EngineBinding.fromStream(stream);
            EngineBinding.ExecutionResult result = binding.execute(
                "root",
                Map.of(),
                Map.of("value", "invalid"),
                ValidationMode.DISABLED
            );
            assertEquals("invalid", result.output().get("value"));
        }
    }

    private InputStream resource(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new IllegalStateException("Missing resource: " + name);
        }
        return stream;
    }
}
