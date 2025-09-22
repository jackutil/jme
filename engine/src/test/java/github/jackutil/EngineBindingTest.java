package github.jackutil;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.Set;

import org.junit.Test;

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

    private InputStream resource(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new IllegalStateException("Missing resource: " + name);
        }
        return stream;
    }
}
