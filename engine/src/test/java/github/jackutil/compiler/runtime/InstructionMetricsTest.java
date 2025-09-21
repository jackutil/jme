package github.jackutil.compiler.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import org.junit.Test;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.ConfigCompiler;
import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.ir.enums.OpCode;
import github.jackutil.compiler.runtime.InstructionMetricsCollector.InstructionMetrics;

public class InstructionMetricsTest {

    @Test
    public void capturesMetricsForReferenceMapping() throws Exception {
        InstructionMetrics metrics = metrics("valid/refs.json", "root");
        assertEquals(4, metrics.totalOpcodes());
        assertEquals(1, metrics.opcodeCount(OpCode.WRITE_FIELD));
        assertEquals(1, metrics.opcodeCount(OpCode.WRITE_VARIABLE));
        assertEquals(0, metrics.opcodeCount(OpCode.WRITE_LITERAL));
        assertEquals(0, metrics.opcodeCount(OpCode.WRITE_MAPPING));
        assertEquals(0, metrics.literalPoolSize());
        assertEquals(1, metrics.fieldPoolSize());
    }

    @Test
    public void capturesMetricsForArrayMapping() throws Exception {
        InstructionMetrics metrics = metrics("valid/arrays.json", "root");
        assertEquals(6, metrics.totalOpcodes());
        assertEquals(2, metrics.opcodeCount(OpCode.WRITE_VARIABLE));
        assertEquals(0, metrics.opcodeCount(OpCode.WRITE_LITERAL));
        assertEquals(0, metrics.opcodeCount(OpCode.WRITE_MAPPING));
        assertEquals(0, metrics.literalPoolSize());
        assertEquals(2, metrics.fieldPoolSize());
    }

    @Test
    public void capturesMetricsForBuiltinsMapping() throws Exception {
        InstructionMetrics metrics = metrics("valid/builtins.json", "root");
        assertEquals(12, metrics.totalOpcodes());
        assertEquals(5, metrics.opcodeCount(OpCode.WRITE_VARIABLE));
        assertEquals(0, metrics.opcodeCount(OpCode.WRITE_LITERAL));
        assertEquals(0, metrics.opcodeCount(OpCode.WRITE_MAPPING));
        assertEquals(0, metrics.literalPoolSize());
        assertEquals(5, metrics.fieldPoolSize());
    }

    @Test
    public void capturesMetricsForConstantInlining() throws Exception {
        CompiledMapping compiled = compile("valid/constants.json");
        InstructionMetrics rootMetrics = InstructionMetricsCollector.collect(compiled, "root");
        InstructionMetrics singleUseMetrics = InstructionMetricsCollector.collect(compiled, "singleUse");
        InstructionMetrics sharedMetrics = InstructionMetricsCollector.collect(compiled, "sharedConst");

        assertEquals(10, rootMetrics.totalOpcodes());
        assertEquals(2, rootMetrics.opcodeCount(OpCode.WRITE_LITERAL));
        assertEquals(2, rootMetrics.opcodeCount(OpCode.WRITE_MAPPING));
        assertEquals(2, rootMetrics.literalPoolSize());
        assertEquals(4, rootMetrics.fieldPoolSize());
        assertEquals(1, rootMetrics.inlineMappingIds().size());
        assertTrue(rootMetrics.hasInlineMapping(mappingId(compiled, "sharedConst")));
        assertFalse(rootMetrics.hasInlineMapping(mappingId(compiled, "singleUse")));

        assertEquals(1, singleUseMetrics.totalOpcodes());
        assertEquals(1, singleUseMetrics.opcodeCount(OpCode.WRITE_LITERAL));
        assertEquals(0, singleUseMetrics.opcodeCount(OpCode.WRITE_MAPPING));

        assertEquals(1, sharedMetrics.totalOpcodes());
        assertEquals(1, sharedMetrics.opcodeCount(OpCode.WRITE_LITERAL));
        assertEquals(0, sharedMetrics.opcodeCount(OpCode.WRITE_MAPPING));
    }

    private InstructionMetrics metrics(String resource, String mappingName) throws Exception {
        CompiledMapping compiled = compile(resource);
        return InstructionMetricsCollector.collect(compiled, mappingName);
    }

    private CompiledMapping compile(String resource) throws Exception {
        try (InputStream in = resource(resource)) {
            return ConfigCompiler.compile(in);
        }
    }

    private InputStream resource(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw MappingException.of("TEST_RESOURCE_MISSING", "Missing test resource: " + name, "/test");
        }
        return stream;
    }

    private int mappingId(CompiledMapping compiled, String name) {
        return compiled.config().mappings().stream()
            .filter(mapping -> mapping.name().equals(name))
            .findFirst()
            .map(mapping -> mapping.id())
            .orElseThrow(() -> new AssertionError("Missing mapping: " + name));
    }
}
