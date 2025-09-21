package github.jackutil.compiler.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiagnosticsCollector {
    private final List<MappingDiagnostic> diagnostics = new ArrayList<>();

    public void add(MappingDiagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public boolean hasErrors() {
        return !diagnostics.isEmpty();
    }

    public List<MappingDiagnostic> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public void throwIfAny() {
        if (hasErrors()) {
            throw new MappingException(diagnostics.get(0));
        }
    }
}
