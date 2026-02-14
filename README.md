# PLC_Project - Programming Language Compiler

**Course:** COP4020 - Programming Language Concepts  
**Students:** Blake McGahee, Lance Daley  
**Semester:** Spring 2026

---

## Project Overview

This project implements a compiler for a custom programming language in Java. The compiler consists of multiple phases:

1. **P1: Lexical Analysis (Lexer)** - Tokenizes source code into meaningful tokens
2. **P2: Syntax Analysis (Parser)** - Builds an Abstract Syntax Tree (AST) from tokens
3. **P3-P5:** Semantic analysis, type checking, and code generation (future phases)

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

---

## Testing Approach

- Unit tests for each token/statement type
- Integration tests for complete expressions and statements
- Edge case testing for error conditions
- All provided test suites pass (49 Lexer tests, 37 Parser tests)

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

---

## Learning Outcomes

Through this project, we gained experience with:

- **Lexical Analysis:** Finite automata, regular expressions, tokenization
- **Syntax Analysis:** Context-free grammars, recursive descent parsing, operator precedence
- **Language Design:** Grammar specification, AST design, error handling
- **Software Engineering:** Test-driven development, Git workflow, iterative development
- **Problem Solving:** Debugging parser edge cases, handling optional grammar elements

---

**Repository:** https://github.com/blakemcgahee/PLC_Project
