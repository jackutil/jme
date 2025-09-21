package github.jackutil.compiler.ir;

/** Engine configuration directives. */
public record EngineConfig(String api, String outputRef, String validationRef) {
    public static EngineConfig empty() {
        return new EngineConfig("", "", null);
    }
}
