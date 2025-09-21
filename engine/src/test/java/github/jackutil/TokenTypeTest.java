package github.jackutil;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public class TokenTypeTest {

    @Test
    public void fromLexemeReturnsMatchingToken() {
        for (TokenType type : TokenType.values()) {
            if (type == TokenType.IDENT) {
                continue;
            }

            assertSame("Expected lookup to resolve " + type + " for lexeme", type, TokenType.fromLexeme(type.getLexeme()));
        }
    }

    @Test
    public void fromLexemeFallsBackToIdent() {
        assertSame(TokenType.IDENT, TokenType.fromLexeme("UNKNOWN"));
        assertSame(TokenType.IDENT, TokenType.fromLexeme(null));
    }
}
