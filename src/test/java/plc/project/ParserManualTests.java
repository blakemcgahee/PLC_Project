package plc.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Manual test cases to verify parser functionality beyond provided tests.
 * These test edge cases and error conditions.
 */
public class ParserManualTests {

    // ========== STATEMENT TESTS ==========

    @Test
    void testExpressionStatementVariable() {
        // Test: x;
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "x", 0),
                new Token(Token.Type.OPERATOR, ";", 1)
        );

        Parser parser = new Parser(tokens);
        Ast.Statement stmt = parser.parseStatement();

        Assertions.assertTrue(stmt instanceof Ast.Statement.Expression);
        Ast.Statement.Expression exprStmt = (Ast.Statement.Expression) stmt;
        Assertions.assertTrue(exprStmt.getExpression() instanceof Ast.Expression.Access);
    }

    @Test
    void testExpressionStatementFunction() {
        // Test: print();
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "print", 0),
                new Token(Token.Type.OPERATOR, "(", 5),
                new Token(Token.Type.OPERATOR, ")", 6),
                new Token(Token.Type.OPERATOR, ";", 7)
        );

        Parser parser = new Parser(tokens);
        Ast.Statement stmt = parser.parseStatement();

        Assertions.assertTrue(stmt instanceof Ast.Statement.Expression);
        Ast.Statement.Expression exprStmt = (Ast.Statement.Expression) stmt;
        Assertions.assertTrue(exprStmt.getExpression() instanceof Ast.Expression.Function);
    }

    @Test
    void testExpressionStatementMissingSemicolon() {
        // Test: print() [missing semicolon]
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "print", 0),
                new Token(Token.Type.OPERATOR, "(", 5),
                new Token(Token.Type.OPERATOR, ")", 6)
                // Missing semicolon!
        );

        Parser parser = new Parser(tokens);
        Assertions.assertThrows(ParseException.class, () -> {
            parser.parseStatement();
        });
    }

    @Test
    void testAssignmentStatementVariable() {
        // Test: x = 5;
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "x", 0),
                new Token(Token.Type.OPERATOR, "=", 2),
                new Token(Token.Type.INTEGER, "5", 4),
                new Token(Token.Type.OPERATOR, ";", 5)
        );

        Parser parser = new Parser(tokens);
        Ast.Statement stmt = parser.parseStatement();

        Assertions.assertTrue(stmt instanceof Ast.Statement.Assignment);
        Ast.Statement.Assignment assignStmt = (Ast.Statement.Assignment) stmt;
        Assertions.assertTrue(assignStmt.getReceiver() instanceof Ast.Expression.Access);
        Assertions.assertTrue(assignStmt.getValue() instanceof Ast.Expression.Literal);
    }

    @Test
    void testAssignmentMissingValue() {
        // Test: x = ; [missing value]
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "x", 0),
                new Token(Token.Type.OPERATOR, "=", 2),
                new Token(Token.Type.OPERATOR, ";", 4)
                // Missing value after =
        );

        Parser parser = new Parser(tokens);
        Assertions.assertThrows(ParseException.class, () -> {
            parser.parseStatement();
        });
    }

    // ========== EXPRESSION ERROR TESTS ==========

    @Test
    void testGroupMissingClosingParen() {
        // Test: (x [missing closing paren]
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.OPERATOR, "(", 0),
                new Token(Token.Type.IDENTIFIER, "x", 1)
                // Missing )
        );

        Parser parser = new Parser(tokens);
        Assertions.assertThrows(ParseException.class, () -> {
            parser.parseExpression();
        });
    }

    @Test
    void testBinaryMissingOperand() {
        // Test: x + [missing right operand]
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "x", 0),
                new Token(Token.Type.OPERATOR, "+", 2)
                // Missing right operand
        );

        Parser parser = new Parser(tokens);
        Assertions.assertThrows(ParseException.class, () -> {
            parser.parseExpression();
        });
    }

    @Test
    void testGroupValidExpression() {
        // Test: (x + y)
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.OPERATOR, "(", 0),
                new Token(Token.Type.IDENTIFIER, "x", 1),
                new Token(Token.Type.OPERATOR, "+", 3),
                new Token(Token.Type.IDENTIFIER, "y", 5),
                new Token(Token.Type.OPERATOR, ")", 6)
        );

        Parser parser = new Parser(tokens);
        Ast.Expression expr = parser.parseExpression();

        Assertions.assertTrue(expr instanceof Ast.Expression.Group);
        Ast.Expression.Group group = (Ast.Expression.Group) expr;
        Assertions.assertTrue(group.getExpression() instanceof Ast.Expression.Binary);
    }

    @Test
    void testBinaryValidExpression() {
        // Test: x * y
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "x", 0),
                new Token(Token.Type.OPERATOR, "*", 2),
                new Token(Token.Type.IDENTIFIER, "y", 4)
        );

        Parser parser = new Parser(tokens);
        Ast.Expression expr = parser.parseExpression();

        Assertions.assertTrue(expr instanceof Ast.Expression.Binary);
        Ast.Expression.Binary binary = (Ast.Expression.Binary) expr;
        Assertions.assertEquals("*", binary.getOperator());
    }

    // ========== BASELINE TESTS ==========

    @Test
    void testSimpleDeclaration() {
        // Test: LET x;
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "LET", 0),
                new Token(Token.Type.IDENTIFIER, "x", 4),
                new Token(Token.Type.OPERATOR, ";", 5)
        );

        Parser parser = new Parser(tokens);
        Ast.Statement stmt = parser.parseStatement();

        Assertions.assertTrue(stmt instanceof Ast.Statement.Declaration);
        Ast.Statement.Declaration decl = (Ast.Statement.Declaration) stmt;
        Assertions.assertEquals("x", decl.getName());
        Assertions.assertEquals(Optional.empty(), decl.getValue());
    }

    @Test
    void testDeclarationWithValue() {
        // Test: LET x = 10;
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "LET", 0),
                new Token(Token.Type.IDENTIFIER, "x", 4),
                new Token(Token.Type.OPERATOR, "=", 6),
                new Token(Token.Type.INTEGER, "10", 8),
                new Token(Token.Type.OPERATOR, ";", 10)
        );

        Parser parser = new Parser(tokens);
        Ast.Statement stmt = parser.parseStatement();

        Assertions.assertTrue(stmt instanceof Ast.Statement.Declaration);
        Ast.Statement.Declaration decl = (Ast.Statement.Declaration) stmt;
        Assertions.assertEquals("x", decl.getName());
        Assertions.assertTrue(decl.getValue().isPresent());
    }

    @Test
    void testWhileLoop() {
        // Test: WHILE TRUE DO RETURN 0; END
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "WHILE", 0),
                new Token(Token.Type.IDENTIFIER, "TRUE", 6),
                new Token(Token.Type.IDENTIFIER, "DO", 11),
                new Token(Token.Type.IDENTIFIER, "RETURN", 14),
                new Token(Token.Type.INTEGER, "0", 21),
                new Token(Token.Type.OPERATOR, ";", 22),
                new Token(Token.Type.IDENTIFIER, "END", 24)
        );

        Parser parser = new Parser(tokens);
        Ast.Statement stmt = parser.parseStatement();

        Assertions.assertTrue(stmt instanceof Ast.Statement.While);
        Ast.Statement.While whileStmt = (Ast.Statement.While) stmt;
        Assertions.assertEquals(1, whileStmt.getStatements().size());
    }

    // ========== ADDITIONAL EDGE CASES ==========

    @Test
    void testEmptyForInitialization() {
        // Test: FOR (; TRUE; ) END
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "FOR", 0),
                new Token(Token.Type.OPERATOR, "(", 4),
                new Token(Token.Type.OPERATOR, ";", 5),
                new Token(Token.Type.IDENTIFIER, "TRUE", 7),
                new Token(Token.Type.OPERATOR, ";", 11),
                new Token(Token.Type.OPERATOR, ")", 13),
                new Token(Token.Type.IDENTIFIER, "END", 15)
        );

        Parser parser = new Parser(tokens);
        Ast.Statement stmt = parser.parseStatement();

        Assertions.assertTrue(stmt instanceof Ast.Statement.For);
    }

    @Test
    void testFieldAccess() {
        // Test: obj.field
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "obj", 0),
                new Token(Token.Type.OPERATOR, ".", 3),
                new Token(Token.Type.IDENTIFIER, "field", 4)
        );

        Parser parser = new Parser(tokens);
        Ast.Expression expr = parser.parseExpression();

        Assertions.assertTrue(expr instanceof Ast.Expression.Access);
        Ast.Expression.Access access = (Ast.Expression.Access) expr;
        Assertions.assertEquals("field", access.getName());
        Assertions.assertTrue(access.getReceiver().isPresent());
    }

    @Test
    void testChainedComparison() {
        // Test: x < y == z
        List<Token> tokens = Arrays.asList(
                new Token(Token.Type.IDENTIFIER, "x", 0),
                new Token(Token.Type.OPERATOR, "<", 2),
                new Token(Token.Type.IDENTIFIER, "y", 4),
                new Token(Token.Type.OPERATOR, "==", 6),
                new Token(Token.Type.IDENTIFIER, "z", 9)
        );

        Parser parser = new Parser(tokens);
        Ast.Expression expr = parser.parseExpression();

        Assertions.assertTrue(expr instanceof Ast.Expression.Binary);
        // Should parse as: (x < y) == z
        Ast.Expression.Binary outer = (Ast.Expression.Binary) expr;
        Assertions.assertEquals("==", outer.getOperator());
    }
}