# Mapping DSL v2 Specification

This document explains how to author mapping configurations for the v2 mapping engine.

## Structure Overview

Each configuration is a single JSON document with the following top-level sections:

- `META`: descriptive metadata about the configuration.
- `ENGINE`: directives for the runtime engine.
- `SCHEMA`: registry of validation schemas used during or after mapping.
- `FUNCTIONS`: custom reusable validation helpers.
- `VARIABLES`: canonical input contract and derived values.
- `MAPPINGS`: transformation instructions grouped by logical name.
- `VALIDATION`: runtime validation stages applied to the produced payload.

All sections are required unless noted otherwise. Unknown sections should be rejected for forward compatibility.

## Section Details

### META
- `dslVersion` (string, required): version of the DSL the document complies with.
- `name` (string, required): unique identifier for the mapping package.
- `targetAspect` (string, required): URN of the SAMM aspect being produced.
- `description` (string, optional): author supplied description.
- `owner` (string, optional): team or person responsible.
- `lastUpdated` (string, optional): ISO-8601 date of last edit.

### ENGINE
- `api` (string, required): engine compatibility level.
- `output` (string, required): reference to the root mapping (e.g., `$MAPPINGS.batch`).
- `validation` (string, optional): reference to a schema entry that should always run post-map.

### SCHEMA
A JSON object keyed by schema identifiers. Each entry contains:
- `ref` (string, required): location of the schema (classpath, file, or URL depending on deployment policy).
- `dialect` (string, optional): JSON Schema dialect.
- `strict` (boolean, optional, default `false`): indicates whether additionalProperties should be forbidden by default.

### FUNCTIONS
Functions map symbolic names to reusable validators.
- `type` (enum: `regex` | `builtin`, required).
- `pattern` (string, required for `regex`): compiled using Java `Pattern`.
- `fn` (string, required for `builtin`): identifier of a registered builtin function.
- `args` (array, optional): parameters supplied to builtin functions.
- `description` (string, optional): human readable explanation.

### VARIABLES
Defines the accepted inputs and derived values. Each entry must describe its shape:
- `type` (enum: `string`, `number`, `integer`, `boolean`, `object`, `array`, required).
- `required` (boolean, optional, default `false`): mark fields the payload must provide.
- `constraints` (array of references, optional): each item points to a function, currently only `$FUNCTIONS.*`.
- `default` (any, optional): fallback value when the field is absent.
- `derive` (object, optional): instructs the engine to compute the value using a builtin (`function`, `args`).
- Additional shape keywords depending on `type`:
  - `string`: `maxLength`, `minLength`, `enum`, `nullable`.
  - `array`: `minItems`, `maxItems`, and `items` definition.
  - `object`: `fields` sub-map and `required` field list.
- Complex values such as arrays or objects may nest definitions following the same rules.

Variables form a closed world contract: payloads may only provide fields listed in this section.

### MAPPINGS
Object keyed by logical mapping names. Each mapping has:
- `REF` (string, required): URN or alias identifying the SAMM concept to populate.
- `MAP` (object or array, required): structure describing how to build the target.

`MAP` can contain:
- Literal values.
- References to variables or other mappings (`$VARIABLES.*`, `$MAPPINGS.*`).
- Nested objects with their own `REF`/`MAP` pairs when arrays of complex types are required.

All references must resolve to previously defined entries to avoid cycles during compilation.

### VALIDATION
Object keyed by validation stage names. Each stage contains:
- `schema` (string reference to `SCHEMA`, required).
- `phase` (enum: `pre-map`, `post-map`, required): determines when the schema runs.

## Reference Resolution Rules

1. `$VARIABLES` references resolve to entries defined in the `VARIABLES` section.
2. `$FUNCTIONS` is only valid inside `constraints` or `derive` instructions.
3. `$MAPPINGS` references point to compiled mapping fragments.
4. References are case-sensitive and must match exactly.
5. Circular references between mappings are invalid.

## Error Handling Guidelines

- Missing required sections or fields should result in descriptive errors listing the JSON path.
- Unknown keys at any level should be rejected to prevent silent typos.
- All regex patterns must compile; failure should halt validation.
- Variable constraints and defaults must be type-compatible.

## Example

See `src/main/resources/config_v2.json` for a canonical example of the DSL in practice.
