# Mapping Engine Progress Snapshot

## What's in place
- **DSL v2 spec + validation**: Human/machine docs, streaming `ConfigValidator`, and fixture-based tests covering required sections, identifiers, type expectations, and schema references.
- **Compiler pipeline**: Streaming parse builds IR, `ReferenceResolver` handles functions/variables/mappings/schemas with cycle detection, and `InstructionCompiler` emits opcode streams per mapping.
- **Runtime engine**: `MappingEngine` executes directly to a `JsonGenerator`, supports nested mappings/arrays, required/default/derived variables, constraint enforcement, and the extended builtin catalog (uuid/date/numeric/string/lookup/math helpers).
- **Input bindings**: DSL `INPUT` section declares host-provided values, wiring through `InputResolver`, `$INPUT.*` references in mappings, and dedicated input maps for CLI/runtime entry points.
- **File-based CLI runner**: `Main` now accepts `--config`, `--mapping`, optional `--input`/`--payload`/`--output`, supports `--pretty`, and respects `-Djme.profile.instructions=true` for JFR metrics when streaming results.
- **Structured diagnostics**: Errors surface as `MappingException` with code/message/pointer from compiler, resolver, and runtime (missing references, type errors, constraint failures, unknown mappings, etc.).
- **Component coverage**: Unit tests for builtins, variable resolver, compiler fixtures (happy + error), runtime integration (simple/builtins/array), plus validation matrix.

## Outstanding / next steps
1. **Instruction optimizations**: constant folding, literal pooling across mappings, and inlining reusable subgraphs to cut runtime allocations.
2. **Execution hooks & metrics**: public API for before/after mapping (logging, metrics, tracing), plus surfacing diagnostics aggregates to callers.
3. **Enhanced parsing diagnostics**: section-level pointer tracking inside `SectionParsers` so field-level errors report exact JSON pointers.
4. **Schema/type enforcement extras**: optional payload type coercion hints (enum validation, nested array/object shape verification) and richer error detail payloads.

## Notes for next session
- Tests currently green via `mvn -q clean test`.
- Config fixtures live under `src/test/resources` (`valid/refs.json`, `valid/builtins.json`, `valid/arrays.json`, plus invalid cases).
- Runtime tests sit in `src/test/java/github/jackutil/compiler/runtime/` and rely on `MappingException` for assertions.
- `docs/dsl-v2-spec.md` and `docs/dsl-v2-reference.md` are the authoritative DSL descriptors.

Pick up with instruction optimization or execution hooks depending on priorities.


