package github.jackutil;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum TokenType {
    IDENT(null),
    API("API"),
    CONFIG("CONFIG"),
    DEF("DEF"),
    ENGINE("ENGINE"),
    FUNCTIONS("FUNCTIONS"),
    INPUT("INPUT"),
    MAP("MAP"),
    MAPPING("MAPPING"),
    MAPPINGS("MAPPINGS"),
    META("META"),
    NAME("NAME"),
    OUT("OUT"),
    REF("REF"),
    SCHEMA("SCHEMA"),
    VALIDATE("VALIDATE"),
    VALIDATION("VALIDATION"),
    VARIABLES("VARIABLES"),
    VAR("VAR");

    private final String lexeme;

    private static final Map<String, TokenType> KEYWORDS = Arrays.stream(TokenType.values())
        .filter(type -> type.lexeme != null)
        .collect(Collectors.toUnmodifiableMap(TokenType::getLexeme, type -> type));

    TokenType(String lexeme) {
        this.lexeme = lexeme;
    }

    String getLexeme() {
        return lexeme;
    }

    public static TokenType fromLexeme(String lexeme) {
        if (lexeme == null) {
            return IDENT;
        }
        return KEYWORDS.getOrDefault(lexeme, IDENT);
    }
}
