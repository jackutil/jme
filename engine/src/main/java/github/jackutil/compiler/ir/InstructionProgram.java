package github.jackutil.compiler.ir;

import java.util.List;

public record InstructionProgram(List<InstructionBlock> blocks,
                                 List<String> fieldNames,
                                 List<Object> literals) {
    public static InstructionProgram empty() {
        return new InstructionProgram(List.of(), List.of(), List.of());
    }
}
