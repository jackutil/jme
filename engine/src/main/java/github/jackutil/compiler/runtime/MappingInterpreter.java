package github.jackutil.compiler.runtime;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;

import github.jackutil.compiler.ir.InstructionBlock;
import github.jackutil.compiler.ir.InstructionProgram;
import github.jackutil.compiler.ir.enums.OpCode;

final class MappingInterpreter {

    void execute(ExecutionContext context, int blockIndex) throws IOException {
        InstructionProgram program = context.program();
        InstructionBlock block = program.blocks().get(blockIndex);
        OpCode[] opcodes = block.opcodes();
        int[][] operands = block.operands();
        JsonGenerator generator = context.generator();
        for (int i = 0; i < opcodes.length; i++) {
            OpCode opcode = opcodes[i];
            int[] operand = operands[i];
            switch (opcode) {
                case BEGIN_OBJECT -> generator.writeStartObject();
                case END_OBJECT -> generator.writeEndObject();
                case BEGIN_ARRAY -> generator.writeStartArray();
                case END_ARRAY -> generator.writeEndArray();
                case WRITE_FIELD -> {
                    String fieldName = program.fieldNames().get(operand[0]);
                    generator.writeFieldName(fieldName);
                }
                case WRITE_LITERAL -> writeValue(generator, program.literals().get(operand[0]));
                case WRITE_VARIABLE -> writeValue(generator, context.variableResolver().valueOf(operand[0]));
                case WRITE_INPUT -> writeValue(generator, context.inputResolver().valueOf(operand[0]));
                case WRITE_MAPPING -> execute(context, operand[0]);
                case WRITE_CONST, NO_OP -> {
                    // reserved for future use
                }
                default -> throw new IllegalArgumentException("Unexpected value: " + opcode);
            }
        }
    }

    private void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (value instanceof String s) {
            generator.writeString(s);
            return;
        }
        if (value instanceof Integer i) {
            generator.writeNumber(i);
            return;
        }
        if (value instanceof Long l) {
            generator.writeNumber(l);
            return;
        }
        if (value instanceof Double d) {
            generator.writeNumber(d);
            return;
        }
        if (value instanceof Float f) {
            generator.writeNumber(f);
            return;
        }
        if (value instanceof BigInteger bi) {
            generator.writeNumber(bi);
            return;
        }
        if (value instanceof BigDecimal bd) {
            generator.writeNumber(bd);
            return;
        }
        if (value instanceof Boolean b) {
            generator.writeBoolean(b);
            return;
        }
        if (value instanceof List<?> list) {
            generator.writeStartArray();
            for (Object element : list) {
                writeValue(generator, element);
            }
            generator.writeEndArray();
            return;
        }
        if (value instanceof Map<?, ?> map) {
            generator.writeStartObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                generator.writeFieldName(String.valueOf(entry.getKey()));
                writeValue(generator, entry.getValue());
            }
            generator.writeEndObject();
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            generator.writeStartArray();
            for (Object element : iterable) {
                writeValue(generator, element);
            }
            generator.writeEndArray();
            return;
        }
        if (value.getClass().isArray()) {
            generator.writeStartArray();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                writeValue(generator, java.lang.reflect.Array.get(value, i));
            }
            generator.writeEndArray();
            return;
        }
        generator.writeString(value.toString());
    }
}



