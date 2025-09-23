package github.jackutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import github.jackutil.compiler.diagnostics.MappingException;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.ConfigCompiler;
import github.jackutil.compiler.runtime.MappingEngine;

public class MappingEngineTest {

    private final JsonFactory jsonFactory = new JsonFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void executesSimpleMapping() throws Exception {
        CompiledMapping compiled = compile("valid/refs.json");
        MappingEngine engine = new MappingEngine(compiled);
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = jsonFactory.createGenerator(writer)) {
            engine.execute("root", Map.of(), Map.of("id", "ABC"), generator);
        }
        assertEquals("{\"id\":\"ABC\"}", writer.toString());
    }

    @Test
    public void executesBuiltinsMapping() throws Exception {
        CompiledMapping compiled = compile("valid/builtins.json");
        MappingEngine engine = new MappingEngine(compiled);
        JsonNode node = executeToJson(engine, "root", Map.of());
        String generatedId = node.get("generatedId").asText();
        assertTrue(Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matcher(generatedId).matches());
        assertEquals("00042", node.get("padded").asText());
        assertEquals("pre-value", node.get("label").asText());
        assertEquals("Blue", node.get("color").asText());
        assertEquals(6, node.get("sum").asInt());
    }

    @Test
    public void executesArrayMapping() throws Exception {
        CompiledMapping compiled = compile("valid/arrays.json");
        MappingEngine engine = new MappingEngine(compiled);
        JsonNode node = executeToJson(engine, "root", Map.of());
        assertEquals("BOX", node.get("label").asText());
        assertEquals(2, node.get("items").size());
        assertEquals("foo", node.get("items").get(0).get("name").asText());
    }

    @Test
    public void executesAdvancedOrderMapping() throws Exception {
        CompiledMapping compiled = compile("valid/advanced-order.json");
        MappingEngine engine = new MappingEngine(compiled);
        Map<String, Object> inputs = readJsonMap("valid/advanced-order-input.json");
        Map<String, Object> payload = readJsonMap("valid/advanced-order-payload.json");
        JsonNode actual = executeToJson(engine, "root", inputs, payload);
        JsonNode expected = readJsonNode("valid/advanced-order-expected.json");
        assertEquals(expected, actual);
    }

    @Test
    public void throwsWhenRequiredVariableMissing() throws Exception {
        CompiledMapping compiled = compile("valid/refs.json");
        MappingEngine engine = new MappingEngine(compiled);
        try (JsonGenerator generator = jsonFactory.createGenerator(new StringWriter())) {
            assertThrows(MappingException.class, () -> engine.execute("root", Map.of(), Map.of(), generator));
        }
    }

    @Test
    public void throwsWhenConstraintFails() throws Exception {
        CompiledMapping compiled = compile("valid/refs.json");
        MappingEngine engine = new MappingEngine(compiled);
        try (JsonGenerator generator = jsonFactory.createGenerator(new StringWriter())) {
            assertThrows(MappingException.class, () -> engine.execute("root", Map.of(), Map.of("id", "abc"), generator));
        }
    }

    @Test
    public void throwsWhenBuiltinDerivationInvalid() throws Exception {
        CompiledMapping compiled = compile("invalid/runtime-builtin.json");
        MappingEngine engine = new MappingEngine(compiled);
        try (JsonGenerator generator = jsonFactory.createGenerator(new StringWriter())) {
            assertThrows(MappingException.class, () -> engine.execute("root", Map.of(), Map.of(), generator));
        }
    }

    @Test
    public void throwsForUnknownMappingName() throws Exception {
        CompiledMapping compiled = compile("valid/refs.json");
        MappingEngine engine = new MappingEngine(compiled);
        try (JsonGenerator generator = jsonFactory.createGenerator(new StringWriter())) {
            assertThrows(MappingException.class, () -> engine.execute("missing", Map.of(), Map.of("id", "ABC"), generator));
        }
    }

    @Test
    public void injectsInputValues() throws Exception {
        CompiledMapping compiled = compile("valid/inputs.json");
        MappingEngine engine = new MappingEngine(compiled);
        JsonNode node = executeToJson(engine, "root", Map.of("tenantId", "TENANT-001"), Map.of());
        assertEquals("TENANT-001", node.get("tenant").asText());
        assertEquals("2024-05-01", node.get("batchDate").asText());
    }

    @Test
    public void throwsWhenRequiredInputMissing() throws Exception {
        CompiledMapping compiled = compile("valid/inputs.json");
        MappingEngine engine = new MappingEngine(compiled);
        try (JsonGenerator generator = jsonFactory.createGenerator(new StringWriter())) {
            assertThrows(MappingException.class, () -> engine.execute("root", Map.of(), Map.of(), generator));
        }
    }
    private JsonNode executeToJson(MappingEngine engine, String mapping, Map<String, Object> inputs, Map<String, Object> payload) throws Exception {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = jsonFactory.createGenerator(writer)) {
            engine.execute(mapping, inputs, payload, generator);
        }
        return objectMapper.readTree(writer.toString());
    }

    private JsonNode executeToJson(MappingEngine engine, String mapping, Map<String, Object> payload) throws Exception {
        return executeToJson(engine, mapping, Map.of(), payload);
    }

    private Map<String, Object> readJsonMap(String resourceName) throws Exception {
        try (InputStream in = resource(resourceName)) {
            return objectMapper.readValue(in, new TypeReference<Map<String, Object>>() { });
        }
    }

    private JsonNode readJsonNode(String resourceName) throws Exception {
        try (InputStream in = resource(resourceName)) {
            return objectMapper.readTree(in);
        }
    }

    private CompiledMapping compile(String resource) throws Exception {
        try (InputStream in = resource(resource)) {
            return ConfigCompiler.compile(in);
        }
    }

    private InputStream resource(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw MappingException.of("TEST_RESOURCE_MISSING", "Missing test resource: " + name, "/test");
        }
        return stream;
    }
}









