package github.jackutil.compiler.runtime;

import java.util.ArrayList;
import java.util.List;

import github.jackutil.compiler.ir.FunctionDef;

final class FunctionExecutor {
    private final List<FunctionRuntime> runtimes;

    FunctionExecutor(List<FunctionDef> functions) {
        this.runtimes = new ArrayList<>(functions.size());
        for (FunctionDef function : functions) {
            runtimes.add(createRuntime(function));
        }
    }

    void validate(int functionId, Object value) {
        if (functionId < 0 || functionId >= runtimes.size()) {
            throw new IllegalArgumentException("Unknown function id: " + functionId);
        }
        runtimes.get(functionId).validate(value);
    }

    Object derive(Integer functionId, List<Object> args) {
        if (functionId == null) {
            return null;
        }
        if (functionId < 0 || functionId >= runtimes.size()) {
            throw new IllegalArgumentException("Unknown function id: " + functionId);
        }
        return runtimes.get(functionId).derive(args);
    }

    private FunctionRuntime createRuntime(FunctionDef function) {
        return switch (function.kind()) {
            case REGEX -> new RegexFunctionRuntime(function);
            case BUILTIN -> new BuiltinFunctionRuntime(function);
        };
    }
}
