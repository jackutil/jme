package github.jackutil.compiler.ir.resolved;

import java.util.List;

public sealed interface ResolvedMapNode permits ResolvedMapNode.LiteralNode,
        ResolvedMapNode.VariableRefNode,
        ResolvedMapNode.InputRefNode,
        ResolvedMapNode.MappingRefNode,
        ResolvedMapNode.ObjectNode,
        ResolvedMapNode.ArrayNode {

    record LiteralNode(Object value) implements ResolvedMapNode {}

    record VariableRefNode(int variableId) implements ResolvedMapNode {}

    record InputRefNode(int inputId) implements ResolvedMapNode {}

    record MappingRefNode(int mappingId) implements ResolvedMapNode {}

    record ObjectNode(List<Field> fields) implements ResolvedMapNode {
        public record Field(String name, ResolvedMapNode value) {}
    }

    record ArrayNode(List<ResolvedMapNode> elements) implements ResolvedMapNode {}
}
