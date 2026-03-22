package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The analyzer walks the AST produced by the parser and decorates it with
 * type and variable/function attribute information needed for code generation.
 *
 * Each visit method corresponds to one AST node type and either annotates the
 * node in place or throws a {@link RuntimeException} when a semantic constraint
 * is violated.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    /**
     * Visits all fields then all methods, and verifies that a valid main/0 exists.
     */
    @Override
    public Void visit(Ast.Source ast) {
        // source ::= field* method*
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }

        // main/0 must exist and must return Integer
        Environment.Function main = scope.lookupFunction("main", 0);
        if (!main.getReturnType().equals(Environment.Type.INTEGER)) {
            throw new RuntimeException("Expected main/0 to have return type Integer, got: " + main.getReturnType().getName());
        }

        return null;
    }

    /**
     * Defines a field variable in the current scope after validating its type and value.
     */
    @Override
    public Void visit(Ast.Field ast) {
        // field ::= 'LET' 'CONST'? identifier ':' identifier ('=' expression)? ';'
        // resolve the declared type from the environment (throws if unknown)
        Environment.Type type = Environment.getType(ast.getTypeName());

        // visit the initializer before defining the variable to prevent use-before-init
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(type, ast.getValue().get().getType());
        }

        // constant fields must have an initial value
        if (ast.getConstant() && ast.getValue().isEmpty()) {
            throw new RuntimeException("Constant field '" + ast.getName() + "' must have an initial value.");
        }

        // define in scope and annotate the AST node
        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type, ast.getConstant(), Environment.NIL);
        ast.setVariable(variable);

        return null;
    }

    /**
     * Defines a function in the current scope and visits its body in a new child scope.
     */
    @Override
    public Void visit(Ast.Method ast) {
        // method ::= 'DEF' identifier '(' (identifier ':' identifier (',' identifier ':' identifier)*)? ')' (':' identifier)? 'DO' statement* 'END'
        // resolve all parameter types
        List<Environment.Type> paramTypes = ast.getParameterTypeNames().stream()
                .map(Environment::getType)
                .collect(Collectors.toList());

        // return type defaults to Nil when not specified
        Environment.Type returnType = ast.getReturnTypeName()
                .map(Environment::getType)
                .orElse(Environment.Type.NIL);

        // define the function before visiting the body to allow recursive calls
        Environment.Function function = scope.defineFunction(ast.getName(), ast.getName(), paramTypes, returnType, args -> Environment.NIL);
        ast.setFunction(function);

        // save outer method context for nested return type checking
        Ast.Method outer = this.method;
        this.method = ast;

        // open a new scope containing each parameter as a variable
        try {
            scope = new Scope(scope);
            List<String> params = ast.getParameters();
            for (int i = 0; i < params.size(); i++) {
                scope.defineVariable(params.get(i), params.get(i), paramTypes.get(i), false, Environment.NIL);
            }
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
            this.method = outer;
        }

        return null;
    }

    /**
     * Validates that the expression statement is a function call.
     */
    @Override
    public Void visit(Ast.Statement.Expression ast) {
        // only function calls are valid as standalone statements
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Expression statement must be a function call.");
        }
        visit(ast.getExpression());
        return null;
    }

    /**
     * Declares a local variable, resolving its type from the annotation or the initializer.
     */
    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        // statement ::= 'LET' identifier (':' identifier)? ('=' expression)? ';'
        // visit the initializer first so its type is available for inference
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }

        Environment.Type type;
        if (ast.getTypeName().isPresent()) {
            // explicit annotation takes priority; throws on unknown type name
            type = Environment.getType(ast.getTypeName().get());
            if (ast.getValue().isPresent()) {
                requireAssignable(type, ast.getValue().get().getType());
            }
        } else if (ast.getValue().isPresent()) {
            // infer type from the initializer when no annotation is given
            type = ast.getValue().get().getType();
        } else {
            throw new RuntimeException("Declaration of '" + ast.getName() + "' requires a type annotation or initial value.");
        }

        // define in scope and annotate the AST node
        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type, false, Environment.NIL);
        ast.setVariable(variable);

        return null;
    }

    /**
     * Validates an assignment, ensuring the receiver is an access expression and types are compatible.
     */
    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        // receiver must be an access expression (the only assignable l-value)
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Assignment receiver must be an Access expression.");
        }

        // visit both sides to resolve types
        visit(ast.getReceiver());
        visit(ast.getValue());

        // constants cannot be reassigned after declaration
        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
        if (access.getVariable().getConstant()) {
            throw new RuntimeException("Cannot assign to constant '" + access.getName() + "'.");
        }

        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());

        return null;
    }

    /**
     * Validates an if statement, requiring a Boolean condition and a non-empty then block.
     */
    @Override
    public Void visit(Ast.Statement.If ast) {
        // condition must be Boolean
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

        // then block must contain at least one statement
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("IF statement must have at least one statement in its then block.");
        }

        // visit then-statements in an isolated scope
        try {
            scope = new Scope(scope);
            for (Ast.Statement stmt : ast.getThenStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }

        // visit else-statements in a separate isolated scope
        try {
            scope = new Scope(scope);
            for (Ast.Statement stmt : ast.getElseStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }

        return null;
    }

    /**
     * Validates a for loop, checking the initializer type, Boolean condition, and non-empty body.
     */
    @Override
    public Void visit(Ast.Statement.For ast) {
        // body must contain at least one statement
        if (ast.getStatements().isEmpty()) {
            throw new RuntimeException("FOR loop body must not be empty.");
        }

        // validate the initialization clause and capture the iterator variable type
        Environment.Type initType = null;
        if (ast.getInitialization() != null) {
            visit(ast.getInitialization());
            if (ast.getInitialization() instanceof Ast.Statement.Assignment) {
                initType = ((Ast.Statement.Assignment) ast.getInitialization()).getReceiver().getType();
                // iterator variable must be a Comparable type
                requireAssignable(Environment.Type.COMPARABLE, initType);
            }
        }

        // condition must be Boolean
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

        // increment expression type must match the iterator variable type
        if (ast.getIncrement() != null) {
            visit(ast.getIncrement());
            if (initType != null && ast.getIncrement() instanceof Ast.Statement.Assignment) {
                Ast.Statement.Assignment inc = (Ast.Statement.Assignment) ast.getIncrement();
                requireAssignable(initType, inc.getValue().getType());
            }
        }

        // visit body statements in a new scope
        try {
            scope = new Scope(scope);
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }

        return null;
    }

    /**
     * Validates a while loop, requiring a Boolean condition.
     */
    @Override
    public Void visit(Ast.Statement.While ast) {
        // condition must be Boolean
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

        // visit body statements in a new scope
        try {
            scope = new Scope(scope);
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }

        return null;
    }

    /**
     * Validates that the returned value is assignable to the enclosing method's return type.
     */
    @Override
    public Void visit(Ast.Statement.Return ast) {
        // resolve the value type and check against the method's declared return type
        visit(ast.getValue());
        requireAssignable(method.getFunction().getReturnType(), ast.getValue().getType());
        return null;
    }

    /**
     * Sets the type of a literal based on its Java value, with range checks for numbers.
     */
    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object literal = ast.getLiteral();

        if (literal == null) {
            ast.setType(Environment.Type.NIL);
        } else if (literal instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (literal instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (literal instanceof String) {
            ast.setType(Environment.Type.STRING);
        } else if (literal instanceof BigInteger) {
            // must fit in a 32-bit signed int
            try {
                ((BigInteger) literal).intValueExact();
            } catch (ArithmeticException ex) {
                throw new RuntimeException("Integer literal out of range: " + literal);
            }
            ast.setType(Environment.Type.INTEGER);
        } else if (literal instanceof BigDecimal) {
            // doubleValue() returns Infinity when the value is out of double range
            double value = ((BigDecimal) literal).doubleValue();
            if (Double.isInfinite(value)) {
                throw new RuntimeException("Decimal literal out of range: " + literal);
            }
            ast.setType(Environment.Type.DECIMAL);
        } else {
            throw new RuntimeException("Unknown literal type: " + literal.getClass().getSimpleName());
        }

        return null;
    }

    /**
     * Validates a group expression, requiring the inner expression to be a binary expression.
     */
    @Override
    public Void visit(Ast.Expression.Group ast) {
        // groups are only meaningful around binary expressions
        if (!(ast.getExpression() instanceof Ast.Expression.Binary)) {
            throw new RuntimeException("Group expression must contain a Binary expression.");
        }
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        return null;
    }

    /**
     * Validates a binary expression and sets its result type based on the operator.
     */
    @Override
    public Void visit(Ast.Expression.Binary ast) {
        // resolve types of both operands first
        visit(ast.getLeft());
        visit(ast.getRight());

        String op = ast.getOperator();
        Environment.Type left  = ast.getLeft().getType();
        Environment.Type right = ast.getRight().getType();

        if (op.equals("&&") || op.equals("||")) {
            // both operands must be Boolean
            requireAssignable(Environment.Type.BOOLEAN, left);
            requireAssignable(Environment.Type.BOOLEAN, right);
            ast.setType(Environment.Type.BOOLEAN);

        } else if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=") || op.equals("==") || op.equals("!=")) {
            // both operands must be Comparable and the same type
            requireAssignable(Environment.Type.COMPARABLE, left);
            requireAssignable(Environment.Type.COMPARABLE, right);
            if (!left.equals(right)) {
                throw new RuntimeException("Comparison '" + op + "' requires matching types, got: " + left.getName() + " and " + right.getName());
            }
            ast.setType(Environment.Type.BOOLEAN);

        } else if (op.equals("+")) {
            // string concatenation if either side is a String
            if (left.equals(Environment.Type.STRING) || right.equals(Environment.Type.STRING)) {
                ast.setType(Environment.Type.STRING);
            } else {
                // numeric addition: LHS must be Integer or Decimal; RHS must match
                if (!left.equals(Environment.Type.INTEGER) && !left.equals(Environment.Type.DECIMAL)) {
                    throw new RuntimeException("Binary '+' requires Integer, Decimal, or String on the left, got: " + left.getName());
                }
                if (!right.equals(left)) {
                    throw new RuntimeException("Binary '+' requires matching numeric types, got: " + left.getName() + " and " + right.getName());
                }
                ast.setType(left);
            }

        } else if (op.equals("-") || op.equals("*") || op.equals("/")) {
            // LHS must be Integer or Decimal; RHS must match LHS exactly
            if (!left.equals(Environment.Type.INTEGER) && !left.equals(Environment.Type.DECIMAL)) {
                throw new RuntimeException("Binary '" + op + "' requires Integer or Decimal on the left, got: " + left.getName());
            }
            if (!right.equals(left)) {
                throw new RuntimeException("Binary '" + op + "' requires matching numeric types, got: " + left.getName() + " and " + right.getName());
            }
            ast.setType(left);

        } else {
            throw new RuntimeException("Unknown binary operator: '" + op + "'");
        }

        return null;
    }

    /**
     * Resolves a variable or field access and sets the variable on the AST node.
     */
    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {
            // field access: visit the receiver and look up the field in its type scope
            visit(ast.getReceiver().get());
            ast.setVariable(ast.getReceiver().get().getType().getField(ast.getName()));
        } else {
            // plain variable: look up in the current scope chain
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }
        return null;
    }

    /**
     * Resolves a function or method call and checks argument types against the parameter list.
     */
    @Override
    public Void visit(Ast.Expression.Function ast) {
        // visit all arguments before resolving the function
        for (Ast.Expression arg : ast.getArguments()) {
            visit(arg);
        }

        Environment.Function function;
        int paramOffset = 0; // methods include an implicit self parameter at index 0

        if (ast.getReceiver().isPresent()) {
            // method call: receiver.method(args...)
            visit(ast.getReceiver().get());
            function = ast.getReceiver().get().getType().getFunction(ast.getName(), ast.getArguments().size());
            paramOffset = 1; // skip the implicit self parameter when checking arguments
        } else {
            // free function call: function(args...)
            function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        }

        ast.setFunction(function);

        // verify each argument is assignable to its declared parameter type
        List<Environment.Type> paramTypes = function.getParameterTypes();
        List<Ast.Expression> args = ast.getArguments();
        for (int i = 0; i < args.size(); i++) {
            requireAssignable(paramTypes.get(i + paramOffset), args.get(i).getType());
        }

        return null;
    }

    /**
     * Asserts that {@code type} is assignable to {@code target}, throwing a
     * {@link RuntimeException} when the constraint is not met.
     */
    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        // same type is always valid
        if (target.equals(type)) {
            return;
        }

        // Any is the top type; everything is assignable to it
        if (target.equals(Environment.Type.ANY)) {
            return;
        }

        // Comparable accepts Integer, Decimal, Character, and String
        if (target.equals(Environment.Type.COMPARABLE)) {
            if (type.equals(Environment.Type.INTEGER) || type.equals(Environment.Type.DECIMAL)
                    || type.equals(Environment.Type.CHARACTER) || type.equals(Environment.Type.STRING)) {
                return;
            }
        }

        throw new RuntimeException("Type mismatch: cannot assign " + type.getName() + " to " + target.getName());
    }

}