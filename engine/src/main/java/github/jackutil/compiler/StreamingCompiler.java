package github.jackutil.compiler;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Streaming pass that consumes the validated configuration and builds IR builders.
 * The implementation currently only establishes the control flow; section-specific
 * logic will be implemented incrementally.
 */
final class StreamingCompiler {
    private StreamingCompiler() {
        // utility class
    }

    static void consume(JsonParser parser, CompilerContext context) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IllegalStateException("Expected root object for configuration");
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IllegalStateException("Expected field name at root");
            }
            String section = parser.getCurrentName();
            context.sectionStack.push(section);
            parser.nextToken();
            switch (section) {
                case "META" -> SectionParsers.meta(parser, context);
                case "ENGINE" -> SectionParsers.engine(parser, context);
                case "INPUT" -> SectionParsers.inputs(parser, context);
                case "SCHEMA" -> SectionParsers.schema(parser, context);
                case "FUNCTIONS" -> SectionParsers.functions(parser, context);
                case "VARIABLES" -> SectionParsers.variables(parser, context);
                case "MAPPINGS" -> SectionParsers.mappings(parser, context);
                case "VALIDATION" -> SectionParsers.validation(parser, context);
                default -> throw new IllegalStateException("Unexpected section: " + section);
            }
            context.sectionStack.pop();
        }
    }
}
