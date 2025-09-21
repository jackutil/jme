package github.jackutil.bench;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.ConfigCompiler;
import github.jackutil.compiler.runtime.MappingEngine;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class MappingEngineBenchmark {

    @State(Scope.Benchmark)
    public static class EngineState {
        private static final OutputStream NULL_OUTPUT = new OutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public void write(byte[] b, int off, int len) {
            }

            @Override
            public void close() {
            }
        };

        private final JsonFactory factory = new JsonFactory();

        @Param({"refs", "arrays", "builtins", "constants"})
        public String fixture;

        MappingEngine engine;
        Map<String, Object> inputs;
        String mappingName;
        Map<String, Object> payload;
        @Setup(Level.Trial)
        public void setup() throws Exception {
            switch (fixture) {
                case "refs" -> init(REFS_MAPPING, "root", Map.of(), Map.of("id", "ABC"));
                case "arrays" -> init(ARRAYS_MAPPING, "root", Map.of(), Map.of());
                case "builtins" -> init(BUILTINS_MAPPING, "root", Map.of(), Map.of());
                case "constants" -> init(CONSTANTS_MAPPING, "root", Map.of(), Map.of());
                default -> throw new IllegalArgumentException("Unknown fixture: " + fixture);
            }
        }

        private void init(String json, String mappingName, Map<String, Object> inputs, Map<String, Object> payload) throws Exception {
            this.engine = new MappingEngine(compile(json));
            this.mappingName = mappingName;
            this.inputs = inputs;
            this.payload = payload;
        }

        MappingEngine engine() {
            return engine;
        }

        JsonGenerator newGenerator() throws IOException {
            return factory.createGenerator(NULL_OUTPUT);
        }

        private CompiledMapping compile(String json) throws Exception {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                return ConfigCompiler.compile(in);
            }
        }
    }

    @Benchmark
    public void execute(EngineState state) throws IOException {
        JsonGenerator generator = state.newGenerator();
        try {
            state.engine().execute(state.mappingName, state.inputs, state.payload, generator);
        } catch (IOException e) {
            throw e;
        } finally {
            generator.close();
        }
    }

    private static final String REFS_MAPPING = """
            {
              "META": {
                "dslVersion": "v2",
                "name": "test",
                "targetAspect": "urn:test"
              },
              "ENGINE": {
                "api": "v2",
                "output": "$MAPPINGS.root"
              },
              "INPUT": {},
              "SCHEMA": {
                "batch": { "ref": "classpath:schema/batch.json" }
              },
              "FUNCTIONS": {
                "uppercase": {
                  "type": "regex",
                  "pattern": "^[A-Z]+$"
                }
              },
              "VARIABLES": {
                "id": {
                  "type": "string",
                  "required": true,
                  "constraints": ["$FUNCTIONS.uppercase"]
                }
              },
              "MAPPINGS": {
                "root": {
                  "REF": "root",
                  "MAP": {
                    "id": "$VARIABLES.id"
                  }
                }
              },
              "VALIDATION": {
                "post": {
                  "schema": "$SCHEMA.batch",
                  "phase": "post-map"
                }
              }
            }
            """;

    private static final String ARRAYS_MAPPING = """
            {
              "META": {
                "dslVersion": "v2",
                "name": "arrays",
                "targetAspect": "urn:test:arrays"
              },
              "ENGINE": {
                "api": "v2",
                "output": "$MAPPINGS.root"
              },
              "INPUT": {},
              "SCHEMA": {},
              "FUNCTIONS": {
                "upper": { "type": "builtin", "fn": "uppercase" }
              },
              "VARIABLES": {
                "label": {
                  "type": "string",
                  "derive": { "function": "$FUNCTIONS.upper", "args": [ "box" ] }
                },
                "items": {
                  "type": "array",
                  "default": [
                    { "name": "foo", "qty": 1 },
                    { "name": "bar", "qty": 2 }
                  ]
                }
              },
              "MAPPINGS": {
                "root": {
                  "REF": "root",
                  "MAP": {
                    "label": "$VARIABLES.label",
                    "items": "$VARIABLES.items"
                  }
                }
              },
              "VALIDATION": {}
            }
            """;

    private static final String BUILTINS_MAPPING = """
            {
              "META": {
                "dslVersion": "v2",
                "name": "builtins",
                "targetAspect": "urn:test:builtins"
              },
              "ENGINE": {
                "api": "v2",
                "output": "$MAPPINGS.root"
              },
              "INPUT": {},
              "SCHEMA": {},
              "FUNCTIONS": {
                "uuidFn": { "type": "builtin", "fn": "uuid" },
                "padLeft": { "type": "builtin", "fn": "pad_left" },
                "concat": { "type": "builtin", "fn": "concat" },
                "lookupColor": {
                  "type": "builtin",
                  "fn": "lookup",
                  "args": [ { "A": "Amber", "B": "Blue" }, "Unknown" ]
                },
                "adder": { "type": "builtin", "fn": "add" }
              },
              "VARIABLES": {
                "generatedId": {
                  "type": "string",
                  "derive": { "function": "$FUNCTIONS.uuidFn" }
                },
                "padded": {
                  "type": "string",
                  "derive": { "function": "$FUNCTIONS.padLeft", "args": [ "42", 5, "0" ] }
                },
                "label": {
                  "type": "string",
                  "derive": { "function": "$FUNCTIONS.concat", "args": [ "pre", "-", "value" ] }
                },
                "color": {
                  "type": "string",
                  "derive": { "function": "$FUNCTIONS.lookupColor", "args": [ "B" ] }
                },
                "sum": {
                  "type": "number",
                  "derive": { "function": "$FUNCTIONS.adder", "args": [ 1, 2, 3 ] }
                }
              },
              "MAPPINGS": {
                "root": {
                  "REF": "root",
                  "MAP": {
                    "generatedId": "$VARIABLES.generatedId",
                    "padded": "$VARIABLES.padded",
                    "label": "$VARIABLES.label",
                    "color": "$VARIABLES.color",
                    "sum": "$VARIABLES.sum"
                  }
                }
              },
              "VALIDATION": {}
            }
            """;

    private static final String CONSTANTS_MAPPING = """
            {
              "META": {
                "dslVersion": "v2",
                "name": "constants",
                "targetAspect": "urn:test:constants"
              },
              "ENGINE": {
                "api": "v2",
                "output": "$MAPPINGS.root"
              },
              "INPUT": {},
              "SCHEMA": {},
              "FUNCTIONS": {},
              "VARIABLES": {},
              "MAPPINGS": {
                "root": {
                  "REF": "root",
                  "MAP": {
                    "staticObject": {
                      "name": "inline",
                      "data": [1, 2, 3]
                    },
                    "inlineRef": "$MAPPINGS.singleUse",
                    "sharedOne": "$MAPPINGS.sharedConst",
                    "sharedTwo": "$MAPPINGS.sharedConst"
                  }
                },
                "singleUse": {
                  "REF": "singleUse",
                  "MAP": {
                    "name": "inline",
                    "data": [1, 2, 3]
                  }
                },
                "sharedConst": {
                  "REF": "sharedConst",
                  "MAP": {
                    "name": "shared",
                    "data": [true, false]
                  }
                }
              },
              "VALIDATION": {}
            }
            """;
}




