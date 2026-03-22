package plc.project;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Tests have been provided for a few selective parts of the AST, and are not
 * exhaustive. You should add additional tests for the remaining parts and make
 * sure to handle all of the cases defined in the specification which have not
 * been tested here.
 */
public final class AnalyzerTests {

    private static final Environment.Type OBJECT_TYPE = new Environment.Type("ObjectType", "ObjectType", init(new Scope(null), scope -> {
        scope.defineVariable("field", "field", Environment.Type.INTEGER, false, Environment.NIL);
        scope.defineFunction("method", "method", Arrays.asList(Environment.Type.ANY), Environment.Type.INTEGER, args -> Environment.NIL);
    }));

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testSource(String test, Ast.Source ast, Ast.Source expected) {
        Analyzer analyzer = test(ast, expected, new Scope(null));
        if (expected != null) {
            expected.getFields().forEach(field -> Assertions.assertEquals(field.getVariable(), analyzer.scope.lookupVariable(field.getName())));
            expected.getMethods().forEach(method -> Assertions.assertEquals(method.getFunction(), analyzer.scope.lookupFunction(method.getName(), method.getParameters().size())));
        }
    }
    private static Stream<Arguments> testSource() {
        return Stream.of(
                // LET value: Boolean = TRUE; DEF main(): Integer DO RETURN value; END
                Arguments.of("Invalid Return",
                        new Ast.Source(
                                Arrays.asList(new Ast.Field("value", "Boolean", false, Optional.of(new Ast.Expression.Literal(true)))),
                                Arrays.asList(new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                        new Ast.Statement.Return(new Ast.Expression.Access(Optional.empty(), "value"))
                                )))
                        ),
                        null
                ),
                // DEF main() DO RETURN 0; END
                Arguments.of("Missing Integer Return Type for Main",
                        new Ast.Source(
                                Arrays.asList(),
                                Arrays.asList(new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.empty(), Arrays.asList(
                                        new Ast.Statement.Return(new Ast.Expression.Literal(new BigInteger("0")))
                                )))
                        ),
                        null
                ),
                // DEF main(): Integer DO RETURN 0; END
                Arguments.of("Valid Main No Fields",
                        new Ast.Source(
                                Arrays.asList(),
                                Arrays.asList(new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                        new Ast.Statement.Return(new Ast.Expression.Literal(BigInteger.ZERO))
                                )))
                        ),
                        new Ast.Source(
                                Arrays.asList(),
                                Arrays.asList(init(new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                        new Ast.Statement.Return(init(new Ast.Expression.Literal(BigInteger.ZERO), ast -> ast.setType(Environment.Type.INTEGER)))
                                )), ast -> ast.setFunction(new Environment.Function("main", "main", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL))))
                        )
                ),
                // no main method defined
                Arguments.of("Missing Main",
                        new Ast.Source(Arrays.asList(), Arrays.asList()),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testField(String test, Ast.Field ast, Ast.Field expected) {
        Analyzer analyzer = test(ast, expected, new Scope(null));
        if (expected != null) {
            Assertions.assertEquals(expected.getVariable(), analyzer.scope.lookupVariable(expected.getName()));
        }
    }
    private static Stream<Arguments> testField() {
        return Stream.of(
                // LET name: Integer;
                Arguments.of("Declaration",
                        new Ast.Field("name", "Integer", false, Optional.empty()),
                        init(new Ast.Field("name", "Integer", false, Optional.empty()),
                                ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.INTEGER, false, Environment.NIL)))
                ),
                // LET name: Integer = 1;
                Arguments.of("Initialization",
                        new Ast.Field("name", "Integer", false, Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                        init(new Ast.Field("name", "Integer", false, Optional.of(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        )), ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.INTEGER, false, Environment.NIL)))
                ),
                // LET name: Decimal = 1;  (Integer value assigned to Decimal field)
                Arguments.of("Type Mismatch",
                        new Ast.Field("name", "Decimal", false, Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                        null
                ),
                // LET name: Unknown;
                Arguments.of("Unknown Type",
                        new Ast.Field("name", "Unknown", false, Optional.empty()),
                        null
                ),
                // LET CONST name: Integer;  (constant with no value)
                Arguments.of("Constant Without Value",
                        new Ast.Field("name", "Integer", true, Optional.empty()),
                        null
                ),
                // LET CONST name: Integer = 1;
                Arguments.of("Constant With Value",
                        new Ast.Field("name", "Integer", true, Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                        init(new Ast.Field("name", "Integer", true, Optional.of(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        )), ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.INTEGER, true, Environment.NIL)))
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testMethod(String test, Ast.Method ast, Ast.Method expected) {
        Analyzer analyzer = test(ast, expected, new Scope(null));
        if (expected != null) {
            Assertions.assertEquals(expected.getFunction(), analyzer.scope.lookupFunction(expected.getName(), expected.getParameters().size()));
        }
    }
    private static Stream<Arguments> testMethod() {
        return Stream.of(
                // DEF main(): Integer DO print("Hello, World!"); END
                Arguments.of("Hello World",
                        new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal("Hello, World!")
                                )))
                        )),
                        init(new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                new Ast.Statement.Expression(init(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        init(new Ast.Expression.Literal("Hello, World!"), ast -> ast.setType(Environment.Type.STRING))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))))
                        )), ast -> ast.setFunction(new Environment.Function("main", "main", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL)))
                ),
                // DEF increment(num: Integer): Decimal DO RETURN num + 1; END
                Arguments.of("Return Type Mismatch",
                        new Ast.Method("increment", Arrays.asList("num"), Arrays.asList("Integer"), Optional.of("Decimal"), Arrays.asList(
                                new Ast.Statement.Return(new Ast.Expression.Binary("+",
                                        new Ast.Expression.Access(Optional.empty(), "num"),
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                ))
                        )),
                        null
                ),
                // DEF add(x: Integer, y: Integer): Integer DO RETURN x + y; END
                Arguments.of("Multiple Parameters",
                        new Ast.Method("add", Arrays.asList("x", "y"), Arrays.asList("Integer", "Integer"), Optional.of("Integer"), Arrays.asList(
                                new Ast.Statement.Return(new Ast.Expression.Binary("+",
                                        new Ast.Expression.Access(Optional.empty(), "x"),
                                        new Ast.Expression.Access(Optional.empty(), "y")
                                ))
                        )),
                        init(new Ast.Method("add", Arrays.asList("x", "y"), Arrays.asList("Integer", "Integer"), Optional.of("Integer"), Arrays.asList(
                                new Ast.Statement.Return(init(new Ast.Expression.Binary("+",
                                        init(new Ast.Expression.Access(Optional.empty(), "x"), ast -> ast.setVariable(new Environment.Variable("x", "x", Environment.Type.INTEGER, false, Environment.NIL))),
                                        init(new Ast.Expression.Access(Optional.empty(), "y"), ast -> ast.setVariable(new Environment.Variable("y", "y", Environment.Type.INTEGER, false, Environment.NIL)))
                                ), ast -> ast.setType(Environment.Type.INTEGER)))
                        )), ast -> ast.setFunction(new Environment.Function("add", "add", Arrays.asList(Environment.Type.INTEGER, Environment.Type.INTEGER), Environment.Type.INTEGER, args -> Environment.NIL)))
                ),
                // DEF nilReturn() DO print(1); END  (no return type annotation = Nil)
                Arguments.of("Nil Return Type",
                        new Ast.Method("nilReturn", Arrays.asList(), Arrays.asList(), Optional.empty(), Arrays.asList(
                                new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                )))
                        )),
                        init(new Ast.Method("nilReturn", Arrays.asList(), Arrays.asList(), Optional.empty(), Arrays.asList(
                                new Ast.Statement.Expression(init(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))))
                        )), ast -> ast.setFunction(new Environment.Function("nilReturn", "nilReturn", Arrays.asList(), Environment.Type.NIL, args -> Environment.NIL)))
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testDeclarationStatement(String test, Ast.Statement.Declaration ast, Ast.Statement.Declaration expected) {
        Analyzer analyzer = test(ast, expected, new Scope(null));
        if (expected != null) {
            Assertions.assertEquals(expected.getVariable(), analyzer.scope.lookupVariable(expected.getName()));
        }
    }
    private static Stream<Arguments> testDeclarationStatement() {
        return Stream.of(
                // LET name: Integer;
                Arguments.of("Declaration",
                        new Ast.Statement.Declaration("name", Optional.of("Integer"), Optional.empty()),
                        init(new Ast.Statement.Declaration("name", Optional.of("Integer"), Optional.empty()), ast -> {
                            ast.setVariable(new Environment.Variable("name", "name", Environment.Type.INTEGER, false, Environment.NIL));
                        })
                ),
                // LET name = 1;
                Arguments.of("Initialization",
                        new Ast.Statement.Declaration("name", Optional.empty(), Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                        init(new Ast.Statement.Declaration("name", Optional.empty(), Optional.of(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        )), ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.INTEGER, false, Environment.NIL)))
                ),
                // LET name;
                Arguments.of("Missing Type",
                        new Ast.Statement.Declaration("name", Optional.empty(), Optional.empty()),
                        null
                ),
                // LET name: Unknown;
                Arguments.of("Unknown Type",
                        new Ast.Statement.Declaration("name", Optional.of("Unknown"), Optional.empty()),
                        null
                ),
                // LET name: Integer = 1;  (annotation and value both present, matching)
                Arguments.of("Annotation And Value Match",
                        new Ast.Statement.Declaration("name", Optional.of("Integer"), Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                        init(new Ast.Statement.Declaration("name", Optional.of("Integer"), Optional.of(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        )), ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.INTEGER, false, Environment.NIL)))
                ),
                // LET name: Boolean = 1;  (annotation and value mismatch)
                Arguments.of("Annotation And Value Mismatch",
                        new Ast.Statement.Declaration("name", Optional.of("Boolean"), Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testAssignmentStatement(String test, Ast.Statement.Assignment ast, Ast.Statement.Assignment expected) {
        test(ast, expected, init(new Scope(null), scope -> {
            scope.defineVariable("variable", "variable", Environment.Type.INTEGER, false, Environment.NIL);
            scope.defineVariable("constant", "constant", Environment.Type.INTEGER, true, Environment.NIL);
            scope.defineVariable("object", "object", OBJECT_TYPE, false, Environment.NIL);
        }));
    }
    private static Stream<Arguments> testAssignmentStatement() {
        return Stream.of(
                // variable = 1;
                Arguments.of("Variable",
                        new Ast.Statement.Assignment(
                                new Ast.Expression.Access(Optional.empty(), "variable"),
                                new Ast.Expression.Literal(BigInteger.ONE)
                        ),
                        new Ast.Statement.Assignment(
                                init(new Ast.Expression.Access(Optional.empty(), "variable"), ast -> ast.setVariable(new Environment.Variable("variable", "variable", Environment.Type.INTEGER, false, Environment.NIL))),
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        )
                ),
                // variable = "string";
                Arguments.of("Invalid Type",
                        new Ast.Statement.Assignment(
                                new Ast.Expression.Access(Optional.empty(), "variable"),
                                new Ast.Expression.Literal("string")
                        ),
                        null
                ),
                // object.field = 1;
                Arguments.of("Field",
                        new Ast.Statement.Assignment(
                                new Ast.Expression.Access(Optional.of(new Ast.Expression.Access(Optional.empty(), "object")), "field"),
                                new Ast.Expression.Literal(BigInteger.ONE)
                        ),
                        new Ast.Statement.Assignment(
                                init(new Ast.Expression.Access(Optional.of(
                                        init(new Ast.Expression.Access(Optional.empty(), "object"), ast -> ast.setVariable(new Environment.Variable("object", "object", OBJECT_TYPE, false, Environment.NIL)))
                                ), "field"), ast -> ast.setVariable(new Environment.Variable("field", "field", Environment.Type.INTEGER, false, Environment.NIL))),
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        )
                ),
                // constant = 1;  (reassignment to a constant)
                Arguments.of("Constant Reassignment",
                        new Ast.Statement.Assignment(
                                new Ast.Expression.Access(Optional.empty(), "constant"),
                                new Ast.Expression.Literal(BigInteger.ONE)
                        ),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testIfStatement(String test, Ast.Statement.If ast, Ast.Statement.If expected) {
        test(ast, expected, new Scope(null));
    }
    private static Stream<Arguments> testIfStatement() {
        return Stream.of(
                // IF TRUE DO print(1); END
                Arguments.of("Valid Condition",
                        new Ast.Statement.If(
                                new Ast.Expression.Literal(Boolean.TRUE),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                )))),
                                Arrays.asList()
                        ),
                        new Ast.Statement.If(
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))))),
                                Arrays.asList()
                        )
                ),
                // IF "FALSE" DO print(1); END
                Arguments.of("Invalid Condition",
                        new Ast.Statement.If(
                                new Ast.Expression.Literal("FALSE"),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                )))),
                                Arrays.asList()
                        ),
                        null
                ),
                // IF TRUE DO print(9223372036854775807); END
                Arguments.of("Invalid Statement",
                        new Ast.Statement.If(
                                new Ast.Expression.Literal(Boolean.TRUE),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.valueOf(Long.MAX_VALUE))
                                )))),
                                Arrays.asList()
                        ),
                        null
                ),
                // IF TRUE DO END
                Arguments.of("Empty Statements",
                        new Ast.Statement.If(
                                new Ast.Expression.Literal(Boolean.TRUE),
                                Arrays.asList(),
                                Arrays.asList()
                        ),
                        null
                ),
                // IF TRUE DO print(1); ELSE print(2); END
                Arguments.of("Valid Else Block",
                        new Ast.Statement.If(
                                new Ast.Expression.Literal(Boolean.TRUE),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                )))),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.TWO)
                                ))))
                        ),
                        new Ast.Statement.If(
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))))),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        init(new Ast.Expression.Literal(BigInteger.TWO), ast -> ast.setType(Environment.Type.INTEGER))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL)))))
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testForStatement(String test, Ast.Statement.For ast, Ast.Statement.For expected) {
        test(ast, expected, init(new Scope(null), scope -> {
            scope.defineVariable("num", "num", Environment.Type.INTEGER, false, Environment.NIL);
        }));
    }
    private static Stream<Arguments> testForStatement() {
        return Stream.of(
                // FOR (num = 0; TRUE; num = 1) DO print(num); END
                Arguments.of("Valid For",
                        new Ast.Statement.For(
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "num"),
                                        new Ast.Expression.Literal(BigInteger.ZERO)
                                ),
                                new Ast.Expression.Literal(Boolean.TRUE),
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "num"),
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                ),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Access(Optional.empty(), "num")
                                ))))
                        ),
                        new Ast.Statement.For(
                                new Ast.Statement.Assignment(
                                        init(new Ast.Expression.Access(Optional.empty(), "num"), ast -> ast.setVariable(new Environment.Variable("num", "num", Environment.Type.INTEGER, false, Environment.NIL))),
                                        init(new Ast.Expression.Literal(BigInteger.ZERO), ast -> ast.setType(Environment.Type.INTEGER))
                                ),
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                new Ast.Statement.Assignment(
                                        init(new Ast.Expression.Access(Optional.empty(), "num"), ast -> ast.setVariable(new Environment.Variable("num", "num", Environment.Type.INTEGER, false, Environment.NIL))),
                                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                                ),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        init(new Ast.Expression.Access(Optional.empty(), "num"), ast -> ast.setVariable(new Environment.Variable("num", "num", Environment.Type.INTEGER, false, Environment.NIL)))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL)))))
                        )
                ),
                // FOR (num = 0; TRUE; num = 1) DO END  (empty body)
                Arguments.of("Empty Body",
                        new Ast.Statement.For(
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "num"),
                                        new Ast.Expression.Literal(BigInteger.ZERO)
                                ),
                                new Ast.Expression.Literal(Boolean.TRUE),
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "num"),
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                ),
                                Arrays.asList()
                        ),
                        null
                ),
                // FOR (num = 0; 1; num = 1) DO print(num); END  (non-Boolean condition)
                Arguments.of("Invalid Condition",
                        new Ast.Statement.For(
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "num"),
                                        new Ast.Expression.Literal(BigInteger.ZERO)
                                ),
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "num"),
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                ),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Access(Optional.empty(), "num")
                                ))))
                        ),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testWhileStatement(String test, Ast.Statement.While ast, Ast.Statement.While expected) {
        test(ast, expected, new Scope(null));
    }
    private static Stream<Arguments> testWhileStatement() {
        return Stream.of(
                // WHILE TRUE DO END  (empty body is valid for while per spec)
                Arguments.of("Valid Condition Empty Body",
                        new Ast.Statement.While(
                                new Ast.Expression.Literal(Boolean.TRUE),
                                Arrays.asList()
                        ),
                        new Ast.Statement.While(
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                Arrays.asList()
                        )
                ),
                // WHILE TRUE DO print(1); END
                Arguments.of("Valid Condition With Body",
                        new Ast.Statement.While(
                                new Ast.Expression.Literal(Boolean.TRUE),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                ))))
                        ),
                        new Ast.Statement.While(
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL)))))
                        )
                ),
                // WHILE 1 DO print(1); END  (non-Boolean condition)
                Arguments.of("Invalid Condition",
                        new Ast.Statement.While(
                                new Ast.Expression.Literal(BigInteger.ONE),
                                Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function(Optional.empty(), "print", Arrays.asList(
                                        new Ast.Expression.Literal(BigInteger.ONE)
                                ))))
                        ),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testReturnStatement(String test, Ast.Method ast, Ast.Method expected) {
        // return statements must be tested inside a method to provide the return type context
        test(ast, expected, new Scope(null));
    }
    private static Stream<Arguments> testReturnStatement() {
        return Stream.of(
                // DEF main(): Integer DO RETURN 1; END
                Arguments.of("Valid Return",
                        new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                new Ast.Statement.Return(new Ast.Expression.Literal(BigInteger.ONE))
                        )),
                        init(new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                new Ast.Statement.Return(init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)))
                        )), ast -> ast.setFunction(new Environment.Function("main", "main", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL)))
                ),
                // DEF main(): String DO RETURN 1; END  (Integer returned from String method)
                Arguments.of("Invalid Return Type",
                        new Ast.Method("main", Arrays.asList(), Arrays.asList(), Optional.of("String"), Arrays.asList(
                                new Ast.Statement.Return(new Ast.Expression.Literal(BigInteger.ONE))
                        )),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testLiteralExpression(String test, Ast.Expression.Literal ast, Ast.Expression.Literal expected) {
        test(ast, expected, new Scope(null));
    }
    private static Stream<Arguments> testLiteralExpression() {
        return Stream.of(
                // NIL
                Arguments.of("Nil",
                        new Ast.Expression.Literal(null),
                        init(new Ast.Expression.Literal(null), ast -> ast.setType(Environment.Type.NIL))
                ),
                // TRUE
                Arguments.of("Boolean",
                        new Ast.Expression.Literal(true),
                        init(new Ast.Expression.Literal(true), ast -> ast.setType(Environment.Type.BOOLEAN))
                ),
                // 'a'
                Arguments.of("Character",
                        new Ast.Expression.Literal('a'),
                        init(new Ast.Expression.Literal('a'), ast -> ast.setType(Environment.Type.CHARACTER))
                ),
                // "string"
                Arguments.of("String",
                        new Ast.Expression.Literal("string"),
                        init(new Ast.Expression.Literal("string"), ast -> ast.setType(Environment.Type.STRING))
                ),
                // 2147483647  (Integer.MAX_VALUE, valid)
                Arguments.of("Integer Valid",
                        new Ast.Expression.Literal(BigInteger.valueOf(Integer.MAX_VALUE)),
                        init(new Ast.Expression.Literal(BigInteger.valueOf(Integer.MAX_VALUE)), ast -> ast.setType(Environment.Type.INTEGER))
                ),
                // 9223372036854775807  (Long.MAX_VALUE, out of int range)
                Arguments.of("Integer Invalid",
                        new Ast.Expression.Literal(BigInteger.valueOf(Long.MAX_VALUE)),
                        null
                ),
                // 1.0  (valid decimal)
                Arguments.of("Decimal Valid",
                        new Ast.Expression.Literal(new BigDecimal("1.0")),
                        init(new Ast.Expression.Literal(new BigDecimal("1.0")), ast -> ast.setType(Environment.Type.DECIMAL))
                ),
                // 1e999999999  (out of double range)
                Arguments.of("Decimal Invalid",
                        new Ast.Expression.Literal(new BigDecimal("1e999999999")),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testGroupExpression(String test, Ast.Expression.Group ast, Ast.Expression.Group expected) {
        test(ast, expected, new Scope(null));
    }
    private static Stream<Arguments> testGroupExpression() {
        return Stream.of(
                // (1 + 10)
                Arguments.of("Valid Group",
                        new Ast.Expression.Group(new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        )),
                        init(new Ast.Expression.Group(init(new Ast.Expression.Binary("+",
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER))), ast -> ast.setType(Environment.Type.INTEGER))
                ),
                // (1)  (literal inside group, not a binary expression)
                Arguments.of("Non-Binary Inner Expression",
                        new Ast.Expression.Group(new Ast.Expression.Literal(BigInteger.ONE)),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testBinaryExpression(String test, Ast.Expression.Binary ast, Ast.Expression.Binary expected) {
        test(ast, expected, new Scope(null));
    }
    private static Stream<Arguments> testBinaryExpression() {
        return Stream.of(
                // TRUE && FALSE
                Arguments.of("Logical AND Valid",
                        new Ast.Expression.Binary("&&",
                                new Ast.Expression.Literal(Boolean.TRUE),
                                new Ast.Expression.Literal(Boolean.FALSE)
                        ),
                        init(new Ast.Expression.Binary("&&",
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                init(new Ast.Expression.Literal(Boolean.FALSE), ast -> ast.setType(Environment.Type.BOOLEAN))
                        ), ast -> ast.setType(Environment.Type.BOOLEAN))
                ),
                // TRUE && "FALSE"
                Arguments.of("Logical AND Invalid",
                        new Ast.Expression.Binary("&&",
                                new Ast.Expression.Literal(Boolean.TRUE),
                                new Ast.Expression.Literal("FALSE")
                        ),
                        null
                ),
                // TRUE || FALSE
                Arguments.of("Logical OR Valid",
                        new Ast.Expression.Binary("||",
                                new Ast.Expression.Literal(Boolean.TRUE),
                                new Ast.Expression.Literal(Boolean.FALSE)
                        ),
                        init(new Ast.Expression.Binary("||",
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                init(new Ast.Expression.Literal(Boolean.FALSE), ast -> ast.setType(Environment.Type.BOOLEAN))
                        ), ast -> ast.setType(Environment.Type.BOOLEAN))
                ),
                // 1 < 2
                Arguments.of("Comparison Less Than Valid",
                        new Ast.Expression.Binary("<",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TWO)
                        ),
                        init(new Ast.Expression.Binary("<",
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.TWO), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.BOOLEAN))
                ),
                // 1 < 1.0  (mixed Comparable types)
                Arguments.of("Comparison Mixed Types",
                        new Ast.Expression.Binary("<",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigDecimal.ONE)
                        ),
                        null
                ),
                // 1 == 1
                Arguments.of("Equality Valid",
                        new Ast.Expression.Binary("==",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.ONE)
                        ),
                        init(new Ast.Expression.Binary("==",
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.BOOLEAN))
                ),
                // "Ben" + 10
                Arguments.of("String Concatenation",
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal("Ben"),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        ),
                        init(new Ast.Expression.Binary("+",
                                init(new Ast.Expression.Literal("Ben"), ast -> ast.setType(Environment.Type.STRING)),
                                init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.STRING))
                ),
                // 1 + 10
                Arguments.of("Integer Addition",
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        ),
                        init(new Ast.Expression.Binary("+",
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER))
                ),
                // 1 + 1.0  (mixed numeric types)
                Arguments.of("Integer Decimal Addition",
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigDecimal.ONE)
                        ),
                        null
                ),
                // 1.0 + 2.0
                Arguments.of("Decimal Addition",
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal(new BigDecimal("1.0")),
                                new Ast.Expression.Literal(new BigDecimal("2.0"))
                        ),
                        init(new Ast.Expression.Binary("+",
                                init(new Ast.Expression.Literal(new BigDecimal("1.0")), ast -> ast.setType(Environment.Type.DECIMAL)),
                                init(new Ast.Expression.Literal(new BigDecimal("2.0")), ast -> ast.setType(Environment.Type.DECIMAL))
                        ), ast -> ast.setType(Environment.Type.DECIMAL))
                ),
                // 10 - 1
                Arguments.of("Integer Subtraction",
                        new Ast.Expression.Binary("-",
                                new Ast.Expression.Literal(BigInteger.TEN),
                                new Ast.Expression.Literal(BigInteger.ONE)
                        ),
                        init(new Ast.Expression.Binary("-",
                                init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER))
                ),
                // 2 * 3
                Arguments.of("Integer Multiplication",
                        new Ast.Expression.Binary("*",
                                new Ast.Expression.Literal(BigInteger.TWO),
                                new Ast.Expression.Literal(new BigInteger("3"))
                        ),
                        init(new Ast.Expression.Binary("*",
                                init(new Ast.Expression.Literal(BigInteger.TWO), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(new BigInteger("3")), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER))
                ),
                // 10 / 2
                Arguments.of("Integer Division",
                        new Ast.Expression.Binary("/",
                                new Ast.Expression.Literal(BigInteger.TEN),
                                new Ast.Expression.Literal(BigInteger.TWO)
                        ),
                        init(new Ast.Expression.Binary("/",
                                init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.TWO), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER))
                ),
                // TRUE - FALSE  (non-numeric arithmetic)
                Arguments.of("Arithmetic Invalid Type",
                        new Ast.Expression.Binary("-",
                                new Ast.Expression.Literal(Boolean.TRUE),
                                new Ast.Expression.Literal(Boolean.FALSE)
                        ),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testAccessExpression(String test, Ast.Expression.Access ast, Ast.Expression.Access expected) {
        test(ast, expected, init(new Scope(null), scope -> {
            scope.defineVariable("variable", "variable", Environment.Type.INTEGER, false, Environment.NIL);
            scope.defineVariable("object", "object", OBJECT_TYPE, false, Environment.NIL);
        }));
    }
    private static Stream<Arguments> testAccessExpression() {
        return Stream.of(
                // variable
                Arguments.of("Variable",
                        new Ast.Expression.Access(Optional.empty(), "variable"),
                        init(new Ast.Expression.Access(Optional.empty(), "variable"), ast -> ast.setVariable(new Environment.Variable("variable", "variable", Environment.Type.INTEGER, false, Environment.NIL)))
                ),
                // object.field
                Arguments.of("Field",
                        new Ast.Expression.Access(Optional.of(new Ast.Expression.Access(Optional.empty(), "object")), "field"),
                        init(new Ast.Expression.Access(Optional.of(
                                init(new Ast.Expression.Access(Optional.empty(), "object"), ast -> ast.setVariable(new Environment.Variable("object", "object", OBJECT_TYPE, false, Environment.NIL)))
                        ), "field"), ast -> ast.setVariable(new Environment.Variable("field", "field", Environment.Type.INTEGER, false, Environment.NIL)))
                ),
                // undefined  (variable not in scope)
                Arguments.of("Undefined Variable",
                        new Ast.Expression.Access(Optional.empty(), "undefined"),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testFunctionExpression(String test, Ast.Expression.Function ast, Ast.Expression.Function expected) {
        test(ast, expected, init(new Scope(null), scope -> {
            scope.defineFunction("function", "function", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL);
            scope.defineFunction("typed", "typed", Arrays.asList(Environment.Type.INTEGER), Environment.Type.INTEGER, args -> Environment.NIL);
            scope.defineVariable("object", "object", OBJECT_TYPE, false, Environment.NIL);
        }));
    }
    private static Stream<Arguments> testFunctionExpression() {
        return Stream.of(
                // function()
                Arguments.of("Function",
                        new Ast.Expression.Function(Optional.empty(), "function", Arrays.asList()),
                        init(new Ast.Expression.Function(Optional.empty(), "function", Arrays.asList()), ast -> ast.setFunction(new Environment.Function("function", "function", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL)))
                ),
                // object.method()
                Arguments.of("Method",
                        new Ast.Expression.Function(Optional.of(new Ast.Expression.Access(Optional.empty(), "object")), "method", Arrays.asList()),
                        init(new Ast.Expression.Function(Optional.of(
                                init(new Ast.Expression.Access(Optional.empty(), "object"), ast -> ast.setVariable(new Environment.Variable("object", "object", OBJECT_TYPE, false, Environment.NIL)))
                        ), "method", Arrays.asList()), ast -> ast.setFunction(new Environment.Function("method", "method", Arrays.asList(Environment.Type.ANY), Environment.Type.INTEGER, args -> Environment.NIL)))
                ),
                // typed(1)  (valid argument type)
                Arguments.of("Valid Argument Type",
                        new Ast.Expression.Function(Optional.empty(), "typed", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE))),
                        init(new Ast.Expression.Function(Optional.empty(), "typed", Arrays.asList(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        )), ast -> ast.setFunction(new Environment.Function("typed", "typed", Arrays.asList(Environment.Type.INTEGER), Environment.Type.INTEGER, args -> Environment.NIL)))
                ),
                // typed("string")  (wrong argument type)
                Arguments.of("Invalid Argument Type",
                        new Ast.Expression.Function(Optional.empty(), "typed", Arrays.asList(new Ast.Expression.Literal("string"))),
                        null
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void testRequireAssignable(String test, Environment.Type target, Environment.Type type, boolean success) {
        if (success) {
            Assertions.assertDoesNotThrow(() -> Analyzer.requireAssignable(target, type));
        } else {
            Assertions.assertThrows(RuntimeException.class, () -> Analyzer.requireAssignable(target, type));
        }
    }
    private static Stream<Arguments> testRequireAssignable() {
        return Stream.of(
                Arguments.of("Integer to Integer", Environment.Type.INTEGER, Environment.Type.INTEGER, true),
                Arguments.of("Integer to Decimal", Environment.Type.DECIMAL, Environment.Type.INTEGER, false),
                Arguments.of("Integer to Comparable", Environment.Type.COMPARABLE, Environment.Type.INTEGER, true),
                Arguments.of("Integer to Any", Environment.Type.ANY, Environment.Type.INTEGER, true),
                Arguments.of("Any to Integer", Environment.Type.INTEGER, Environment.Type.ANY, false),
                Arguments.of("Decimal to Comparable", Environment.Type.COMPARABLE, Environment.Type.DECIMAL, true),
                Arguments.of("Character to Comparable", Environment.Type.COMPARABLE, Environment.Type.CHARACTER, true),
                Arguments.of("String to Comparable", Environment.Type.COMPARABLE, Environment.Type.STRING, true),
                Arguments.of("Boolean to Comparable", Environment.Type.COMPARABLE, Environment.Type.BOOLEAN, false),
                Arguments.of("Boolean to Any", Environment.Type.ANY, Environment.Type.BOOLEAN, true),
                Arguments.of("Nil to Any", Environment.Type.ANY, Environment.Type.NIL, true),
                Arguments.of("String to Integer", Environment.Type.INTEGER, Environment.Type.STRING, false)
        );
    }

    /**
     * Helper function for tests. If {@param expected} is {@code null}, analysis
     * is expected to throw a {@link RuntimeException}.
     */
    private static <T extends Ast> Analyzer test(T ast, T expected, Scope scope) {
        Analyzer analyzer = new Analyzer(scope);
        if (expected != null) {
            analyzer.visit(ast);
            Assertions.assertEquals(expected, ast);
        } else {
            Assertions.assertThrows(RuntimeException.class, () -> analyzer.visit(ast));
        }
        return analyzer;
    }

    /**
     * Runs a callback on the given value, used for inline initialization.
     */
    private static <T> T init(T value, Consumer<T> initializer) {
        initializer.accept(value);
        return value;
    }

}