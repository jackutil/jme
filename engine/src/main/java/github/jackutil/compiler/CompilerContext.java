package github.jackutil.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import github.jackutil.compiler.diagnostics.DiagnosticsCollector;
import github.jackutil.compiler.ir.EngineConfig;
import github.jackutil.compiler.ir.FunctionDef;
import github.jackutil.compiler.ir.InputDef;
import github.jackutil.compiler.ir.MappingDef;
import github.jackutil.compiler.ir.Meta;
import github.jackutil.compiler.ir.SchemaDef;
import github.jackutil.compiler.ir.ValidationRule;
import github.jackutil.compiler.ir.VariableDef;

final class CompilerContext {
    final JsonPointerTracker pointer = new JsonPointerTracker();
    final DiagnosticsCollector diagnostics = new DiagnosticsCollector();

    Meta meta;
    EngineConfig engine;

    final List<SchemaDef> schemas = new ArrayList<>();
    final Map<String, Integer> schemaIndex = new HashMap<>();

    final List<FunctionDef> functions = new ArrayList<>();
    final Map<String, Integer> functionIndex = new HashMap<>();
    final List<InputDef> inputs = new ArrayList<>();
    final Map<String, Integer> inputIndex = new HashMap<>();


    final List<VariableDef> variables = new ArrayList<>();
    final Map<String, Integer> variableIndex = new HashMap<>();

    final List<MappingDef> mappings = new ArrayList<>();
    final Map<String, Integer> mappingIndex = new HashMap<>();

    final List<ValidationRule> validations = new ArrayList<>();

    final Deque<String> sectionStack = new ArrayDeque<>();

    String pointer() {
        if (sectionStack.isEmpty()) {
            return "/";
        }
        List<String> segments = new ArrayList<>(sectionStack);
        Collections.reverse(segments);
        return "/" + String.join("/", segments);
    }
}



