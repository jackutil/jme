package github.jackutil.compiler.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.ir.InstructionBlock;
import github.jackutil.compiler.ir.InstructionProgram;
import github.jackutil.compiler.ir.MappingProgram;
import github.jackutil.compiler.ir.enums.OpCode;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedMapping;

/**
 * Collects opcode-level metrics for compiled mappings.
 */
public final class InstructionMetricsCollector {
    private InstructionMetricsCollector() {
    }

    public static InstructionMetrics collect(CompiledMapping compiled, String mappingName) {
        return collect(compiled.config(), compiled.program(), mappingName);
    }

    public static InstructionMetrics collect(ResolvedConfig config, MappingProgram program, String mappingName) {
        for (ResolvedMapping mapping : config.mappings()) {
            if (mapping.name().equals(mappingName)) {
                return collectMapping(program.program(), mapping);
            }
        }
        throw new IllegalArgumentException("Unknown mapping name: " + mappingName);
    }

    public static List<InstructionMetrics> collectAll(CompiledMapping compiled) {
        return collectAll(compiled.config(), compiled.program());
    }

    public static List<InstructionMetrics> collectAll(ResolvedConfig config, MappingProgram program) {
        InstructionProgram instructions = program.program();
        List<InstructionMetrics> metrics = new ArrayList<>(config.mappings().size());
        for (ResolvedMapping mapping : config.mappings()) {
            metrics.add(collectMapping(instructions, mapping));
        }
        return Collections.unmodifiableList(metrics);
    }

    private static InstructionMetrics collectMapping(InstructionProgram instructions, ResolvedMapping mapping) {
        InstructionBlock block = instructions.blocks().get(mapping.id());
        EnumMap<OpCode, Integer> counts = new EnumMap<>(OpCode.class);
        for (OpCode opcode : OpCode.values()) {
            counts.put(opcode, 0);
        }
        int totalOpcodes = 0;
        Set<Integer> inlineMappings = new HashSet<>();
        OpCode[] opcodes = block.opcodes();
        int[][] operands = block.operands();
        for (int i = 0; i < opcodes.length; i++) {
            OpCode opcode = opcodes[i];
            counts.put(opcode, counts.get(opcode) + 1);
            totalOpcodes++;
            if (opcode == OpCode.WRITE_MAPPING) {
                inlineMappings.add(operands[i][0]);
            }
        }
        return new InstructionMetrics(
            mapping.id(),
            mapping.name(),
            totalOpcodes,
            Collections.unmodifiableMap(new EnumMap<>(counts)),
            instructions.literals().size(),
            instructions.fieldNames().size(),
            Collections.unmodifiableSet(inlineMappings)
        );
    }

    public record InstructionMetrics(int mappingId,
                                     String mappingName,
                                     int totalOpcodes,
                                     Map<OpCode, Integer> opcodeCounts,
                                     int literalPoolSize,
                                     int fieldPoolSize,
                                     Set<Integer> inlineMappingIds) {
        public int opcodeCount(OpCode opcode) {
            return opcodeCounts.getOrDefault(opcode, 0);
        }

        public boolean hasInlineMapping(int mappingId) {
            return inlineMappingIds.contains(mappingId);
        }
    }
}
