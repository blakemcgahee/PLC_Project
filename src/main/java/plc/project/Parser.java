package plc.project;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling those functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        // source ::= field* method*
        // Array definitions for parseField and parseMethod
        List<Ast.Field>  fields = new ArrayList<>();
        List<Ast.Method>  methods = new ArrayList<>();

        // Look for "LET" or "DEF"; the only valid options
        while (tokens.has(0) && peek("LET")) {
            fields.add(parseField());
        }

        while (tokens.has(0) && peek("DEF")) {
            methods.add(parseMethod());
        }

        // All other possibilities are invalid at Source
        if (tokens.has(0)) {
            throw new ParseException("Unexpected token in Source: " + tokens.get(0).getLiteral(),
                    tokens.getErrorIndex());
        }

        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        // field ::= 'LET' 'CONST'? identifier ('=' expression)? ';'
        // Additional check for "LET"
        if (!match("LET")) {
            throw new ParseException("Expected LET", tokens.getErrorIndex());
        }
        // optional CONST (consumes token only if present)
        boolean constant = match("CONST");

        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected IDENTIFIER after LET (CONST)?", tokens.getErrorIndex());
        }
        String name = tokens.get(-1).getLiteral();

        Optional<Ast.Expression> value = Optional.empty();
        if (match("=")) {
            value = Optional.of(parseExpression());
        }
        if (!match(";")) {
            throw new ParseException("Expected ';' after declaration",
                    tokens.has(0) ? tokens.getErrorIndex() : 0);
        }
        // For: public Field(String name, boolean constant, Optional<Ast.Expression> value)
        // boolean constant tested for inclusion of CONST
        return new Ast.Field(name, constant, value);
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        // method ::= 'DEF' identifier '(' (identifier (',' identifier)*)? ')' 'DO' statement* 'END'
        // Additional check for "DEF"
        if (!match("DEF")) {
            throw new ParseException("Expected DEF", tokens.getErrorIndex());
        }

        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected IDENTIFIER after DEF", tokens.getErrorIndex());
        }

        String name = tokens.get(-1).getLiteral();

        if (!match("(")) {
            throw new ParseException("Expected '(' after method", tokens.getErrorIndex());
        }

        List<String> parameters = new ArrayList<>();
        List<String> parameterTypeNames = new ArrayList<>();

        if (peek(Token.Type.IDENTIFIER)) {
            // parameter list creation (identifier (',' identifier)*)?
            match(Token.Type.IDENTIFIER);
            parameters.add(tokens.get(-1).getLiteral());

            if (match(":")) {
                if (!match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected parameter after ':'", tokens.getErrorIndex());
                }
                parameterTypeNames.add(tokens.get(-1).getLiteral());
            } else {
                parameterTypeNames.add("Any");
            }
            while (match(",")) {
                if (!match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected IDENTIFIER after ','", tokens.getErrorIndex());
                }
                parameters.add(tokens.get(-1).getLiteral());

                if (match(":")) {
                    if (!match(Token.Type.IDENTIFIER)) {
                        throw new ParseException("Expected parameter after ':'", tokens.getErrorIndex());
                    }
                    parameterTypeNames.add(tokens.get(-1).getLiteral());
                } else {
                    parameterTypeNames.add("Any");
                }
            }
        }

        if (!match(")")) {
            throw new ParseException("Expected ')' after parameters", tokens.getErrorIndex());
        }

        Optional<String> returnTypeName = Optional.empty();
        if (match(":")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected return type after ':'", tokens.getErrorIndex());
            }
            returnTypeName = Optional.of(tokens.get(-1).getLiteral());
        }

        if (!match("DO")) {
            throw new ParseException("Expected DO before statements", tokens.getErrorIndex());
        }

        // Potential list of statement -> statement*
        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            if (!tokens.has(0)) {
                throw new ParseException("Expected END to terminate method", tokens.getErrorIndex());
            }
            statements.add(parseStatement());
        }

        if (!match("END")) {
            throw new ParseException("Expected END to terminate method", tokens.getErrorIndex());
        }

        return new Ast.Method(name, parameters, parameterTypeNames, returnTypeName, statements);
    }


    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, for, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        if (peek("LET")) {
            return parseDeclarationStatement();
        } else if (peek("IF")) {
            return parseIfStatement();
        } else if (peek("FOR")) {
            return parseForStatement();
        } else if (peek("WHILE")) {
            return parseWhileStatement();
        } else if (peek("RETURN")) {
            return parseReturnStatement();
        } else {
            // It must be an Expression or an Assignment
            Ast.Expression left = parseExpression();

            // Check if it is an assignment (e.g., name = value;)
            if (match("=")) {
                Ast.Expression right = parseExpression();

                if (!match(";")) {
                    // Handle missing semicolon safely (check if at end of stream)
                    int index = tokens.has(0) ? tokens.getErrorIndex() :
                            tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ';' after assignment", index);
                }

                return new Ast.Statement.Assignment(left, right);
            }
            // Otherwise, it is just an expression statement (e.g., name();)
            else {
                if (!match(";")) {
                    int index = tokens.has(0) ? tokens.getErrorIndex() :
                            tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
                    throw new ParseException("Expected ';' after expression statement", index);
                }

                return new Ast.Statement.Expression(left);
            }
        }
    }

    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        match("LET");

        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after LET",
                    tokens.has(0) ? tokens.getErrorIndex() : 0);
        }

        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        Optional<String> typeName = Optional.empty();
        if (match(":")) {
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected IDENTIFIER after ':'",
                        tokens.has(0) ? tokens.getErrorIndex() : 0);
            }
            typeName = Optional.of(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
        }

        Optional<Ast.Expression> value = Optional.empty();
        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        if (!match(";")) {
            throw new ParseException("Expected ';' after declaration",
                    tokens.has(0) ? tokens.getErrorIndex() : 0);
        }

        return new Ast.Statement.Declaration(name, typeName, value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        match("IF");
        Ast.Expression condition = parseExpression();

        if (!match("DO")) {
            throw new ParseException("Expected DO after IF condition",
                    tokens.has(0) ? tokens.getErrorIndex() : tokens.get(-1).getIndex());
        }

        List<Ast.Statement> thenStatements = new ArrayList<>();
        // Keep parsing statements until we hit ELSE or END
        while (!peek("ELSE") && !peek("END")) {
            thenStatements.add(parseStatement());
        }

        List<Ast.Statement> elseStatements = new ArrayList<>();
        if (match("ELSE")) {
            while (!peek("END")) {
                elseStatements.add(parseStatement());
            }
        }

        if (!match("END")) {
            throw new ParseException("Expected END after IF statement",
                    tokens.has(0) ? tokens.getErrorIndex() : tokens.get(-1).getIndex());
        }

        return new Ast.Statement.If(condition, thenStatements, elseStatements);
    }

    public Ast.Statement.For parseForStatement() throws ParseException {
        match("FOR");

        if (!match("(")) {
            throw new ParseException("Expected '(' after FOR", tokens.getErrorIndex());
        }

        // Optional initialization
        Ast.Statement initialization = null;
        if (!peek(";")) {
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier in FOR initialization", tokens.getErrorIndex());
            }
            String name = tokens.get(0).getLiteral();
            match(Token.Type.IDENTIFIER);

            if (!match("=")) {
                throw new ParseException("Expected '=' in FOR initialization", tokens.getErrorIndex());
            }

            Ast.Expression value = parseExpression();
            initialization = new Ast.Statement.Assignment(
                    new Ast.Expression.Access(Optional.empty(), name),
                    value
            );
        }

        if (!match(";")) {
            throw new ParseException("Expected ';' after FOR initialization", tokens.getErrorIndex());
        }

        // Condition (required)
        Ast.Expression condition = parseExpression();

        if (!match(";")) {
            throw new ParseException("Expected ';' after FOR condition", tokens.getErrorIndex());
        }

        // Optional increment
        Ast.Statement increment = null;
        if (!peek(")")) {
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier in FOR increment", tokens.getErrorIndex());
            }
            String name = tokens.get(0).getLiteral();
            match(Token.Type.IDENTIFIER);

            if (!match("=")) {
                throw new ParseException("Expected '=' in FOR increment", tokens.getErrorIndex());
            }

            Ast.Expression value = parseExpression();
            increment = new Ast.Statement.Assignment(
                    new Ast.Expression.Access(Optional.empty(), name),
                    value
            );
        }

        if (!match(")")) {
            throw new ParseException("Expected ')' after FOR header", tokens.getErrorIndex());
        }

        // Parse body statements
        // Note: Initially forgot to check for END, caused infinite loop
        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            if (!tokens.has(0)) {
                throw new ParseException("Expected END after FOR loop", tokens.getErrorIndex());
            }
            statements.add(parseStatement());
        }

        if (!match("END")) {
            throw new ParseException("Expected END after FOR loop", tokens.getErrorIndex());
        }

        // The constructor expects 4 arguments: init statement, condition, increment statement, body
        // For optional parts, we need to pass actual Statement objects or handle them appropriately
        return new Ast.Statement.For(initialization, condition, increment, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        match("WHILE");
        Ast.Expression condition = parseExpression();

        if (!match("DO")) {
            throw new ParseException("Expected DO after WHILE condition",
                    tokens.has(0) ? tokens.getErrorIndex() : tokens.get(-1).getIndex());
        }

        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            statements.add(parseStatement());
        }

        if (!match("END")) {
            throw new ParseException("Expected END after WHILE loop",
                    tokens.has(0) ? tokens.getErrorIndex() : tokens.get(-1).getIndex());
        }

        return new Ast.Statement.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        match("RETURN");
        Ast.Expression value = parseExpression();

        if (!match(";")) {
            throw new ParseException("Expected ';' after return value",
                    tokens.has(0) ? tokens.getErrorIndex() : tokens.get(-1).getIndex());
        }

        return new Ast.Statement.Return(value);
    }

    /**
     * Parses the expression rule - entry point for all expressions.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses logical expressions: && and ||
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        // 1. Parse the left side (higher precedence)
        Ast.Expression result = parseComparisonExpression();

        // 2. Loop while we see specific operators for this level
        while (peek("&&") || peek("||")) {
            String operator = tokens.get(0).getLiteral();
            match(Token.Type.OPERATOR); // Advance past the operator

            // 3. Parse the right side
            Ast.Expression right = parseComparisonExpression();

            // 4. Wrap in a Binary Expression
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses comparison expressions: ==, !=, <, >, <=, >=
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression result = parseAdditiveExpression();

        while (peek("==") || peek("!=") || peek("<") || peek("<=") || peek(">") || peek(">=")) {
            String operator = tokens.get(0).getLiteral();
            match(Token.Type.OPERATOR);

            Ast.Expression right = parseAdditiveExpression();
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses additive expressions: + and -
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression result = parseMultiplicativeExpression();

        while (peek("+") || peek("-")) {
            String operator = tokens.get(0).getLiteral();
            match(Token.Type.OPERATOR);

            Ast.Expression right = parseMultiplicativeExpression();
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses multiplicative expressions: * and /
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression result = parseSecondaryExpression();

        while (peek("*") || peek("/")) {
            String operator = tokens.get(0).getLiteral();
            match(Token.Type.OPERATOR);

            Ast.Expression right = parseSecondaryExpression();
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses secondary expressions: field access (dot notation)
     */
    public Ast.Expression parseSecondaryExpression() throws ParseException {
        Ast.Expression expression = parsePrimaryExpression();

        // Keep parsing '.' access as long as we see dots
        while (match(".")) {
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier after '.'", tokens.getErrorIndex());
            }

            Token fieldToken = tokens.get(0);
            match(Token.Type.IDENTIFIER);
            String fieldName = fieldToken.getLiteral();

            // Check if it's a method call: obj.method()
            if (match("(")) {
                List<Ast.Expression> arguments = new ArrayList<>();

                // Parse arguments if present
                if (!peek(")")) {
                    arguments.add(parseExpression());
                    while (match(",")) {
                        arguments.add(parseExpression());
                    }
                }

                if (!match(")")) {
                    throw new ParseException("Expected closing ')'", tokens.getErrorIndex());
                }

                expression = new Ast.Expression.Function(Optional.of(expression), fieldName, arguments);
            }
            // Just field access: obj.field
            else {
                expression = new Ast.Expression.Access(Optional.of(expression), fieldName);
            }
        }

        return expression;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */

    /**
     * Parses primary expressions: literals, groups, variables, and function calls.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        // NIL literal
        if (match("NIL")) {
            return new Ast.Expression.Literal(null);
        }
        // TRUE literal
        else if (match("TRUE")) {
            return new Ast.Expression.Literal(Boolean.TRUE);
        }
        // FALSE literal
        else if (match("FALSE")) {
            return new Ast.Expression.Literal(Boolean.FALSE);
        }
        // INTEGER literal
        else if (peek(Token.Type.INTEGER)) {
            Token token = tokens.get(0);
            match(Token.Type.INTEGER);
            return new Ast.Expression.Literal(new java.math.BigInteger(token.getLiteral()));
        }
        // DECIMAL literal
        else if (peek(Token.Type.DECIMAL)) {
            Token token = tokens.get(0);
            match(Token.Type.DECIMAL);
            return new Ast.Expression.Literal(new java.math.BigDecimal(token.getLiteral()));
        }
        // CHARACTER literal
        else if (peek(Token.Type.CHARACTER)) {
            Token token = tokens.get(0);
            match(Token.Type.CHARACTER);
            // Remove quotes and handle escape sequences
            String literal = token.getLiteral();
            char value = literal.charAt(1); // Skip opening quote
            if (value == '\\') {
                // Handle escape sequence
                char escaped = literal.charAt(2);
                switch (escaped) {
                    case 'b': value = '\b'; break;
                    case 'n': value = '\n'; break;
                    case 'r': value = '\r'; break;
                    case 't': value = '\t'; break;
                    case '\'': value = '\''; break;
                    case '\"': value = '\"'; break;
                    case '\\': value = '\\'; break;
                    default: value = escaped;
                }
            }
            return new Ast.Expression.Literal(value);
        }
        // STRING literal
        else if (peek(Token.Type.STRING)) {
            Token token = tokens.get(0);
            match(Token.Type.STRING);
            // Remove quotes and handle escape sequences
            String literal = token.getLiteral();
            StringBuilder result = new StringBuilder();
            for (int i = 1; i < literal.length() - 1; i++) { // Skip quotes
                char c = literal.charAt(i);
                if (c == '\\' && i + 1 < literal.length() - 1) {
                    char next = literal.charAt(i + 1);
                    switch (next) {
                        case 'b': result.append('\b'); break;
                        case 'n': result.append('\n'); break;
                        case 'r': result.append('\r'); break;
                        case 't': result.append('\t'); break;
                        case '\'': result.append('\''); break;
                        case '\"': result.append('\"'); break;
                        case '\\': result.append('\\'); break;
                        default: result.append(next);
                    }
                    i++; // Skip next character
                } else {
                    result.append(c);
                }
            }
            return new Ast.Expression.Literal(result.toString());
        }
        // Grouped expression: '(' expression ')'
        else if (match("(")) {
            Ast.Expression expression = parseExpression();
            if (!match(")")) {
                throw new ParseException("Expected closing ')'", tokens.getErrorIndex());
            }
            return new Ast.Expression.Group(expression);
        }
        // Identifier (variable or function call)
        else if (peek(Token.Type.IDENTIFIER)) {
            Token token = tokens.get(0);
            match(Token.Type.IDENTIFIER);
            String name = token.getLiteral();

            // Check for function call: identifier '(' ... ')'
            if (match("(")) {
                List<Ast.Expression> arguments = new java.util.ArrayList<>();

                // Parse arguments if present
                if (!peek(")")) {
                    arguments.add(parseExpression());
                    while (match(",")) {
                        arguments.add(parseExpression());
                    }
                }

                if (!match(")")) {
                    throw new ParseException("Expected closing ')'", tokens.getErrorIndex());
                }

                return new Ast.Expression.Function(java.util.Optional.empty(), name, arguments);
            }
            // Just a variable access
            else {
                return new Ast.Expression.Access(java.util.Optional.empty(), name);
            }
        }
        // Nothing matched - invalid primary expression
        else {
            throw new ParseException("Invalid primary expression", tokens.getErrorIndex());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.

     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.

     * Returns true if the current sequence of tokens matches the given patterns.
     * Patterns can be Token.Type (matches token type) or String (matches token literal).
     */

    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            }
            Token token = tokens.get(i);

            if (patterns[i] instanceof Token.Type) {
                // Pattern is a Token.Type check if types match
                if (token.getType() != patterns[i]) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                // Pattern is a String check if literals match
                if (!token.getLiteral().equals(patterns[i])) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern type: " + patterns[i].getClass());
            }
        }
        return true;
    }
    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.

     * Returns true if peek() succeeds and advances the token stream.
     * If peek() fails, the token stream is not modified.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

        /**
         * Gets the current index for error reporting.
         * Returns the index of the current token, or the end of the last token if no tokens remain.
         */
        public int getErrorIndex() {
            if (has(0)) {
                return get(0).getIndex();
            } else if (index > 0) {
                Token lastToken = tokens.get(index - 1);
                return lastToken.getIndex() + lastToken.getLiteral().length();
            } else {
                return 0;
            }
        }

    }

}


