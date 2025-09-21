package github.jackutil.compiler.diagnostics;

import java.util.Collections;
import java.util.Map;

public record MappingDiagnostic(String code, String message, String pointer, Map<String, Object> details) {
    public MappingDiagnostic(String code, String message, String pointer) {
        this(code, message, pointer, Collections.emptyMap());
    }
}
