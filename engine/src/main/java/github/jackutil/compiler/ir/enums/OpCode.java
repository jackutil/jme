package github.jackutil.compiler.ir.enums;

public enum OpCode {
    BEGIN_OBJECT,
    END_OBJECT,
    BEGIN_ARRAY,
    END_ARRAY,
    WRITE_FIELD,
    WRITE_LITERAL,
    WRITE_VARIABLE,
    WRITE_INPUT,
    WRITE_MAPPING,
    WRITE_CONST,
    NO_OP
}
