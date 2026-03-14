# PLC_Project - Programming Language Compiler
**Course:** COP4020 - Programming Language Concepts
**Students:** Blake McGahee, Lance Daley
**Semester:** Spring 2026

---

## Project Overview
This project implements a compiler for a custom programming language in Java. The compiler consists of multiple phases:
1. **P1: Lexical Analysis (Lexer)** - Tokenizes source code into meaningful tokens
2. **P2: Syntax Analysis (Parser)** - Builds an Abstract Syntax Tree (AST) from tokens
3. **P3: Interpreter** - Evaluates the AST by walking the tree and executing operations directly
4. **P4-P5:** Type checking and code generation (future phases)

---

## Implementation Notes

### P1: Lexer Development
- Implemented using character stream with lookahead (`peek`) and consumption (`match`)
- Handles edge cases like leading zeros (`01` is invalid), escape sequences, and unterminated strings
- Follows finite automaton pattern taught in lecture
- Iterative development: helper methods → simple tokens → complex tokens → integration testing

### P2: Parser Development
- Implements recursive descent parsing algorithm
- Expression precedence handled through method call chain (lowest precedence calls highest)
- Statement parsing uses lookahead to determine statement type
- Error recovery: throws exceptions with precise token locations
- Challenge: FOR loop optional initialization/increment required careful null handling

### P3: Interpreter Development
- Implements the Visitor pattern to evaluate each AST node type
- Manages a scope chain for variable and function lookup across nested blocks
- Supports all statement types: Declaration, Assignment, If, While, For, Return
- Supports all expression types: Literal, Group, Binary, Access, Function call
- Arithmetic operations use `BigInteger` and `BigDecimal` for precision
- Short-circuit evaluation implemented for `AND` and `OR` operators
- Function calls use lexical scoping — definition-time scope is captured as a closure, preventing call-site scope leakage
- Key bug fixed: lambda closure capture in `visitMethod` was referencing the mutable `scope` field by reference instead of capturing the definition-time scope, causing incorrect results in function scope tests

---

## Testing Approach
- Unit tests for each token/statement type
- Integration tests for complete expressions and statements
- Edge case testing for error conditions
- All provided test suites pass (31 Lexer tests, 18 Parser tests, 43 Interpreter tests)

---

## Development Timeline

### Week 1-2: P1 Lexer
- **Day 1:** Project setup, `peek()` and `match()` implementation
- **Day 2:** Identifier and number token parsing
- **Day 3:** String and character literals with escape sequences
- **Day 4-5:** Operator handling, testing, bug fixes
- **Day 6-7:** Integration testing, whitespace handling refinement

### Week 3-4: P2 Parser
- **Day 1:** Parser foundation, `peek()` and `match()` adaptation for tokens
- **Day 2:** Primary and secondary expression parsing
- **Day 3:** Binary operator precedence chain
- **Day 4:** Statement parsing (IF, WHILE, RETURN)
- **Day 5:** FOR loop implementation, declaration statements
- **Day 6:** Error handling improvements, IndexOutOfBoundsException fixes
- **Day 7:** Final testing, all tests passing

### Week 5-6: P3 Interpreter
- **Day 1:** Interpreter foundation, Visitor pattern setup, scope infrastructure
- **Day 2:** Literal, Group, and Binary expression evaluation
- **Day 3:** Variable access, assignment, and declaration statements
- **Day 4:** If, While, and For statement execution with proper scope management
- **Day 5:** Function/method definition and invocation, Return exception handling
- **Day 6:** Lexical closure fix for function scope, TA feedback corrections
- **Day 7:** Final testing, 43/43 tests passing, log function added to interpreter scope for baseline test compatibility

---

## Learning Outcomes
Through this project, we gained experience with:
- **Lexical Analysis:** Finite automata, regular expressions, tokenization
- **Syntax Analysis:** Context-free grammars, recursive descent parsing, operator precedence
- **Interpretation:** AST traversal, Visitor pattern, runtime scope management, lexical closures
- **Language Design:** Grammar specification, AST design, error handling
- **Software Engineering:** Test-driven development, Git workflow, iterative development
- **Problem Solving:** Debugging parser edge cases, handling optional grammar elements, fixing closure capture bugs

---

**Repository:** https://github.com/blakemcgahee/PLC_Project
