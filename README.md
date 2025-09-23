# JSON Mapping Engine (JME)

JME is a streaming-first JSON transformation engine powered by a declarative DSL. It lets teams describe mappings once and reuse them to validate inputs, derive fields, and emit normalized payloads across services.

- **Streaming pipeline** – validation, compilation, and execution all operate on streams so large payloads never buffer in memory.
- **Deterministic optimizations** – constant folding, literal pooling, and inline subgraph expansion reduce runtime work before the interpreter even runs.
- **Composable runtime** – `EngineBinding` embeds mappings in JVM services, while the CLI executes the same configs for jobs or debugging.
- **Tooling** – a schema generator mirrors the `VARIABLES` contract as JSON Schema; docs cover the DSL, optimizer internals, and roadmap.

## When to use JME

- You need a contract-driven way to reshape JSON payloads for downstream systems or digital twins.
- Domain experts should author mappings in DSL files rather than Java or ad-hoc scripts.
- Transformations must enforce schema validation, builtin functions, and reusable logic at runtime.
- Output payloads should stream straight to files, HTTP, Kafka, etc. without materialising whole documents.

## Core Capabilities

| Area | Highlights |
| --- | --- |
| Validation | Streaming section checks, pointer-rich diagnostics, schema references |
| Compilation | Single-pass IR builder, reference resolution with cycle detection |
| Runtime | Streaming interpreter, nested mapping reuse, input resolver, variable snapshots |
| Optimizer | Constant folding (including deterministic builtins), literal pooling across mappings, inline subgraph expansion |
| Tooling | CLI runner, schema generator, docs for DSL v2 and optimizer internals |

## Project Layout

- `engine/` – validator, compiler, optimizer, runtime, CLI entrypoint.
- `engine/docs/` – DSL specs, optimizer notes, progress snapshot, and a docs README for quick navigation.
- `engine/examples/` – sample configs and CLI walkthroughs.
- `schema-generator/` – CLI that exports JSON Schema derived from `VARIABLES` declarations.

## Getting Started

### Build
```bash
mvn -pl engine -am package
```
The shaded distribution lands at `engine/target/engine-1.0.1-SNAPSHOT-all.jar`.

### Run a Mapping
```bash
java -jar engine/target/engine-1.0.1-SNAPSHOT-all.jar   --config engine/src/main/resources/config_v2.json   --mapping sample   --input examples/cli-sample/input.json   --payload examples/cli-sample/payload.json   --output out.json   --pretty
```
Use `-Djme.profile.instructions=true` to emit Jackson Flight Recorder metrics for executed opcodes.

### Generate a Schema
```bash
mvn -pl schema-generator -am package
java -jar schema-generator/target/schema-generator-1.0.1-SNAPSHOT.jar   --config engine/src/main/resources/config_v2.json   --output variables-schema.json   --pretty
```
The generator streams the DSL, reuses the validator, and emits JSON Schema that mirrors the variable contract.

### Embed the Engine
```java
EngineBinding binding = EngineBinding.fromPath(Path.of("config.json"));
EngineBinding.ExecutionResult result = binding.execute(
    "shipment",
    Map.of("tenant", "acme"),
    Map.of("sourcePayload", payload)
);
System.out.println(result.prettyOutput());
```
`EngineBinding` exposes the compiled program, rendered output, and variable snapshot for auditing.

## Developer Utilities

Common host-side helpers when working with `EngineBinding`:

- Config loader that stitches environment-specific overrides before compilation.
- Input/payload normaliser to coerce incoming requests to expected types.
- Default filler that applies tenant/business rules before the engine runs.
- Contract validator for friendly preflight errors.
- Result post-processor that wraps the engine output with metadata.
- Diagnostics collector to log execution time, folded literals, or variable snapshots.

## Documentation

Key references live under `engine/docs/`:

- `dsl-v2-spec.md` – human-friendly DSL overview.
- `dsl-v2-reference.md` – grammar and keyword reference.
- `runtime-optimizations.md` – optimizer pipeline, builtin folding, literal pooling, and inlining heuristics.
- `progress-context.md` – feature snapshot and roadmap notes.
- `README.md` (in `engine/docs/`) – quick index of available documents.

## Test & Verification

```bash
mvn -pl engine test
mvn -pl schema-generator -am test
```
Fixtures cover validation, compiler regressions, optimizer behaviour, and runtime integration (including advanced mapping scenarios).

Have an idea for new builtin functions, diagnostics, or integrations? Issues and PRs are welcome.
