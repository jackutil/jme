# Runtime Optimizations

This document captures the optimizer and interpreter mechanics that shape JME runtime performance. It should give contributors enough context to reason about opcode changes, cost trade-offs, and guard rails when extending the engine.

## Instruction Optimizer Pipeline

1. **Reference counting** - every `ResolvedMapNode.MappingRefNode` increments a counter for its target mapping. The counts drive inline heuristics and prevent accidental removal of shared subtrees.
2. **Constant folding** - nested literal/object/array nodes that resolve to static values are collapsed into shared literal objects. Compile-time evaluation now also runs deterministic builtin derives (string casing, arithmetic, padding, min/max, etc.) when all arguments are literals, baking the result straight into variable defaults.
3. **Literal pooling** - after folding, identical literal values across mappings share a single `ResolvedMapNode.LiteralNode` so the emitted program references one canonical entry.
4. **Inline subgraph expansion** - single-use mapping references are cloned into the caller when they meet the heuristics described below, removing interpreter recursion for hot, repeated structures.


## Builtin Folding

Variable derives that call deterministic builtins (uppercase/lowercase/trim, concat, padding, add/subtract/multiply/divide, min/max, abs) are evaluated during optimization when every argument is a literal. The derive metadata is cleared and the folded value is stored as the variable default so runtime never invokes the builtin. Non-deterministic helpers (date/uuid/random) remain untouched.

## Literal Pooling

Literal pooling happens after folding to capture both hand-authored literals and builtin outputs. Nested maps and arrays are canonicalised so repeated structures reuse a single node, keeping the instruction literal table compact and preserving object identity checks in tests.
## Inline Subgraph Heuristics

Inlining trades compiler work for runtime savings. The optimizer applies the following checks before rewriting a `MappingRefNode`:

- **Reference budget**: only mappings referenced once (`referenceCounts` <= 1) are eligible. Shared mappings still execute through `WRITE_MAPPING` opcodes to avoid code bloat.
- **Protected roots**: mappings wired to `ENGINE.output` are never inlined, even when reference counts allow it. This keeps top-level entry points intact for API consumers and diagnostics.
- **Node complexity cap**: the total node count of the target mapping must be below `INLINE_MAX_NODE_COUNT` (currently 128). This avoids generating massive opcode sequences that would negate the runtime win.
- **Re-entrancy guard**: mappings that immediately dispatch other mappings are left alone to avoid deep nesting scenarios we cannot flatten yet.

If all checks pass, the optimizer deep-clones the resolved node graph and replaces the reference. Cloning preserves variable/input references while preventing shared mutable state between parents.

## Emission Impact

The `InstructionEmitter` is unaware of the rewrite and simply walks the updated IR. Inlined mappings therefore turn into a contiguous opcode sequence (`BEGIN_OBJECT`, `WRITE_FIELD`, etc.) inside the caller block. Shared mappings continue to emit a single `WRITE_MAPPING` opcode that points at their dedicated block.

## Interpreter Behaviour

`MappingInterpreter` still executes blocks recursively for `WRITE_MAPPING`, but the inlined paths now stay inside the current block. This eliminates extra stack frames, reduces map lookups, and keeps `JsonGenerator` interactions linear for repeated payload rows.

## Diagnostics and Tests

- `ConfigCompilerTest#inlinesSingleUseMappings` asserts that detail mappings inline while shared ones remain referenced.
- `ConfigCompilerTest#poolsLiteralsAcrossMappings` and `ConfigCompilerTest#foldsDeterministicBuiltinDerives` lock in literal pooling and builtin folding outcomes.
- When debugging inline behaviour, turn on the instruction metrics profiling flag (`-Djme.profile.instructions=true`) to compare opcode counts before and after optimizer tweaks.

## Extending the Optimizer

Future enhancements should respect the guard rails above. In particular:

- Adjust `INLINE_MAX_NODE_COUNT` only alongside profiling evidence and consider making it configurable.
- Introduce cost models if we add array unrolling or cross-mapping literal pooling to keep the compiler fast.
- Collect heuristics metrics to expose inline decisions in the planned tracing hooks.
