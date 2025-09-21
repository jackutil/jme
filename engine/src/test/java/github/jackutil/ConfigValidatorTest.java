package github.jackutil;

import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import github.jackutil.compiler.ConfigValidationException;
import github.jackutil.compiler.ConfigValidator;

public class ConfigValidatorTest {

    private static final List<String> VALID_CONFIGS = List.of(
        "valid/minimal.json"
    );

    private static final List<String> INVALID_CONFIGS = Arrays.asList(
        "invalid/missing-mappings.json",
        "invalid/unknown-section.json",
        "invalid/meta-missing-name.json",
        "invalid/function-missing-type.json",
        "invalid/variable-missing-type.json",
        "invalid/mapping-missing-ref.json",
        "invalid/validation-bad-phase.json"
    );

    @Test
    public void validatesAllGoodFixtures() throws Exception {
        for (String resource : VALID_CONFIGS) {
            try (InputStream in = resource(resource)) {
                ConfigValidator.validate(in);
            }
        }
    }

    @Test
    public void rejectsAllBadFixtures() {
        for (String resource : INVALID_CONFIGS) {
            try (InputStream in = resource(resource)) {
                assertThrows(resource + " should fail validation", ConfigValidationException.class, () -> ConfigValidator.validate(in));
            } catch (Exception e) {
                throw new AssertionError("Unexpected exception for " + resource, e);
            }
        }
    }

    private InputStream resource(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new IllegalStateException("Missing test resource: " + name);
        }
        return stream;
    }
}


