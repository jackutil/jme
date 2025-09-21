package github.jackutil.compiler;

import java.util.Objects;

import github.jackutil.compiler.ir.MappingProgram;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;

/**
 * Immutable representation of a compiled mapping program ready for execution.
 */
public final class CompiledMapping {
    private final ResolvedConfig config;
    private final MappingProgram program;

    public CompiledMapping(ResolvedConfig config, MappingProgram program) {
        this.config = Objects.requireNonNull(config, "config");
        this.program = Objects.requireNonNull(program, "program");
    }

    public ResolvedConfig config() {
        return config;
    }

    public MappingProgram program() {
        return program;
    }
}
