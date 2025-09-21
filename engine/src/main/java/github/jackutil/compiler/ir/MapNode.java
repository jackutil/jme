package github.jackutil.compiler.ir;

import java.util.List;

/**
 * Represents transformations inside a mapping definition.
 */
public sealed interface MapNode permits MapNode.LiteralNode,
        MapNode.VariableRefNode,
        MapNode.InputRefNode,
        MapNode.MappingRefNode,
        MapNode.ObjectNode,
        MapNode.ArrayNode {

    record LiteralNode(Object value) implements MapNode {}

    record VariableRefNode(String reference) implements MapNode {}

    record InputRefNode(String reference) implements MapNode {}

    record MappingRefNode(String reference) implements MapNode {}

    record ObjectNode(List<Field> fields) implements MapNode {
        public record Field(String name, MapNode value) {}
    }

    record ArrayNode(List<MapNode> elements) implements MapNode {}
}
