package github.jackutil.compiler.diagnostics;

public class MappingException extends RuntimeException {
    private final MappingDiagnostic diagnostic;

    public MappingException(MappingDiagnostic diagnostic) {
        super(diagnostic.message());
        this.diagnostic = diagnostic;
    }

    public MappingDiagnostic diagnostic() {
        return diagnostic;
    }

    public static MappingException of(String code, String message, String pointer) {
        return new MappingException(new MappingDiagnostic(code, message, pointer));
    }
}
