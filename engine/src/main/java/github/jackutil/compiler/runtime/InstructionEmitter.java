package github.jackutil.compiler.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import github.jackutil.compiler.ir.InstructionBlock;
import github.jackutil.compiler.ir.InstructionProgram;
import github.jackutil.compiler.ir.enums.OpCode;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedMapNode;
import github.jackutil.compiler.ir.resolved.ResolvedMapping;

public final class InstructionEmitter {

    public InstructionProgram emit(ResolvedConfig config) {
        List<String> fieldNames = new ArrayList<>();
        List<Object> literals = new ArrayList<>();
        Map<String, Integer> fieldNameIndex = new HashMap<>();
        Map<Object, Integer> literalIndex = new HashMap<>();

        List<InstructionBlock> blocks = new ArrayList<>(config.mappings().size());
        for (int i = 0; i < config.mappings().size(); i++) {
            blocks.add(null);
        }
        for (ResolvedMapping mapping : config.mappings()) {
            blocks.set(mapping.id(), emitMapping(mapping, fieldNames, literals, fieldNameIndex, literalIndex));
        }
        return new InstructionProgram(blocks, fieldNames, literals);
    }

    private InstructionBlock emitMapping(ResolvedMapping mapping,
                                         List<String> fieldNames,
                                         List<Object> literals,
                                         Map<String, Integer> fieldNameIndex,
                                         Map<Object, Integer> literalIndex) {
        List<OpCode> opcodes = new ArrayList<>();
        List<int[]> operands = new ArrayList<>();

        emitNode(mapping.root(), opcodes, operands, fieldNames, literals, fieldNameIndex, literalIndex);

        return new InstructionBlock(opcodes.toArray(OpCode[]::new), operands.toArray(int[][]::new));
    }

    private void emitNode(ResolvedMapNode node,
                          List<OpCode> opcodes,
                          List<int[]> operands,
                          List<String> fieldNames,
                          List<Object> literals,
                          Map<String, Integer> fieldNameIndex,
                          Map<Object, Integer> literalIndex) {
        if (node instanceof ResolvedMapNode.LiteralNode literalNode) {
            int index = internLiteral(literals, literalIndex, literalNode.value());
            opcodes.add(OpCode.WRITE_LITERAL);
            operands.add(new int[]{index});
            return;
        }
        if (node instanceof ResolvedMapNode.VariableRefNode variableRefNode) {
            opcodes.add(OpCode.WRITE_VARIABLE);
            operands.add(new int[]{variableRefNode.variableId()});
            return;
        }
        if (node instanceof ResolvedMapNode.InputRefNode inputRefNode) {
            opcodes.add(OpCode.WRITE_INPUT);
            operands.add(new int[]{inputRefNode.inputId()});
            return;
        }
        if (node instanceof ResolvedMapNode.MappingRefNode mappingRefNode) {
            opcodes.add(OpCode.WRITE_MAPPING);
            operands.add(new int[]{mappingRefNode.mappingId()});
            return;
        }
        if (node instanceof ResolvedMapNode.ObjectNode objectNode) {
            opcodes.add(OpCode.BEGIN_OBJECT);
            operands.add(new int[0]);
            for (ResolvedMapNode.ObjectNode.Field field : objectNode.fields()) {
                int fieldNameId = internField(fieldNames, fieldNameIndex, field.name());
                opcodes.add(OpCode.WRITE_FIELD);
                operands.add(new int[]{fieldNameId});
                emitNode(field.value(), opcodes, operands, fieldNames, literals, fieldNameIndex, literalIndex);
            }
            opcodes.add(OpCode.END_OBJECT);
            operands.add(new int[0]);
            return;
        }
        if (node instanceof ResolvedMapNode.ArrayNode arrayNode) {
            opcodes.add(OpCode.BEGIN_ARRAY);
            operands.add(new int[0]);
            for (ResolvedMapNode element : arrayNode.elements()) {
                emitNode(element, opcodes, operands, fieldNames, literals, fieldNameIndex, literalIndex);
            }
            opcodes.add(OpCode.END_ARRAY);
            operands.add(new int[0]);
        }
    }

    private int internField(List<String> pool, Map<String, Integer> index, String value) {
        return index.computeIfAbsent(value, key -> {
            pool.add(key);
            return pool.size() - 1;
        });
    }

    private int internLiteral(List<Object> pool, Map<Object, Integer> index, Object value) {
        return index.computeIfAbsent(value, key -> {
            pool.add(key);
            return pool.size() - 1;
        });
    }
}




