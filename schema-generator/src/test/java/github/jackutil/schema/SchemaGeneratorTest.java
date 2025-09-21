package github.jackutil.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SchemaGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SchemaGenerator generator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        generator = new SchemaGenerator(mapper);
    }

    @Test
    void generatesSchemaFromVariables() throws Exception {
        Path configPath = writeSampleConfig(tempDir.resolve("sample-config.json"));

        ObjectNode schema = generator.generate(configPath);

        assertThat(schema.path("$schema").asText()).isEqualTo("http://json-schema.org/draft-07/schema#");
        assertThat(schema.path("title").asText()).isEqualTo("sample.mapping");
        assertThat(schema.path("description").asText()).isEqualTo("Sample mapping for tests");
        assertThat(schema.path("x-targetAspect").asText()).isEqualTo("urn:test:aspect");

        JsonNode properties = schema.path("properties");
        assertThat(properties.isObject()).isTrue();
        assertThat(((ObjectNode) properties).fieldNames()).toIterable()
            .contains("catenaXId", "status", "shipment", "lineItems");

        assertThat(asTextList(schema.path("required"))).containsExactly("catenaXId");

        JsonNode idSchema = properties.path("catenaXId");
        assertThat(idSchema.path("type").asText()).isEqualTo("string");
        assertThat(idSchema.path("pattern").asText()).isEqualTo("^[A-Z0-9]+$");
        assertThat(idSchema.path("description").asText()).isEqualTo("Identifier");

        JsonNode statusSchema = properties.path("status");
        assertThat(asTextList(statusSchema.path("type"))).containsExactlyInAnyOrder("string", "null");
        assertThat(asTextList(statusSchema.path("enum"))).containsExactlyInAnyOrder("draft", "released", null);
        assertThat(statusSchema.path("default").asText()).isEqualTo("draft");

        JsonNode shipmentSchema = properties.path("shipment");
        assertThat(asTextList(shipmentSchema.path("required"))).containsExactly("quantity");
        assertThat(shipmentSchema.path("additionalProperties").asBoolean()).isFalse();
        JsonNode shipmentFields = shipmentSchema.path("properties");
        assertThat(shipmentFields.path("deliveryDate").path("readOnly").asBoolean()).isTrue();
        assertThat(asTextList(shipmentFields.path("quantity").path("x-constraints"))).contains("$FUNCTIONS.nonEmpty");

        JsonNode lineItemsSchema = properties.path("lineItems");
        assertThat(lineItemsSchema.path("minItems").asInt()).isEqualTo(1);
        JsonNode itemSchema = lineItemsSchema.path("items");
        JsonNode itemFields = itemSchema.path("properties");
        assertThat(itemFields.path("sku").path("pattern").asText()).isEqualTo("^[A-Z0-9]+$");
        assertThat(asTextList(itemSchema.path("required"))).containsExactly("sku");
    }

    @Test
    void failsWhenVariableTypeMissing() throws Exception {
        Path configPath = writeConfigWithMissingType(tempDir.resolve("invalid-config.json"));

        assertThatThrownBy(() -> generator.generate(configPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing required field type");
    }

    private Path writeSampleConfig(Path path) throws IOException {
        ObjectNode root = createBaseConfig();
        ObjectNode functions = (ObjectNode) root.get("FUNCTIONS");
        ObjectNode regex = functions.putObject("alphaNumericUpper");
        regex.put("type", "regex");
        regex.put("pattern", "^[A-Z0-9]+$");
        ObjectNode builtin = functions.putObject("nonEmpty");
        builtin.put("type", "builtin");
        builtin.put("fn", "nonEmpty");

        ObjectNode variables = root.putObject("VARIABLES");

        ObjectNode id = variables.putObject("catenaXId");
        id.put("type", "string");
        id.put("required", true);
        id.put("description", "Identifier");
        id.putArray("constraints").add("$FUNCTIONS.alphaNumericUpper");

        ObjectNode status = variables.putObject("status");
        status.put("type", "string");
        status.put("nullable", true);
        status.putArray("enum").add("draft").add("released");
        status.put("default", "draft");

        ObjectNode shipment = variables.putObject("shipment");
        shipment.put("type", "object");
        ObjectNode shipmentFields = shipment.putObject("fields");
        ObjectNode quantity = shipmentFields.putObject("quantity");
        quantity.put("type", "integer");
        quantity.putArray("constraints").add("$FUNCTIONS.nonEmpty");
        ObjectNode deliveryDate = shipmentFields.putObject("deliveryDate");
        deliveryDate.put("type", "string");
        deliveryDate.putObject("derive").put("function", "now");
        shipment.putArray("requiredFields").add("quantity");

        ObjectNode lineItems = variables.putObject("lineItems");
        lineItems.put("type", "array");
        lineItems.put("minItems", 1);
        ObjectNode item = lineItems.putObject("items");
        item.put("type", "object");
        ObjectNode itemFields = item.putObject("fields");
        ObjectNode sku = itemFields.putObject("sku");
        sku.put("type", "string");
        sku.putArray("constraints").add("$FUNCTIONS.alphaNumericUpper");
        ObjectNode amount = itemFields.putObject("amount");
        amount.put("type", "number");
        item.putArray("requiredFields").add("sku");

        return writeConfig(path, root);
    }

    private Path writeConfigWithMissingType(Path path) throws IOException {
        ObjectNode root = createBaseConfig();
        ObjectNode functions = (ObjectNode) root.get("FUNCTIONS");
        functions.putObject("noop").put("type", "builtin").put("fn", "noop");

        ObjectNode variables = root.putObject("VARIABLES");
        ObjectNode invalid = variables.putObject("catenaXId");
        invalid.put("required", true);

        return writeConfig(path, root);
    }

    private ObjectNode createBaseConfig() {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode meta = root.putObject("META");
        meta.put("dslVersion", "v2");
        meta.put("name", "sample.mapping");
        meta.put("targetAspect", "urn:test:aspect");
        meta.put("description", "Sample mapping for tests");

        ObjectNode engine = root.putObject("ENGINE");
        engine.put("api", "v2");
        engine.put("output", "$MAPPINGS.root");
        engine.put("validation", "$SCHEMA.root");

        root.putObject("INPUT");

        ObjectNode schema = root.putObject("SCHEMA");
        ObjectNode schemaEntry = schema.putObject("root");
        schemaEntry.put("ref", "classpath:schemas/root.json");

        root.putObject("FUNCTIONS");

        ObjectNode mappings = root.putObject("MAPPINGS");
        ObjectNode rootMapping = mappings.putObject("root");
        rootMapping.put("REF", "urn:test:root");
        ObjectNode map = rootMapping.putObject("MAP");
        map.put("id", "$VARIABLES.catenaXId");

        ObjectNode validation = root.putObject("VALIDATION");
        ObjectNode validationEntry = validation.putObject("root");
        validationEntry.put("schema", "$SCHEMA.root");
        validationEntry.put("phase", "post-map");

        return root;
    }

    private Path writeConfig(Path path, ObjectNode root) throws IOException {
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
        return path;
    }

    private List<String> asTextList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.isNull() ? null : value.asText()));
        return values;
    }
}
