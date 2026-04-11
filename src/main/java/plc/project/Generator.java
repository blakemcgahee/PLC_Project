package plc.project;

import java.io.PrintWriter;
import java.util.List;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    // if the object passed in is an AST node visit it, otherwise write its string value
    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    // writes a newline followed by 4 spaces per indent level
    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        // everything lives inside a class called Main
        print("public class Main {");
        indent++;
        newline(0);

        // fields are grouped together above the Java entry point
        // if there are no fields this block is skipped entirely
        if (!ast.getFields().isEmpty()) {
            for (Ast.Field field : ast.getFields()) {
                newline(indent);
                visit(field);
            }
            // blank line after the fields block to separate from the methods below
            // originally used newline(indent) here thinking it needed to match indentation
            // but that was writing 4 spaces on an otherwise empty line and the test string
            // comparisons were failing because "    \n" != "\n" newline(0) is correct
            newline(0);
        }

        // hardcoded Java entry point that creates a Main instance and calls our language's main()
        // System.exit passes the return value of main() as the process exit code
        newline(indent);
        print("public static void main(String[] args) {");
        newline(indent + 1);
        print("System.exit(new Main().main());");
        newline(indent);
        print("}");

        // each method gets a blank line above it to keep things readable
        for (Ast.Method method : ast.getMethods()) {
            newline(0);
            newline(indent);
            visit(method);
        }

        // step indent back out before closing the class
        indent--;
        newline(0);
        newline(0);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        // constant fields need the final modifier to match Java semantics
        if (ast.getConstant()) {
            print("final ");
        }

        // JVM type and variable name are resolved by the analyzer, not the raw source name
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        // only emit the initializer if the field was declared with a value
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }

        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        // JVM return type and method name come from the function set during analysis
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName(), "(");

        // build the parameter list where each parameter is a JVM type name followed by the param name
        List<String> params = ast.getParameters();
        List<Environment.Type> paramTypes = ast.getFunction().getParameterTypes();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) print(", ");
            // tried pulling types from ast.getParameterTypeNames() first but that returns
            // the source language names like "Integer" and "Decimal" just like the declaration issue
            // the correct JVM names like "int" and "double" only come from the resolved function
            print(paramTypes.get(i).getJvmName(), " ", params.get(i));
        }
        print(") {");

        // empty body closes on the same line, otherwise indent and emit each statement
        if (ast.getStatements().isEmpty()) {
            print("}");
        } else {
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                visit(stmt);
            }
            // step back out to the methods indentation level for the closing brace
            newline(--indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        // expression statements exist for their side effects, e.g. print("hello")
        // the semicolon is appended here since expressions don't add it themselves
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        // type and variable name are pulled from the variable resolved during analysis
        // this ensures we use the JVM name, which may differ from the source name

        // first tried using ast.getTypeName().get() here but that returns the source language
        // type like Integer or Decimal instead of the JVM equivalent int or double
        // tests were failing because the output was Integer name; instead of int name;
        // the correct path is through the resolved variable on the AST node
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        // only emit the initializer if a value was provided in the declaration
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }

        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        // receiver can be a variable or a field access, and print() handles both via visit()
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        // condition is wrapped in parens, opening brace follows on the same line
        print("if (", ast.getCondition(), ") {");

        // if the then block is empty close immediately, otherwise indent the body
        if (ast.getThenStatements().isEmpty()) {
            print("}");
        } else {
            indent++;
            for (Ast.Statement stmt : ast.getThenStatements()) {
                newline(indent);
                visit(stmt);
            }
            newline(--indent);
            print("}");
        }

        // else is optional, and if present it opens on the same line as the closing brace above
        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            indent++;
            for (Ast.Statement stmt : ast.getElseStatements()) {
                newline(indent);
                visit(stmt);
            }
            newline(--indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        // spaces inside the parens are always present even when init/increment are absent
        // format is: for ( [init]; condition; [increment] ) { ... }
        print("for ( ");

        // initialization is optional, so null means nothing is emitted before the first semicolon
        if (ast.getInitialization() != null) {
            printForClauseStmt(ast.getInitialization());
        }

        // originally tried just calling visit(ast.getInitialization()) directly here
        // but visit(Assignment) appends a semicolon, which broke the for signature
        // it was generating for ( num = 0;; num < 5; ... )" with a double semicolon
        // had to write printForClauseStmt to emit the assignment without the trailing one

        // condition always sits between the two semicolons
        print("; ", ast.getCondition(), "; ");

        // increment is optional, so null means nothing is emitted before the closing paren
        // when increment is present a trailing space is needed before the closing paren
        if (ast.getIncrement() != null) {
            printForClauseStmt(ast.getIncrement());
            print(" ");
        }

        print(") {");

        // same empty body pattern as the other block statements
        if (ast.getStatements().isEmpty()) {
            print("}");
        } else {
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                visit(stmt);
            }
            newline(--indent);
            print("}");
        }

        return null;
    }

    // init and increment sit inside the for signature so they cant have a trailing semicolon
    // we pull the assignment apart and emit it manually rather than delegating to visit(Assignment)
    private void printForClauseStmt(Ast.Statement stmt) {
        if (stmt instanceof Ast.Statement.Assignment) {
            Ast.Statement.Assignment assign = (Ast.Statement.Assignment) stmt;
            print(assign.getReceiver(), " = ", assign.getValue());
        }
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        // condition is wrapped in parens, same brace style as if and for
        print("while (", ast.getCondition(), ") {");

        // empty body closes immediately, otherwise each statement gets its own indented line
        if (ast.getStatements().isEmpty()) {
            print("}");
        } else {
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                visit(stmt);
            }
            newline(--indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        // return keyword, the value expression, then semicolon to close the statement
        print("return ", ast.getValue(), ";");
        return null;
    }


    // -------------------------------------------------------------------------
    // TODO: implement the five expression visitors below
    // -------------------------------------------------------------------------

    // Literal will handle correct Java syntax
    @Override
    public Void visit(Ast.Expression.Literal ast) {

        Object value = ast.getLiteral();                // retrieve the raw literal from AST node

        if (value == null) {                            // explicit null handling
            print("null");
        } else if (value instanceof String) {           // strings require double quotes
            print("\"" + value.toString() + "\"");
        } else if (value instanceof Boolean) {          // booleans map directly to keyword
            print(value.toString());
        } else if (value instanceof Character) {        // char requires single quotes
            print("'", value.toString(), "'");
        } else {                                        // assume remaining types are numeric
            print(value.toString());
        }
        return null;
    }

    // Group will enforce correct evaluation order
    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");           // grouping via parentheses for precedence
        return null;
    }

    // Binary generates correct binary expressions with proper spacing
    @Override
    public Void visit(Ast.Expression.Binary ast) {      // output operands with operator
        print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        return null;
    }

    // Access makes determination of variable or field via receiver presence
    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {                    // check if this is a field access
            print(ast.getReceiver().get(), ".", ast.getVariable().getJvmName());    // print obj ref, dot, variable name
        } else {                                                // simple variable reference
            print(ast.getVariable().getJvmName());              // print variable name
        }
        return null;
    }

    // Function to handle static and receiver based calls with proper formats
    @Override
    public Void visit(Ast.Expression.Function ast) {
        if (ast.getReceiver().isPresent()) {                    // check for method call
            print(ast.getReceiver().get(), ".");                // print object reference and dot
        }
        print(ast.getFunction().getJvmName(), "(");             // print function name and open parenthesis

        for (int i = 0; i < ast.getArguments().size(); i++) {   // iterate through function args
            if (i > 0) print(", ");                             // comma separated arguments
            print(ast.getArguments().get(i));                   // print each argument
        }
        print(")");                                             // close parenthesis to end arg list
        return null;
    }
}