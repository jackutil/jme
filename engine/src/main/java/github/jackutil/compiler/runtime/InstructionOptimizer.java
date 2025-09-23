package github.jackutil.compiler.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import github.jackutil.compiler.ir.EngineConfig;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedMapNode;
import github.jackutil.compiler.ir.resolved.ResolvedMapping;

/**
 * Performs simple IR optimizations before bytecode emission to reduce runtime work.
 */
public final class InstructionOptimizer {
    private static final int INLINE_MAX_REFERENCE_COUNT = 1;
    private static final int INLINE_MAX_NODE_COUNT = 128;

    private InstructionOptimizer() {
    }

    public static ResolvedConfig optimize(ResolvedConfig config) {
        return new Optimizer(config).optimize();
    }

    private static final class Optimizer {
        private final ResolvedConfig config;
        private final Map<Integer, ResolvedMapping> mappingById = new HashMap<>();
        private final Map<String, Integer> mappingIdByName = new HashMap<>();
        private final Map<Integer, Integer> referenceCounts = new HashMap<>();
        private final Map<Integer, OptimizationResult> optimizedMappings = new HashMap<>();
        private final Set<Integer> inProgress = new HashSet<>();
        private final Map<Object, ResolvedMapNode.LiteralNode> literalPool = new HashMap<>();
        private final Set<Integer> protectedMappings = new HashSet<>();

        Optimizer(ResolvedConfig config) {
            this.config = config;
            for (ResolvedMapping mapping : config.mappings()) {
                mappingById.put(mapping.id(), mapping);
                mappingIdByName.put(mapping.name(), mapping.id());
                referenceCounts.put(mapping.id(), 0);
            }
            for (ResolvedMapping mapping : config.mappings()) {
                countReferences(mapping.root());
            }
            initProtectedMappings();
        }

        ResolvedConfig optimize() {
            List<ResolvedMapping> optimized = new ArrayList<>(config.mappings().size());
            for (ResolvedMapping mapping : config.mappings()) {
                OptimizationResult result = optimizeMapping(mapping.id());
                optimized.add(new ResolvedMapping(mapping.id(), mapping.name(), mapping.ref(), result.node()));
            }
            return new ResolvedConfig(
                config.meta(),
                config.engine(),
                config.schemas(),
                config.functions(),
                config.inputs(),
                config.variables(),
                List.copyOf(optimized),
                config.validations()
            );
        }

        private void countReferences(ResolvedMapNode node) {
            if (node instanceof ResolvedMapNode.MappingRefNode refNode) {
                referenceCounts.merge(refNode.mappingId(), 1, Integer::sum);
                return;
            }
            if (node instanceof ResolvedMapNode.ObjectNode objectNode) {
                for (ResolvedMapNode.ObjectNode.Field field : objectNode.fields()) {
                    countReferences(field.value());
                }
                return;
            }
            if (node instanceof ResolvedMapNode.ArrayNode arrayNode) {
                for (ResolvedMapNode element : arrayNode.elements()) {
                    countReferences(element);
                }
            }
        }

        private void initProtectedMappings() {
            EngineConfig engineConfig = config.engine();
            if (engineConfig == null) {
                return;
            }
            registerProtectedMapping(engineConfig.outputRef());
        }

        private void registerProtectedMapping(String pointer) {
            if (pointer == null || pointer.isBlank()) {
                return;
            }
            String mappingName = stripPrefix(pointer.trim(), "$MAPPINGS.");
            Integer id = mappingIdByName.get(mappingName);
            if (id != null) {
                protectedMappings.add(id);
            }
        }

        private String stripPrefix(String value, String prefix) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
            return value;
        }

        private OptimizationResult optimizeMapping(int mappingId) {
            OptimizationResult cached = optimizedMappings.get(mappingId);
            if (cached != null) {
                return cached;
            }
            if (inProgress.contains(mappingId)) {
                // Cycle detected; bail out and keep original structure.
                ResolvedMapping mapping = Objects.requireNonNull(mappingById.get(mappingId));
                OptimizationResult result = OptimizationResult.nonConstant(mapping.root());
                optimizedMappings.put(mappingId, result);
                return result;
            }
            ResolvedMapping mapping = Objects.requireNonNull(mappingById.get(mappingId),
                () -> "Unknown mapping id " + mappingId);
            inProgress.add(mappingId);
            OptimizationResult result = optimizeNode(mapping.root());
            inProgress.remove(mappingId);
            optimizedMappings.put(mappingId, result);
            return result;
        }

        private OptimizationResult optimizeNode(ResolvedMapNode node) {
            if (node instanceof ResolvedMapNode.LiteralNode literalNode) {
                return constantLiteral(literalNode.value());
            }
            if (node instanceof ResolvedMapNode.VariableRefNode) {
                return OptimizationResult.nonConstant(node);
            }
            if (node instanceof ResolvedMapNode.MappingRefNode mappingRefNode) {
                int targetId = mappingRefNode.mappingId();
                OptimizationResult target = optimizeMapping(targetId);
                int refs = referenceCounts.getOrDefault(targetId, 0);
                if (target.isConstant() && refs == 1) {
                    // Safe to inline a constant mapping referenced only once.
                    return target;
                }
                if (shouldInlineMapping(targetId, target.node(), refs)) {
                    return OptimizationResult.nonConstant(cloneNode(target.node()));
                }
                return OptimizationResult.nonConstant(node);
            }
            if (node instanceof ResolvedMapNode.InputRefNode) {
                return OptimizationResult.nonConstant(node);
            }
            if (node instanceof ResolvedMapNode.ObjectNode objectNode) {
                return optimizeObject(objectNode);
            }
            if (node instanceof ResolvedMapNode.ArrayNode arrayNode) {
                return optimizeArray(arrayNode);
            }
            return OptimizationResult.nonConstant(node);
        }

        private boolean shouldInlineMapping(int mappingId, ResolvedMapNode targetNode, int references) {
            if (protectedMappings.contains(mappingId)) {
                return false;
            }
            if (references > INLINE_MAX_REFERENCE_COUNT) {
                return false;
            }
            if (targetNode instanceof ResolvedMapNode.MappingRefNode) {
                return false;
            }
            return nodeSize(targetNode) <= INLINE_MAX_NODE_COUNT;
        }

        private OptimizationResult optimizeObject(ResolvedMapNode.ObjectNode objectNode) {
            List<ResolvedMapNode.ObjectNode.Field> fields = objectNode.fields();
            List<ResolvedMapNode.ObjectNode.Field> optimizedFields = new ArrayList<>(fields.size());
            boolean changed = false;
            boolean allConstant = true;
            List<Object> constants = new ArrayList<>(fields.size());
            for (ResolvedMapNode.ObjectNode.Field field : fields) {
                OptimizationResult result = optimizeNode(field.value());
                ResolvedMapNode valueNode = result.node();
                optimizedFields.add(new ResolvedMapNode.ObjectNode.Field(field.name(), valueNode));
                if (valueNode != field.value()) {
                    changed = true;
                }
                if (result.isConstant()) {
                    constants.add(result.constantValue());
                } else {
                    allConstant = false;
                }
            }
            if (allConstant) {
                LinkedHashMap<String, Object> folded = new LinkedHashMap<>();
                for (int i = 0; i < fields.size(); i++) {
                    folded.put(fields.get(i).name(), constants.get(i));
                }
                return constantLiteral(Collections.unmodifiableMap(folded));
            }
            if (!changed) {
                return OptimizationResult.nonConstant(objectNode);
            }
            return OptimizationResult.nonConstant(new ResolvedMapNode.ObjectNode(List.copyOf(optimizedFields)));
        }

        private OptimizationResult optimizeArray(ResolvedMapNode.ArrayNode arrayNode) {
            List<ResolvedMapNode> elements = arrayNode.elements();
            List<ResolvedMapNode> optimizedElements = new ArrayList<>(elements.size());
            boolean changed = false;
            boolean allConstant = true;
            List<Object> constants = new ArrayList<>(elements.size());
            for (ResolvedMapNode element : elements) {
                OptimizationResult result = optimizeNode(element);
                ResolvedMapNode node = result.node();
                optimizedElements.add(node);
                if (node != element) {
                    changed = true;
                }
                if (result.isConstant()) {
                    constants.add(result.constantValue());
                } else {
                    allConstant = false;
                }
            }
            if (allConstant) {
                return constantLiteral(List.copyOf(constants));
            }
            if (!changed) {
                return OptimizationResult.nonConstant(arrayNode);
            }
            return OptimizationResult.nonConstant(new ResolvedMapNode.ArrayNode(List.copyOf(optimizedElements)));
        }

        private OptimizationResult constantLiteral(Object value) {
            Object canonical = canonicalizeValue(value);
            ResolvedMapNode.LiteralNode literal = literalPool.computeIfAbsent(canonical, key -> new ResolvedMapNode.LiteralNode(key));
            return OptimizationResult.constant(literal, canonical);
        }

        private Object canonicalizeValue(Object value) {
            if (value instanceof List<?> list) {
                List<Object> canonical = new ArrayList<>(list.size());
                for (Object element : list) {
                    canonical.add(canonicalizeValue(element));
                }
                return List.copyOf(canonical);
            }
            if (value instanceof Map<?, ?> map) {
                LinkedHashMap<Object, Object> canonical = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    canonical.put(entry.getKey(), canonicalizeValue(entry.getValue()));
                }
                return Collections.unmodifiableMap(canonical);
            }
            return value;
        }

        private ResolvedMapNode cloneNode(ResolvedMapNode node) {
            if (node instanceof ResolvedMapNode.ObjectNode objectNode) {
                List<ResolvedMapNode.ObjectNode.Field> clonedFields = new ArrayList<>(objectNode.fields().size());
                for (ResolvedMapNode.ObjectNode.Field field : objectNode.fields()) {
                    clonedFields.add(new ResolvedMapNode.ObjectNode.Field(field.name(), cloneNode(field.value())));
                }
                return new ResolvedMapNode.ObjectNode(List.copyOf(clonedFields));
            }
            if (node instanceof ResolvedMapNode.ArrayNode arrayNode) {
                List<ResolvedMapNode> clonedElements = new ArrayList<>(arrayNode.elements().size());
                for (ResolvedMapNode element : arrayNode.elements()) {
                    clonedElements.add(cloneNode(element));
                }
                return new ResolvedMapNode.ArrayNode(List.copyOf(clonedElements));
            }
            if (node instanceof ResolvedMapNode.VariableRefNode variableRefNode) {
                return new ResolvedMapNode.VariableRefNode(variableRefNode.variableId());
            }
            if (node instanceof ResolvedMapNode.InputRefNode inputRefNode) {
                return new ResolvedMapNode.InputRefNode(inputRefNode.inputId());
            }
            if (node instanceof ResolvedMapNode.MappingRefNode mappingRefNode) {
                return new ResolvedMapNode.MappingRefNode(mappingRefNode.mappingId());
            }
            return node;
        }

        private int nodeSize(ResolvedMapNode node) {
            if (node instanceof ResolvedMapNode.ObjectNode objectNode) {
                int size = 1;
                for (ResolvedMapNode.ObjectNode.Field field : objectNode.fields()) {
                    size += nodeSize(field.value());
                }
                return size;
            }
            if (node instanceof ResolvedMapNode.ArrayNode arrayNode) {
                int size = 1;
                for (ResolvedMapNode element : arrayNode.elements()) {
                    size += nodeSize(element);
                }
                return size;
            }
            return 1;
        }
    }

    private static final class OptimizationResult {
        private final ResolvedMapNode node;
        private final boolean constant;
        private final Object constantValue;

        private OptimizationResult(ResolvedMapNode node, boolean constant, Object constantValue) {
            this.node = node;
            this.constant = constant;
            this.constantValue = constantValue;
        }

        static OptimizationResult constant(ResolvedMapNode node, Object constantValue) {
            return new OptimizationResult(node, true, constantValue);
        }

        static OptimizationResult nonConstant(ResolvedMapNode node) {
            return new OptimizationResult(node, false, null);
        }

        ResolvedMapNode node() {
            return node;
        }

        boolean isConstant() {
            return constant;
        }

        Object constantValue() {
            return constantValue;
        }
    }
}
