package github.jackutil.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.ir.ConfigModel;
import github.jackutil.compiler.ir.DerivedValue;
import github.jackutil.compiler.ir.FunctionDef;
import github.jackutil.compiler.ir.InputDef;
import github.jackutil.compiler.ir.MapNode;
import github.jackutil.compiler.ir.MappingDef;
import github.jackutil.compiler.ir.ValidationRule;
import github.jackutil.compiler.ir.VariableDef;
import github.jackutil.compiler.ir.resolved.ResolvedConfig;
import github.jackutil.compiler.ir.resolved.ResolvedInput;
import github.jackutil.compiler.ir.resolved.ResolvedMapNode;
import github.jackutil.compiler.ir.resolved.ResolvedMapping;
import github.jackutil.compiler.ir.resolved.ResolvedValidationRule;
import github.jackutil.compiler.ir.resolved.ResolvedVariable;

final class ReferenceResolver {
    private ReferenceResolver() {
    }

    static ResolvedConfig resolve(ConfigModel model,
                                  Map<String, Integer> functionIndex,
                                  Map<String, Integer> inputIndex,
                                  Map<String, Integer> variableIndex,
                                  Map<String, Integer> mappingIndex,
                                  Map<String, Integer> schemaIndex) {
        List<FunctionDef> functions = model.functions();
        List<ResolvedInput> inputs = resolveInputs(model.inputs());
        List<ResolvedVariable> variables = resolveVariables(model.variables(), functionIndex);
        List<ResolvedMapping> mappings = resolveMappings(model.mappings(), inputIndex, variableIndex, mappingIndex);
        List<ResolvedValidationRule> validations = resolveValidations(model.validations(), schemaIndex);
        return new ResolvedConfig(model.meta(), model.engine(), model.schemas(), functions, inputs, variables, mappings, validations);
    }

    private static List<ResolvedInput> resolveInputs(List<InputDef> inputs) {
        List<ResolvedInput> resolved = new ArrayList<>(inputs.size());
        for (InputDef input : inputs) {
            resolved.add(new ResolvedInput(
                input.id(),
                input.name(),
                input.type(),
                input.required(),
                input.nullable(),
                input.defaultValue()
            ));
        }
        return resolved;
    }

    private static List<ResolvedVariable> resolveVariables(List<VariableDef> variables,
                                                           Map<String, Integer> functionIndex) {
        List<ResolvedVariable> resolved = new ArrayList<>(variables.size());
        for (VariableDef variable : variables) {
            int[] constraintIds = variable.constraintRefs().stream()
                .mapToInt(ref -> resolveFunctionRef(ref, functionIndex))
                .toArray();
            DerivedValue derive = variable.derive();
            Integer deriveFunctionId = null;
            List<Object> deriveArgs = List.of();
            if (derive != null) {
                deriveFunctionId = resolveFunctionRef(derive.functionRef(), functionIndex);
                deriveArgs = derive.args();
            }
            resolved.add(new ResolvedVariable(
                variable.id(),
                variable.name(),
                variable.type(),
                variable.required(),
                variable.nullable(),
                constraintIds,
                deriveFunctionId,
                deriveArgs,
                variable.defaultValue()
            ));
        }
        return resolved;
    }

    private static int resolveFunctionRef(String reference, Map<String, Integer> functionIndex) {
        if (reference == null) {
            throw MappingException.of("REFERENCE_NULL", "Function reference cannot be null", "/FUNCTIONS");
        }
        String key = stripPrefix(reference, "$FUNCTIONS.", "/FUNCTIONS");
        Integer id = functionIndex.get(key);
        if (id == null) {
            throw MappingException.of("REFERENCE_UNKNOWN_FUNCTION", "Unknown function reference: " + reference, "/FUNCTIONS/" + key);
        }
        return id;
    }

    private static List<ResolvedMapping> resolveMappings(List<MappingDef> mappings,
                                                         Map<String, Integer> inputIndex,
                                                         Map<String, Integer> variableIndex,
                                                         Map<String, Integer> mappingIndex) {
        List<ResolvedMapping> resolved = new ArrayList<>(mappings.size());
        for (MappingDef mapping : mappings) {
            ResolvedMapNode node = resolveNode(mapping.root(), inputIndex, variableIndex, mappingIndex);
            resolved.add(new ResolvedMapping(mapping.id(), mapping.name(), mapping.ref(), node));
        }
        detectCycles(resolved);
        return resolved;
    }

    private static List<ResolvedValidationRule> resolveValidations(List<ValidationRule> validations,
                                                                   Map<String, Integer> schemaIndex) {
        List<ResolvedValidationRule> resolved = new ArrayList<>(validations.size());
        for (ValidationRule rule : validations) {
            int schemaId = resolveSchemaRef(rule.schemaRef(), schemaIndex);
            resolved.add(new ResolvedValidationRule(rule.id(), rule.name(), schemaId, rule.phase()));
        }
        return resolved;
    }

    private static int resolveSchemaRef(String reference, Map<String, Integer> schemaIndex) {
        String key = stripPrefix(reference, "$SCHEMA.", "/SCHEMA");
        Integer id = schemaIndex.get(key);
        if (id == null) {
            throw MappingException.of("REFERENCE_UNKNOWN_SCHEMA", "Unknown schema reference: " + reference, "/SCHEMA/" + key);
        }
        return id;
    }

    private static ResolvedMapNode resolveNode(MapNode node,
                                               Map<String, Integer> inputIndex,
                                               Map<String, Integer> variableIndex,
                                               Map<String, Integer> mappingIndex) {
        if (node instanceof MapNode.LiteralNode literal) {
            return new ResolvedMapNode.LiteralNode(literal.value());
        }
        if (node instanceof MapNode.VariableRefNode variableRef) {
            int variableId = resolveVariableRef(variableRef.reference(), variableIndex);
            return new ResolvedMapNode.VariableRefNode(variableId);
        }
        if (node instanceof MapNode.InputRefNode inputRef) {
            int inputId = resolveInputRef(inputRef.reference(), inputIndex);
            return new ResolvedMapNode.InputRefNode(inputId);
        }
        if (node instanceof MapNode.MappingRefNode mappingRef) {
            int mappingId = resolveMappingRef(mappingRef.reference(), mappingIndex);
            return new ResolvedMapNode.MappingRefNode(mappingId);
        }
        if (node instanceof MapNode.ObjectNode objectNode) {
            List<ResolvedMapNode.ObjectNode.Field> fields = new ArrayList<>(objectNode.fields().size());
            for (MapNode.ObjectNode.Field field : objectNode.fields()) {
                fields.add(new ResolvedMapNode.ObjectNode.Field(field.name(), resolveNode(field.value(), inputIndex, variableIndex, mappingIndex)));
            }
            return new ResolvedMapNode.ObjectNode(fields);
        }
        if (node instanceof MapNode.ArrayNode arrayNode) {
            List<ResolvedMapNode> elements = new ArrayList<>(arrayNode.elements().size());
            for (MapNode element : arrayNode.elements()) {
                elements.add(resolveNode(element, inputIndex, variableIndex, mappingIndex));
            }
            return new ResolvedMapNode.ArrayNode(elements);
        }
        throw MappingException.of("UNSUPPORTED_NODE", "Unsupported node type: " + node.getClass(), "/MAPPINGS");
    }

    private static int resolveInputRef(String reference, Map<String, Integer> inputIndex) {
        String key = stripPrefix(reference, "$INPUT.", "/INPUT");
        Integer id = inputIndex.get(key);
        if (id == null) {
            throw MappingException.of("REFERENCE_UNKNOWN_INPUT", "Unknown input reference: " + reference, "/INPUT/" + key);
        }
        return id;
    }

    private static int resolveVariableRef(String reference, Map<String, Integer> variableIndex) {
        String key = stripPrefix(reference, "$VARIABLES.", "/VARIABLES");
        Integer id = variableIndex.get(key);
        if (id == null) {
            throw MappingException.of("REFERENCE_UNKNOWN_VARIABLE", "Unknown variable reference: " + reference, "/VARIABLES/" + key);
        }
        return id;
    }

    private static int resolveMappingRef(String reference, Map<String, Integer> mappingIndex) {
        String key = stripPrefix(reference, "$MAPPINGS.", "/MAPPINGS");
        Integer id = mappingIndex.get(key);
        if (id == null) {
            throw MappingException.of("REFERENCE_UNKNOWN_MAPPING", "Unknown mapping reference: " + reference, "/MAPPINGS/" + key);
        }
        return id;
    }

    private static String stripPrefix(String value, String prefix, String pointerBase) {
        if (!value.startsWith(prefix)) {
            throw MappingException.of("REFERENCE_FORMAT", "Reference must start with " + prefix + ": " + value, pointerBase);
        }
        return value.substring(prefix.length());
    }

    private static void detectCycles(List<ResolvedMapping> mappings) {
        int size = mappings.size();
        Map<Integer, ResolvedMapping> mappingById = mappings.stream()
            .collect(Collectors.toMap(ResolvedMapping::id, m -> m));
        boolean[] visited = new boolean[size];
        boolean[] stack = new boolean[size];
        for (ResolvedMapping mapping : mappings) {
            if (!visited[mapping.id()]) {
                dfs(mapping, mappingById, visited, stack);
            }
        }
    }

    private static void dfs(ResolvedMapping mapping,
                            Map<Integer, ResolvedMapping> mappingById,
                            boolean[] visited,
                            boolean[] stack) {
        visited[mapping.id()] = true;
        stack[mapping.id()] = true;
        for (int dep : mappingDependencies(mapping.root())) {
            if (!visited[dep]) {
                dfs(mappingById.get(dep), mappingById, visited, stack);
            } else if (stack[dep]) {
                throw MappingException.of("REFERENCE_CYCLE", "Cycle detected in mappings involving id " + dep, "/MAPPINGS");
            }
        }
        stack[mapping.id()] = false;
    }

    private static List<Integer> mappingDependencies(ResolvedMapNode node) {
        List<Integer> deps = new ArrayList<>();
        Deque<ResolvedMapNode> work = new ArrayDeque<>();
        work.push(node);
        while (!work.isEmpty()) {
            ResolvedMapNode current = work.pop();
            if (current instanceof ResolvedMapNode.MappingRefNode mappingRef) {
                deps.add(mappingRef.mappingId());
            } else if (current instanceof ResolvedMapNode.ObjectNode objectNode) {
                for (ResolvedMapNode.ObjectNode.Field field : objectNode.fields()) {
                    work.push(field.value());
                }
            } else if (current instanceof ResolvedMapNode.ArrayNode arrayNode) {
                work.addAll(arrayNode.elements());
            }
        }
        return deps;
    }
}
