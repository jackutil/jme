package github.jackutil.compiler;

import java.util.List;

import github.jackutil.compiler.ir.ConfigModel;
import github.jackutil.compiler.ir.EngineConfig;
import github.jackutil.compiler.ir.MappingProgram;
import github.jackutil.compiler.ir.Meta;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.runtime.InstructionEmitter;
import github.jackutil.compiler.runtime.InstructionMetricsRecorder;
import github.jackutil.compiler.runtime.InstructionOptimizer;

/**
 * Converts the collected IR into executable instructions.
 */
final class InstructionCompiler {
    private InstructionCompiler() {
    }

    static ConfigModel prepareModel(CompilerContext context) {
        Meta meta = context.meta != null ? context.meta : Meta.empty();
        EngineConfig engine = context.engine != null ? context.engine : EngineConfig.empty();
        return new ConfigModel(
            meta,
            engine,
            List.copyOf(context.schemas),
            List.copyOf(context.functions),
            List.copyOf(context.inputs),
            List.copyOf(context.variables),
            List.copyOf(context.mappings),
            List.copyOf(context.validations)
        );
    }

    static CompiledMapping compile(ResolvedConfig config) {
        ResolvedConfig optimized = InstructionOptimizer.optimize(config);
        InstructionEmitter emitter = new InstructionEmitter();
        MappingProgram program = new MappingProgram(emitter.emit(optimized));
        InstructionMetricsRecorder.record(optimized, program);
        return new CompiledMapping(optimized, program);
    }
}
