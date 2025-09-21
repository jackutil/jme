package github.jackutil.compiler.ir;

/** Schema definition metadata. */
public record SchemaDef(int id, String name, String ref, String dialect, boolean strict) {
}
