package github.jackutil.compiler.ir;

public record MappingProgram(InstructionProgram program) {
    public static MappingProgram empty() {
        return new MappingProgram(InstructionProgram.empty());
    }
}
