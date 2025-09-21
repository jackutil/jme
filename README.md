# JSON Mapping Engine (JME)

JME is a streaming-first JSON transformation engine powered by a declarative DSL. It was built to let data engineering teams translate inbound platform payloads into SAMM aspects (or other structured targets) without hand-written code. Author a mapping once, then reuse it to validate inputs, derive fields, and emit normalized payloads in a consistent way across services and delivery channels.

## When to use JME

- You need a contract-driven way to reshape JSON payloads for downstream systems or digital twins.
- Domain experts should be able to describe mappings in a DSL rather than Java or scripting glue.
- Transformations must enforce schema validation, derived values, and reusable functions at runtime.
- Output payloads should stream straight to their destination (files, HTTP, Kafka) without building large in-memory objects.

## Streaming-first pipeline

JME keeps memory use predictable by streaming every major stage:

1. **Streaming validation** - ConfigValidator walks the DSL file with Jackson's JsonParser, enforcing section-level rules, type guards, and identifier hygiene as bytes arrive. Nothing is materialized beyond what is necessary for diagnostics.
2. **Streaming compilation** - StreamingCompiler performs a single-pass parse that hands tokens off to section-specific parsers. The compiler produces an intermediate representation (IR) with opcode blocks, literal tables, and reference indexes while continuing to read from the stream.
3. **Streaming execution** - MappingEngine interprets the IR directly against a JsonGenerator, writing the target document field-by-field. Inputs, payload variables, and nested mappings resolve on demand, so outputs of any size can flow through without buffering.

This architecture makes it practical to run large mappings inside batch jobs or latency-sensitive services where holding full documents in memory would be prohibitive.

## Project layout

- Engine/ - Core validator, compiler, runtime interpreter, and CLI entry point.
- Engine/docs/ - DSL v2 specification and reference material.
- Engine/examples/ - Sample configurations and CLI walkthroughs.
- Engine/src/main/resources/config_v2.json - Canonical example used by tests and documentation.

## Getting started

### Build

```Bash
mvn -pl engine -am package
```

The shaded distribution lands at Engine/target/engine-1.0.0-SNAPSHOT-all.jar.

### Run a mapping from the CLI

```Bash
java -jar engine/target/engine-1.0.0-SNAPSHOT-all.jar ^
  --config engine/src/main/resources/config_v2.json ^
  --mapping sample ^
  --input examples/cli-sample/input.json ^
  --payload examples/cli-sample/payload.json ^
  --output out.json ^
  --pretty
```

Use -Djme.profile.instructions=true during launch to record Jackson Flight Recorder metrics for executed opcodes.

### Embed the engine

```java
EngineBinding binding = EngineBinding.fromPath(Path.of("config.json"));
EngineBinding.ExecutionResult result = binding.execute(
    "shipment",
    Map.of("tenant", "acme"),
    Map.of("sourcePayload", payload)
);
System.out.println(result.prettyOutput());
```

EngineBinding exposes the compiled artifact, the rendered output, and a snapshot of runtime variables for auditing or debugging.

## DSL resources

- Engine/docs/dsl-v2-spec.md - Human-readable authoring guide.
- Engine/docs/dsl-v2-reference.md - Machine-friendly grammar reference.
- Engine/docs/progress-context.md - Current feature set and roadmap notes.

## Test & verification

```Bash
mvn -pl engine test
```

Tests cover validation fixtures, compiler regression cases, runtime integration, and builtin function behaviour.

Feel free to open an issue or pull request if you have ideas for additional streaming integrations, builtin functions, or diagnostics.
