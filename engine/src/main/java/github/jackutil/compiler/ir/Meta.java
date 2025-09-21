package github.jackutil.compiler.ir;

/**
 * Metadata about the configuration.
 */
public record Meta(String dslVersion, String name, String targetAspect, String description, String owner, String lastUpdated) {
    public static Meta empty() {
        return new Meta("", "", "", null, null, null);
    }
}
