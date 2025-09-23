# Engine Internals Outline

## 1. Streaming Pipeline Overview
- Recap Validator -> Compiler -> Runtime flow and how streaming boundaries are preserved.
- Identify shared infrastructure (JsonParser / JsonGenerator, intermediate instruction program).

## 2. Compiler Stages
### 2.1 Config Validation
- Section guards, pointer tracking, and diagnostics emission.
### 2.2 Reference Resolution
- Symbol tables for functions, variables, mappings, schemas.
- Cycle detection semantics.
### 2.3 Instruction Optimization
- Constant folding and literal pooling steps.
- Inline subgraph heuristics (new) and protection rules.

## 3. Instruction Emission
- Opcode layout (BEGIN_OBJECT/WRITE_FIELD/etc.).
- Field/literal interning tables and ordering guarantees.

## 4. Runtime Execution
### 4.1 ExecutionContext
- Resolver binding lifecycle (inputs, variables, functions).
### 4.2 MappingInterpreter
- Opcode dispatch loop and re-entrancy.
- Interaction with inlined vs. referenced subgraphs.

## 5. Observability Hooks
- Instruction metrics collection.
- Planned tracing / diagnostics extensions.

## 6. Future Work
- Remaining optimization tasks (constant propagation, reuse across configs).
- API hooks still outstanding (before/after mapping callbacks).
