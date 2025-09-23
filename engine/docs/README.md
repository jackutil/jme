# Engine Documentation Index

Quick links for contributors exploring the JME engine internals.

## Core Specs

- `dsl-v2-spec.md` - human-readable DSL authoring guide with examples.
- `dsl-v2-reference.md` - full grammar reference with keywords, sections, and validation rules.

## Engine Internals

- `runtime-optimizations.md` - instruction optimizer pipeline (constant folding, builtin evaluation, literal pooling, inline subgraphs) and runtime dispatch details.
- `engine-internals-outline.md` - high-level outline for future deep-dive docs covering validation, compilation, emission, and runtime execution.
- `progress-context.md` - snapshot of completed work and the forward-looking roadmap.

## Examples & Fixtures

- `../examples/` - CLI walkthroughs and mapping samples.
- `../src/test/resources/valid/` - regression configs used by tests (constants, inputs, arrays, advanced order scenario).

## Host Integration

When embedding via `EngineBinding`, consider companion utilities for config loading, input normalisation, result post-processing, and metrics collection. See the root README for guidance.

