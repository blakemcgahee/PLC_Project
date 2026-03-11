package plc.project;
import java.util.ArrayList;
import java.util.List;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {

        // iterate through all global variables and register each field
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        // iterate through all defined methods and register in interpreter scope
        for  (Ast.Method method : ast.getMethods()) {
            visit(method);
        }
        // execute main with no parameters and return result
        return scope.lookupFunction("main", 0).invoke(List.of());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        // Goal is to define a global variable
        // FIELDS: "LET x = 50;" and "LET CONST y = 0;"
        // Initialize variable with default NIL
        Environment.PlcObject value = Environment.NIL;

        // Determine if value exist and set to variable if so
        if (ast.getValue().isPresent()) {
            value = visit(ast.getValue().get());
        }
        // register teh variable inside of current scope
        // arguments are (name, constant flag, value)
        scope.defineVariable(ast.getName(), ast.getConstant(), value);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        // Register and store method for future use
        /* METHOD: "DEF add(j, k) DO
                        RETURN j + k;
                    END"
         */
        //register function with (name, num of parms, function body)
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            scope = new Scope(scope);

            try {
                // bind parameters given the number of parameters
                for (int i = 0; i < ast.getParameters().size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), false, args.get(i));
                }
                // execute statements inside function
                for (Ast.Statement statement : ast.getStatements()) {
                    visit(statement);
                }
            // catches the return exception used to exit functions and return value
            } catch (Return r) {
                return r.value;
            } finally {
                // restore the previous scope after execution
                scope = scope.getParent();
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        // Evaluate the expression for its side effects (e.g., print("Hello, World!")).
        // The result is intentionally discarded - statement expressions don't return a value.
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(),false, visit(ast.getValue().get()));
        } else {
            scope.defineVariable(ast.getName(),false,Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        /* goal is to execute assignments like "x = 5;" or "obj.field = 1;" */

        // verify assignment target is valid, i.e. access expression
        if (!(ast.getReceiver() instanceof Ast.Expression.Access access)) {
            throw new RuntimeException("The assignment must receive an access expression");
        }

        // evaluate the rhs expression
        Environment.PlcObject value = visit(ast.getValue());

        // set value for "field" case else set value for variable case
        if (access.getReceiver().isPresent()) {
            Environment.PlcObject receiver = visit(access.getReceiver().get());
            receiver.getField(access.getName()).setValue(value);
        } else {
            scope.lookupVariable(access.getName()).setValue(value);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        /* goal is to execute statements depending upon boolean condition */

        // Evaluate condition expression
        boolean condition = requireType(Boolean.class, visit(ast.getCondition()));

        // Choose which block of statement(s) to execute
        List<Ast.Statement> branch = condition ? ast.getThenStatements() : ast.getElseStatements();

        // Create a temporary scope within which to execute block(s)
        scope = new Scope(scope);

        try {
            // Execute each statement in the selected branch of statement(s)
            for (Ast.Statement statement : branch) {
                visit(statement);
            }
        } finally {
            // Return to previous scope
            scope = scope.getParent();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.For ast) {
        /* goal is to iteratively execute block of statement(s) for defined increment */

        // create a new loop scope within which to execute for statement
        scope = new Scope(scope);

        try {
            // establish loop initialization
            if (ast.getInitialization() != null) {
                visit(ast.getInitialization());
            }
            // test each iteration to determine if execution proceeds
            while (requireType(Boolean.class, visit(ast.getCondition()))) {
                for (Ast.Statement statement : ast.getStatements()) {
                    visit(statement);
                }
                // increment the counter
                if (ast.getIncrement() != null) {
                    visit(ast.getIncrement());
                }
            }
        } finally {
            // Return to previous scope
            scope = scope.getParent();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);

                // alternate version of for loop statement to visit all statements
                // ast.getStatements().forEach(this::visit);
                // same as below

                for (Ast.Statement statement : ast.getStatements()) {
                    visit(statement);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        /* goal is to return a value from a function */

        // because "method" handler catches the return: "catch (Return r)"
        // wrap expression in a return exception and throw it
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        // NIL is represented as a null literal in the AST, return the singleton NIL object
        // rather than wrapping null inside a new PlcObject, which would cause issues downstream.
        if (ast.getLiteral() == null) {
            return Environment.NIL;
        }

        // For all other literal types (Boolean, BigInteger, BigDecimal, Character, String),
        // wrap the raw Java value in a PlcObject so the interpreter can work with it uniformly.
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        // A group expression is purely for controlling precedence during parsing.
        // At interpretation time, the grouping has no effect we just evaluate what's inside.
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String op = ast.getOperator();

        // AND/OR use short circuit evaluation, we only evaluate the RHS if necessary.
        // Per the spec, both sides must be Booleans.
        if (op.equals("AND")) {
            boolean lhs = requireType(Boolean.class, visit(ast.getLeft()));
            // Short circuit: if LHS is false, the result is always false - skip RHS
            if (!lhs) return Environment.create(false);
            boolean rhs = requireType(Boolean.class, visit(ast.getRight()));
            return Environment.create(rhs);
        } else if (op.equals("OR")) {
            boolean lhs = requireType(Boolean.class, visit(ast.getLeft()));
            // Short circuit: if LHS is true, the result is always true - skip RHS
            if (lhs) return Environment.create(true);
            boolean rhs = requireType(Boolean.class, visit(ast.getRight()));
            return Environment.create(rhs);
        }

        // For all remaining operators, evaluate both sides upfront
        Environment.PlcObject left = visit(ast.getLeft());
        Environment.PlcObject right = visit(ast.getRight());

        // Comparison operators: LHS must be Comparable, RHS must be the same type as LHS
        if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=")) {
            Comparable<Object> lhs = requireType(Comparable.class, left);
            // RHS must match the exact same class as LHS, no mixed type comparisons allowed
            Object rhs = requireType(left.getValue().getClass(), right);
            int result = lhs.compareTo(rhs);
            switch (op) {
                case "<":  return Environment.create(result < 0);
                case "<=": return Environment.create(result <= 0);
                case ">":  return Environment.create(result > 0);
                case ">=": return Environment.create(result >= 0);
            }
        }

        // Equality: uses Objects.equals which handles null safely, unlike .equals()
        if (op.equals("==")) {
            return Environment.create(java.util.Objects.equals(left.getValue(), right.getValue()));
        } else if (op.equals("!=")) {
            return Environment.create(!java.util.Objects.equals(left.getValue(), right.getValue()));
        }

        // Addition: handles both String concatenation and BigInteger/BigDecimal addition
        if (op.equals("+")) {
            // If either side is a String, concatenate both as strings
            if (left.getValue() instanceof String || right.getValue() instanceof String) {
                return Environment.create(left.getValue().toString() + right.getValue().toString());
            }
            // Otherwise both must be the same numeric type
            if (left.getValue() instanceof java.math.BigInteger) {
                return Environment.create(
                        requireType(java.math.BigInteger.class, left)
                                .add(requireType(java.math.BigInteger.class, right))
                );
            } else if (left.getValue() instanceof java.math.BigDecimal) {
                return Environment.create(
                        requireType(java.math.BigDecimal.class, left)
                                .add(requireType(java.math.BigDecimal.class, right))
                );
            }
            throw new RuntimeException("Unsupported type for + operator: " + left.getValue().getClass().getName());
        }

        // Subtraction and Multiplication: both sides must be the same BigInteger/BigDecimal type
        if (op.equals("-") || op.equals("*")) {
            if (left.getValue() instanceof java.math.BigInteger) {
                java.math.BigInteger lhs = requireType(java.math.BigInteger.class, left);
                java.math.BigInteger rhs = requireType(java.math.BigInteger.class, right);
                return Environment.create(op.equals("-") ? lhs.subtract(rhs) : lhs.multiply(rhs));
            } else if (left.getValue() instanceof java.math.BigDecimal) {
                java.math.BigDecimal lhs = requireType(java.math.BigDecimal.class, left);
                java.math.BigDecimal rhs = requireType(java.math.BigDecimal.class, right);
                return Environment.create(op.equals("-") ? lhs.subtract(rhs) : lhs.multiply(rhs));
            }
            throw new RuntimeException("Unsupported type for " + op + " operator: " + left.getValue().getClass().getName());
        }

        // Division: same type requirement, plus zero-division check.
        // BigDecimal uses HALF_EVEN rounding per the spec.
        if (op.equals("/")) {
            if (left.getValue() instanceof java.math.BigInteger) {
                java.math.BigInteger lhs = requireType(java.math.BigInteger.class, left);
                java.math.BigInteger rhs = requireType(java.math.BigInteger.class, right);
                if (rhs.equals(java.math.BigInteger.ZERO))
                    throw new RuntimeException("Division by zero.");
                return Environment.create(lhs.divide(rhs));
            } else if (left.getValue() instanceof java.math.BigDecimal) {
                java.math.BigDecimal lhs = requireType(java.math.BigDecimal.class, left);
                java.math.BigDecimal rhs = requireType(java.math.BigDecimal.class, right);
                if (rhs.compareTo(java.math.BigDecimal.ZERO) == 0)
                    throw new RuntimeException("Division by zero.");
                return Environment.create(lhs.divide(rhs, java.math.RoundingMode.HALF_EVEN));
            }
            throw new RuntimeException("Unsupported type for / operator: " + left.getValue().getClass().getName());
        }

        throw new RuntimeException("Unknown binary operator: " + op);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        // If there's a receiver, evaluate the receiver first
        // then look up the named field on the resulting PlcObject.
        if (ast.getReceiver().isPresent()) {
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            return receiver.getField(ast.getName()).getValue();
        }

        // No receiver means it is a simple variable access.
        // Look it up in the current scope, walking up the scope chain if needed.
        return scope.lookupVariable(ast.getName()).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        // Evaluate all argument expressions upfront into a list of PlcObjects
        // that will be passed into the function/method call.
        List<Environment.PlcObject> args = new ArrayList<>();
        for (Ast.Expression arg : ast.getArguments()) {
            args.add(visit(arg));
        }

        // If there's a receiver, evaluate the receiver first
        // then invoke the named method on that PlcObject, passing the evaluated args.
        if (ast.getReceiver().isPresent()) {
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            return receiver.callMethod(ast.getName(), args);
        }

        // No receiver means it is a plain function call.
        // Look up the function by name and arity in the current scope, then invoke it.
        return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(args);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
