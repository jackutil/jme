package github.jackutil.compiler.runtime;

import java.util.List;

import github.jackutil.compiler.ir.MappingProgram;
import github.jackutil.compiler.ir.enums.OpCode;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * Emits JFR events with instruction metrics when enabled via a system property.
 */
public final class InstructionMetricsRecorder {
    private static final String PROFILE_PROPERTY = "jme.profile.instructions";

    private InstructionMetricsRecorder() {
    }

    public static void record(ResolvedConfig config, MappingProgram program) {
        if (!Boolean.getBoolean(PROFILE_PROPERTY)) {
            return;
        }
        try {
            List<InstructionMetricsCollector.InstructionMetrics> metrics =
                InstructionMetricsCollector.collectAll(config, program);
            for (InstructionMetricsCollector.InstructionMetrics metric : metrics) {
                InstructionMetricsEvent event = new InstructionMetricsEvent();
                event.mappingId = metric.mappingId();
                event.mappingName = metric.mappingName();
                event.totalOpcodes = metric.totalOpcodes();
                event.literalPoolSize = metric.literalPoolSize();
                event.fieldPoolSize = metric.fieldPoolSize();
                event.inlineMappingCount = metric.inlineMappingIds().size();
                event.writeLiteralCount = metric.opcodeCount(OpCode.WRITE_LITERAL);
                event.writeMappingCount = metric.opcodeCount(OpCode.WRITE_MAPPING);
                event.writeVariableCount = metric.opcodeCount(OpCode.WRITE_VARIABLE);
                event.writeInputCount = metric.opcodeCount(OpCode.WRITE_INPUT);
                event.writeFieldCount = metric.opcodeCount(OpCode.WRITE_FIELD);
                event.commit();
            }
        } catch (LinkageError ignored) {
            // JFR not available on this runtime; ignore quietly.
        }
    }

    @Name("jme.InstructionMetrics")
    @Label("Instruction Metrics")
    @Category({"JME", "Instructions"})
    @Description("Opcode distribution per mapping after compilation")
    @Enabled
    private static class InstructionMetricsEvent extends Event {
        @Label("Mapping Id")
        int mappingId;

        @Label("Mapping Name")
        String mappingName;

        @Label("Total Opcodes")
        int totalOpcodes;

        @Label("WRITE_LITERAL Count")
        int writeLiteralCount;

        @Label("WRITE_VARIABLE Count")
        int writeVariableCount;

        @Label("WRITE_MAPPING Count")
        int writeMappingCount;

        @Label("WRITE_INPUT Count")
        int writeInputCount;

        @Label("WRITE_FIELD Count")
        int writeFieldCount;

        @Label("Literal Pool Size")
        int literalPoolSize;

        @Label("Field Pool Size")
        int fieldPoolSize;

        @Label("Inline Mapping Count")
        int inlineMappingCount;
    }
}

