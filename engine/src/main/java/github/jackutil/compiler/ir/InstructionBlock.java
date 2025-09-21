package github.jackutil.compiler.ir;

import github.jackutil.compiler.ir.enums.OpCode;

public record InstructionBlock(OpCode[] opcodes, int[][] operands) {
}
