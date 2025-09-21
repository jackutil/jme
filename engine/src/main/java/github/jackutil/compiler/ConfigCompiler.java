package github.jackutil.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.ir.ConfigModel;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;

/**
 * Entry point for compiling a validated mapping configuration into an executable form.
 */
public final class ConfigCompiler {
    private static final JsonFactory FACTORY = new JsonFactory();

    private ConfigCompiler() {
        // utility class
    }

    public static CompiledMapping compile(InputStream stream) {
        Objects.requireNonNull(stream, "stream");
        CompilerContext context = new CompilerContext();
        try (JsonParser parser = FACTORY.createParser(stream)) {
            StreamingCompiler.consume(parser, context);
            ConfigModel model = InstructionCompiler.prepareModel(context);
            ResolvedConfig resolved = ReferenceResolver.resolve(
                model,
                context.functionIndex,
                context.inputIndex,
                context.variableIndex,
                context.mappingIndex,
                context.schemaIndex
            );
            return InstructionCompiler.compile(resolved);
        } catch (MappingException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            throw MappingException.of("CONFIG_ERROR", ex.getMessage(), context.pointer());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to compile configuration", ex);
        }
    }
}
