package github.jackutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import github.jackutil.compiler.diagnostics.MappingException;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.ConfigCompiler;
import github.jackutil.compiler.ir.InstructionBlock;
import github.jackutil.compiler.ir.InstructionProgram;
import github.jackutil.compiler.ir.enums.OpCode;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedMapping;
import github.jackutil.compiler.ir.resolved.ResolvedVariable;

public class ConfigCompilerTest {

    @Test
    public void compilesReferenceAwareConfig() throws Exception {
        try (InputStream in = resource("valid/refs.json")) {
            CompiledMapping mapping = ConfigCompiler.compile(in);
            ResolvedConfig config = mapping.config();
            assertEquals(1, config.variables().size());
            ResolvedVariable variable = config.variables().get(0);
            assertEquals(1, variable.constraintFunctionIds().length);
            assertNull(variable.deriveFunctionId());

            assertEquals(1, config.mappings().size());
            ResolvedMapping root = config.mappings().get(0);
            assertNotNull(root.root());

            InstructionProgram program = mapping.program().program();
            assertEquals(1, program.blocks().size());
            InstructionBlock block = program.blocks().get(0);
            OpCode[] opcodes = block.opcodes();
            assertEquals(4, opcodes.length);
            assertEquals(OpCode.BEGIN_OBJECT, opcodes[0]);
            assertEquals(OpCode.WRITE_FIELD, opcodes[1]);
            assertEquals(OpCode.WRITE_VARIABLE, opcodes[2]);
            assertEquals(OpCode.END_OBJECT, opcodes[3]);
        }
    }

    @Test
    public void compilesArrayMapping() throws Exception {
        try (InputStream in = resource("valid/arrays.json")) {
            CompiledMapping mapping = ConfigCompiler.compile(in);
            InstructionProgram program = mapping.program().program();
            InstructionBlock block = program.blocks().get(mapping.config().mappings().get(0).id());
            OpCode[] opcodes = block.opcodes();
            assertEquals(OpCode.BEGIN_OBJECT, opcodes[0]);
            assertEquals(OpCode.WRITE_FIELD, opcodes[1]);
            assertEquals(OpCode.WRITE_VARIABLE, opcodes[2]);
            assertEquals(OpCode.WRITE_FIELD, opcodes[3]);
            assertEquals(OpCode.WRITE_VARIABLE, opcodes[4]);
            assertEquals(OpCode.END_OBJECT, opcodes[5]);
        }
    }

    @Test
    public void failsWhenVariableReferenceMissing() {
        try (InputStream in = resource("invalid/mapping-unknown-variable.json")) {
            assertThrows(MappingException.class, () -> ConfigCompiler.compile(in));
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    @Test
    public void failsOnMappingCycle() {
        try (InputStream in = resource("invalid/mapping-cycle.json")) {
            assertThrows(MappingException.class, () -> ConfigCompiler.compile(in));
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    @Test
    public void failsOnUnknownFunctionReference() {
        try (InputStream in = resource("invalid/unknown-function.json")) {
            assertThrows(MappingException.class, () -> ConfigCompiler.compile(in));
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    @Test
    public void failsOnUnknownSchemaReference() {
        try (InputStream in = resource("invalid/unknown-schema.json")) {
            assertThrows(MappingException.class, () -> ConfigCompiler.compile(in));
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    @Test
    public void optimizesConstantMappings() throws Exception {
        try (InputStream in = resource("valid/constants.json")) {
            CompiledMapping mapping = ConfigCompiler.compile(in);
            ResolvedConfig config = mapping.config();
            InstructionProgram program = mapping.program().program();

            ResolvedMapping root = mapping(config, "root");
            InstructionBlock rootBlock = program.blocks().get(root.id());
            OpCode[] opcodes = rootBlock.opcodes();
            int[][] operands = rootBlock.operands();

            assertEquals(10, opcodes.length);
            assertEquals(OpCode.BEGIN_OBJECT, opcodes[0]);
            assertEquals(OpCode.WRITE_FIELD, opcodes[1]);
            assertEquals(OpCode.WRITE_LITERAL, opcodes[2]);
            assertEquals(OpCode.WRITE_FIELD, opcodes[3]);
            assertEquals(OpCode.WRITE_LITERAL, opcodes[4]);
            assertEquals(OpCode.WRITE_FIELD, opcodes[5]);
            assertEquals(OpCode.WRITE_MAPPING, opcodes[6]);
            assertEquals(OpCode.WRITE_FIELD, opcodes[7]);
            assertEquals(OpCode.WRITE_MAPPING, opcodes[8]);
            assertEquals(OpCode.END_OBJECT, opcodes[9]);

            assertEquals(operands[2][0], operands[4][0]);

            ResolvedMapping singleUse = mapping(config, "singleUse");
            InstructionBlock singleBlock = program.blocks().get(singleUse.id());
            assertEquals(1, singleBlock.opcodes().length);
            assertEquals(OpCode.WRITE_LITERAL, singleBlock.opcodes()[0]);
            int singleLiteralIndex = singleBlock.operands()[0][0];

            ResolvedMapping sharedConst = mapping(config, "sharedConst");
            InstructionBlock sharedBlock = program.blocks().get(sharedConst.id());
            assertEquals(1, sharedBlock.opcodes().length);
            assertEquals(OpCode.WRITE_LITERAL, sharedBlock.opcodes()[0]);
            int sharedLiteralIndex = sharedBlock.operands()[0][0];
            assertNotEquals(singleLiteralIndex, sharedLiteralIndex);

            assertEquals(sharedConst.id(), operands[6][0]);
            assertEquals(sharedConst.id(), operands[8][0]);

            assertEquals(2, program.literals().size());
        }
    }

    @Test
    public void poolsLiteralsAcrossMappings() throws Exception {
        try (InputStream in = resource("valid/literal-pool.json")) {
            CompiledMapping mapping = ConfigCompiler.compile(in);
            ResolvedConfig config = mapping.config();
            InstructionProgram program = mapping.program().program();

            ResolvedMapping root = mapping(config, "root");
            ResolvedMapping support = mapping(config, "support");

            Map<?, ?> rootLiteral = castToMap(program.literals().get(program.blocks().get(root.id()).operands()[0][0]));
            Map<?, ?> supportLiteral = castToMap(program.literals().get(program.blocks().get(support.id()).operands()[0][0]));

            Map<?, ?> firstLiteral = castToMap(rootLiteral.get("first"));
            Map<?, ?> secondLiteral = castToMap(rootLiteral.get("second"));
            assertSame("Support literal should back first field", supportLiteral, firstLiteral);
            assertSame("Support literal should back second field", supportLiteral, secondLiteral);

            List<?> tags = castToList(supportLiteral.get("tags"));
            assertEquals(2, tags.size());
            assertEquals("alpha", tags.get(0));
            assertEquals("beta", tags.get(1));
        }
    }

    @Test
    public void inlinesSingleUseMappings() throws Exception {
        try (InputStream in = resource("valid/inline.json")) {
            CompiledMapping mapping = ConfigCompiler.compile(in);
            ResolvedConfig config = mapping.config();
            InstructionProgram program = mapping.program().program();

            ResolvedMapping root = mapping(config, "root");
            ResolvedMapping detail = mapping(config, "detail");
            ResolvedMapping shared = mapping(config, "shared");

            InstructionBlock rootBlock = program.blocks().get(root.id());
            OpCode[] opcodes = rootBlock.opcodes();
            int[][] operands = rootBlock.operands();

            int writeMappingCount = 0;
            for (int i = 0; i < opcodes.length; i++) {
                if (opcodes[i] == OpCode.WRITE_MAPPING) {
                    writeMappingCount++;
                    assertEquals(shared.id(), operands[i][0]);
                }
            }
            assertEquals(2, writeMappingCount);

            boolean inlinedDetail = false;
            for (int i = 0; i < opcodes.length - 1; i++) {
                if (opcodes[i] == OpCode.WRITE_FIELD) {
                    String fieldName = program.fieldNames().get(operands[i][0]);
                    if ("detail".equals(fieldName)) {
                        inlinedDetail = opcodes[i + 1] == OpCode.BEGIN_OBJECT;
                        break;
                    }
                }
            }
            assertTrue("detail field should inline object instructions", inlinedDetail);

            InstructionBlock detailBlock = program.blocks().get(detail.id());
            assertEquals(OpCode.BEGIN_OBJECT, detailBlock.opcodes()[0]);
        }
    }

        @SuppressWarnings("unchecked")
    private Map<?, ?> castToMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new AssertionError("Expected map literal but got: " + value);
        }
        return map;
    }

    private List<?> castToList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new AssertionError("Expected list literal but got: " + value);
        }
        return list;
    }

    private ResolvedMapping mapping(ResolvedConfig config, String name) {
        return config.mappings().stream()
            .filter(m -> m.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing mapping: " + name));
    }

    private InputStream resource(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw MappingException.of("TEST_RESOURCE_MISSING", "Missing test resource: " + name, "/test");
        }
        return stream;
    }
}



