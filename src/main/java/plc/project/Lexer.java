package plc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the invalid character.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation easier.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> tokens = new ArrayList<>();

        while (chars.has(0)) {
            // Skip whitespace
            if (peek("[ \\t\\n\\r]")) {
                match("[ \\t\\n\\r]");
                chars.skip();  // Reset token length
            } else {
                // Lex the next token
                tokens.add(lexToken());
            }
        }

        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        // Identifier: starts with letter or underscore
        if (peek("[A-Za-z_]")) {
            return lexIdentifier();
        }
        // Number: starts with +/- followed by digit, or just a digit
        else if (peek("[+-]", "[0-9]") || peek("[0-9]")) {
            return lexNumber();
        }
        // Character: starts with single quote '
        else if (peek("'")) {
            return lexCharacter();
        }
        // String: starts with double quote "
        else if (peek("\"")) {
            return lexString();
        }
        // Everything else is an operator
        else {
            return lexOperator();
        }
    }

    public Token lexIdentifier() {
        // Match first character: letter or underscore
        if (!match("[A-Za-z_]")) {
            throw new ParseException("Identifier must start with a letter or underscore", chars.index);
        }

        // Keep matching: letters, digits, underscores, or hyphens
        while (match("[A-Za-z0-9_-]")) {
            // Just keep consuming characters
        }

        // Emit the IDENTIFIER token
        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {
        // Match optional sign
        match("[+-]");

        // Check for leading zero
        if (peek("0", "\\.")) {
            // "0." is start of a decimal like "0.5"
            match("0");
            match("\\.");
            if (!match("[0-9]")) {
                throw new ParseException("Decimal must have digits after decimal point", chars.index);
            }
            while (match("[0-9]")) {
                // consume remaining decimal digits
            }
            return chars.emit(Token.Type.DECIMAL);
        } else if (peek("0")) {
            // Just "0" by itself
            match("0");
            return chars.emit(Token.Type.INTEGER);
        }

        // Non-zero number
        if (!match("[1-9]")) {
            throw new ParseException("Number must start with a digit", chars.index);
        }

        // Match remaining digits
        while (match("[0-9]")) {
            // consume digits
        }

        // Check for decimal point
        if (peek("\\.", "[0-9]")) {
            match("\\.");
            match("[0-9]");
            while (match("[0-9]")) {
                // consume decimal digits
            }
            return chars.emit(Token.Type.DECIMAL);
        }

        // No decimal point, it's an integer
        return chars.emit(Token.Type.INTEGER);
    }

    public Token lexCharacter() {
        // Match opening single quote
        if (!match("'")) {
            throw new ParseException("Character must start with '", chars.index);
        }

        // Check for escape sequence (e.g., '\n', '\t')
        if (peek("\\\\")) {
            lexEscape();  // Handle \b, \n, \r, \t, ', ", \
        }
        // Check for valid single character (not quote, newline, or carriage return)
        else if (chars.has(0) && chars.get(0) != '\'' && chars.get(0) != '\n' && chars.get(0) != '\r') {
            chars.advance();  // Consume the character
        }
        // Empty or invalid character
        else {
            throw new ParseException("Invalid or empty character literal", chars.index);
        }

        // Match closing single quote
        if (!match("'")) {
            throw new ParseException("Unterminated character literal", chars.index);
        }

        return chars.emit(Token.Type.CHARACTER);
    }


    public Token lexString() {
        // Match opening double quote
        if (!match("\"")) {
            throw new ParseException("String must start with \"", chars.index);
        }

        // Keep consuming characters until we hit closing quote, newline, or end of input
        while (chars.has(0) && chars.get(0) != '"' && chars.get(0) != '\n' && chars.get(0) != '\r') {
            // Check for escape sequence (e.g., \", \\, \n)
            if (peek("\\\\")) {
                lexEscape();  // Handle \b, \n, \r, \t, ', ", \
            } else {
                chars.advance();  // Regular character, consume it
            }
        }

        // Match closing double quote (or throw if unterminated)
        if (!match("\"")) {
            throw new ParseException("Unterminated string", chars.index);
        }

        return chars.emit(Token.Type.STRING);
    }

    public void lexEscape() {
        // Must start with backslash
        if (!match("\\\\")) {
            throw new ParseException("Escape must start with backslash", chars.index);
        }

        // Must be followed by one of: b n r t ' " \
        if (!match("[bnrt'\"\\\\]")) {
            throw new ParseException("Invalid escape sequence", chars.index);
        }
    }

    public Token lexOperator() {

        // Add support for logical && and || operators
        if (peek("&", "&")) {
            match("&");
            match("&");
            return chars.emit(Token.Type.OPERATOR);
        }
        if (peek("\\|", "\\|")) {
            match("\\|");
            match("\\|");
            return chars.emit(Token.Type.OPERATOR);
        }

        // Two-character comparisons: <=, >=, !=, ==
        if (peek("[<>!=]", "=")) {
            match("[<>!=]");
            match("=");
            return chars.emit(Token.Type.OPERATOR);
        }

        // Single character operators (common programming operators only)
        // Includes: + - * / ( ) [ ] { } ; : , . < > = !
        if (peek("[+\\-*/()\\[\\]{}:;,.<>=!]")) {
            match("[+\\-*/()\\[\\]{}:;,.<>=!]");
            return chars.emit(Token.Type.OPERATOR);
        }

        // Tokenize vertical tab and form feed
        if (peek("[\\u000B\\u000C\\f]")) {
            match("[\\u000B\\u000C\\f]");
            return chars.emit(Token.Type.OPERATOR);
        }


        // If we get here, it's an invalid character
        throw new ParseException("Invalid character", chars.index);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
