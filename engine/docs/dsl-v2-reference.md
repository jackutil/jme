# DSL v2 Formal Reference

This file encodes the v2 grammar in a structured format intended for automated processing.

```json
{
  "dslVersion": "v2",
  "document": {
    "type": "object",
    "required": ["META", "ENGINE", "SCHEMA", "FUNCTIONS", "VARIABLES", "MAPPINGS", "VALIDATION"],
    "additionalProperties": false,
    "properties": {
      "META": { "$ref": "#/definitions/META" },
      "ENGINE": { "$ref": "#/definitions/ENGINE" },
      "SCHEMA": { "$ref": "#/definitions/SCHEMA" },
      "FUNCTIONS": { "$ref": "#/definitions/FUNCTIONS" },
      "VARIABLES": { "$ref": "#/definitions/VARIABLES" },
      "MAPPINGS": { "$ref": "#/definitions/MAPPINGS" },
      "VALIDATION": { "$ref": "#/definitions/VALIDATION" }
    }
  },
  "definitions": {
    "META": {
      "type": "object",
      "required": ["dslVersion", "name", "targetAspect"],
      "additionalProperties": false,
      "properties": {
        "dslVersion": { "type": "string" },
        "name": { "type": "string" },
        "targetAspect": { "type": "string" },
        "description": { "type": "string" },
        "owner": { "type": "string" },
        "lastUpdated": { "type": "string", "format": "date" }
      }
    },
    "ENGINE": {
      "type": "object",
      "required": ["api", "output"],
      "additionalProperties": false,
      "properties": {
        "api": { "type": "string" },
        "output": { "$ref": "#/definitions/Reference" },
        "validation": { "$ref": "#/definitions/Reference" }
      }
    },
    "SCHEMA": {
      "type": "object",
      "minProperties": 1,
      "additionalProperties": false,
      "patternProperties": {
        "^[A-Za-z0-9_.-]+$": {
          "type": "object",
          "required": ["ref"],
          "additionalProperties": false,
          "properties": {
            "ref": { "type": "string" },
            "dialect": { "type": "string" },
            "strict": { "type": "boolean", "default": false }
          }
        }
      }
    },
    "FUNCTIONS": {
      "type": "object",
      "additionalProperties": false,
      "patternProperties": {
        "^[A-Za-z0-9_.-]+$": {
          "type": "object",
          "required": ["type"],
          "properties": {
            "type": { "enum": ["regex", "builtin"] },
            "pattern": { "type": "string" },
            "fn": { "type": "string" },
            "args": { "type": "array" },
            "description": { "type": "string" }
          },
          "allOf": [
            { "if": { "properties": { "type": { "const": "regex" } } }, "then": { "required": ["pattern"] } },
            { "if": { "properties": { "type": { "const": "builtin" } } }, "then": { "required": ["fn"] } }
          ]
        }
      }
    },
    "VARIABLES": {
      "type": "object",
      "additionalProperties": false,
      "patternProperties": {
        "^[A-Za-z0-9_.-]+$": { "$ref": "#/definitions/Variable" }
      }
    },
    "Variable": {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": { "enum": ["string", "number", "integer", "boolean", "object", "array"] },
        "required": { "type": "boolean", "default": false },
        "constraints": { "type": "array", "items": { "$ref": "#/definitions/Reference" } },
        "default": {},
        "derive": {
          "type": "object",
          "required": ["function"],
          "properties": {
            "function": { "type": "string" },
            "args": { "type": "array" }
          },
          "additionalProperties": false
        },
        "description": { "type": "string" },
        "maxLength": { "type": "integer", "minimum": 0 },
        "minLength": { "type": "integer", "minimum": 0 },
        "enum": { "type": "array" },
        "nullable": { "type": "boolean" },
        "minItems": { "type": "integer", "minimum": 0 },
        "maxItems": { "type": "integer", "minimum": 0 },
        "items": { "$ref": "#/definitions/Variable" },
        "fields": {
          "type": "object",
          "additionalProperties": { "$ref": "#/definitions/Variable" }
        },
        "requiredFields": { "type": "array", "items": { "type": "string" } }
      },
      "additionalProperties": false
    },
    "MAPPINGS": {
      "type": "object",
      "additionalProperties": false,
      "patternProperties": {
        "^[A-Za-z0-9_.-]+$": {
          "type": "object",
          "required": ["REF", "MAP"],
          "additionalProperties": false,
          "properties": {
            "REF": { "type": "string" },
            "MAP": { "$ref": "#/definitions/MapValue" }
          }
        }
      }
    },
    "MapValue": {
      "anyOf": [
        { "type": ["string", "number", "integer", "boolean", "null"] },
        {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "REF": { "type": "string" },
            "MAP": { "$ref": "#/definitions/MapValue" }
          }
        },
        {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["REF", "MAP"],
            "additionalProperties": false,
            "properties": {
              "REF": { "type": "string" },
              "MAP": { "$ref": "#/definitions/MapValue" }
            }
          }
        }
      ]
    },
    "VALIDATION": {
      "type": "object",
      "additionalProperties": false,
      "patternProperties": {
        "^[A-Za-z0-9_.-]+$": {
          "type": "object",
          "required": ["schema", "phase"],
          "additionalProperties": false,
          "properties": {
            "schema": { "$ref": "#/definitions/Reference" },
            "phase": { "enum": ["pre-map", "post-map"] }
          }
        }
      }
    },
    "Reference": {
      "type": "string",
      "pattern": "^\$?(META|ENGINE|SCHEMA|FUNCTIONS|VARIABLES|MAPPINGS|VALIDATION)(\.[A-Za-z0-9_.-]+)+$"
    }
  }
}
```

Notes:
- `requiredFields` inside `Variable` corresponds to the human-oriented `required` array for object members.
- Reference pattern enforces explicit namespace prefixes.
- Validators must reject unknown keys to maintain forward compatibility.
