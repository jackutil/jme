package github.jackutil.compiler.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedMapNode;
import github.jackutil.compiler.ir.resolved.ResolvedMapping;

/**
 * Pools literal nodes across all mappings so identical values reuse a single node instance.
 */
final class LiteralPooler {
    private LiteralPooler() {
    }

    static ResolvedConfig poolLiterals(ResolvedConfig config) {
        return new Pooler(config).pool();
    }

    private static final class Pooler {
        private final ResolvedConfig config;
        private final Map<Object, ResolvedMapNode.LiteralNode> pool = new HashMap<>();
        private final Map<Object, Object> canonicalValues = new HashMap<>();

        Pooler(ResolvedConfig config) {
            this.config = config;
        }

        ResolvedConfig pool() {
            List<ResolvedMapping> pooledMappings = new ArrayList<>(config.mappings().size());
            for (ResolvedMapping mapping : config.mappings()) {
                ResolvedMapNode pooledRoot = poolNode(mapping.root());
                pooledMappings.add(new ResolvedMapping(mapping.id(), mapping.name(), mapping.ref(), pooledRoot));
            }
            return new ResolvedConfig(
                config.meta(),
                config.engine(),
                config.schemas(),
                config.functions(),
                config.inputs(),
                config.variables(),
                List.copyOf(pooledMappings),
                config.validations()
            );
        }

        private ResolvedMapNode poolNode(ResolvedMapNode node) {
            if (node instanceof ResolvedMapNode.LiteralNode literalNode) {
                return poolLiteral(literalNode);
            }
            if (node instanceof ResolvedMapNode.ObjectNode objectNode) {
                return poolObject(objectNode);
            }
            if (node instanceof ResolvedMapNode.ArrayNode arrayNode) {
                return poolArray(arrayNode);
            }
            return node;
        }

        private ResolvedMapNode poolObject(ResolvedMapNode.ObjectNode objectNode) {
            List<ResolvedMapNode.ObjectNode.Field> fields = objectNode.fields();
            List<ResolvedMapNode.ObjectNode.Field> pooledFields = new ArrayList<>(fields.size());
            boolean changed = false;
            for (ResolvedMapNode.ObjectNode.Field field : fields) {
                ResolvedMapNode pooledValue = poolNode(field.value());
                pooledFields.add(new ResolvedMapNode.ObjectNode.Field(field.name(), pooledValue));
                if (pooledValue != field.value()) {
                    changed = true;
                }
            }
            if (!changed) {
                return objectNode;
            }
            return new ResolvedMapNode.ObjectNode(List.copyOf(pooledFields));
        }

        private ResolvedMapNode poolArray(ResolvedMapNode.ArrayNode arrayNode) {
            List<ResolvedMapNode> elements = arrayNode.elements();
            List<ResolvedMapNode> pooledElements = new ArrayList<>(elements.size());
            boolean changed = false;
            for (ResolvedMapNode element : elements) {
                ResolvedMapNode pooledElement = poolNode(element);
                pooledElements.add(pooledElement);
                if (pooledElement != element) {
                    changed = true;
                }
            }
            if (!changed) {
                return arrayNode;
            }
            return new ResolvedMapNode.ArrayNode(List.copyOf(pooledElements));
        }

        private ResolvedMapNode poolLiteral(ResolvedMapNode.LiteralNode literalNode) {
            Object canonical = canonicalizeValue(literalNode.value());
            ResolvedMapNode.LiteralNode pooled = pool.get(canonical);
            if (pooled != null) {
                return pooled;
            }
            ResolvedMapNode.LiteralNode candidate = literalNode.value() == canonical
                ? literalNode
                : new ResolvedMapNode.LiteralNode(canonical);
            pool.put(canonical, candidate);
            return candidate;
        }

        private Object canonicalizeValue(Object value) {
            if (value instanceof List<?> list) {
                List<Object> canonical = new ArrayList<>(list.size());
                for (Object element : list) {
                    canonical.add(canonicalizeValue(element));
                }
                return dedupeCanonical(List.copyOf(canonical));
            }
            if (value instanceof Map<?, ?> map) {
                LinkedHashMap<Object, Object> canonical = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    canonical.put(entry.getKey(), canonicalizeValue(entry.getValue()));
                }
                return dedupeCanonical(Collections.unmodifiableMap(canonical));
            }
            if (value != null && value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                List<Object> canonical = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    canonical.add(canonicalizeValue(java.lang.reflect.Array.get(value, i)));
                }
                return dedupeCanonical(List.copyOf(canonical));
            }
            return value;
        }

        private Object dedupeCanonical(Object canonical) {
            Object cached = canonicalValues.get(canonical);
            if (cached != null) {
                return cached;
            }
            canonicalValues.put(canonical, canonical);
            return canonical;
        }
    }
}
