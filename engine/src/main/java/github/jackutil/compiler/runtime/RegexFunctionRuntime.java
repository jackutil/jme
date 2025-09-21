package github.jackutil.compiler.runtime;

import java.util.List;
import java.util.regex.Pattern;

import github.jackutil.compiler.ir.FunctionDef;

final class RegexFunctionRuntime implements FunctionRuntime {
    private final Pattern pattern;
    private final String description;

    RegexFunctionRuntime(FunctionDef function) {
        this.pattern = (Pattern) function.payload();
        this.description = function.description() != null ? function.description() : "regex";
    }

    @Override
    public void validate(Object value) {
        if (value == null) {
            return; // allow optional values
        }
        if (!(value instanceof CharSequence sequence)) {
            throw new IllegalStateException("Regex function expects a string value: " + description);
        }
        if (!pattern.matcher(sequence).matches()) {
            throw new IllegalStateException("Value '" + sequence + "' does not match pattern " + pattern.pattern());
        }
    }

    @Override
    public Object derive(List<Object> args) {
        throw new UnsupportedOperationException("Regex functions cannot derive values");
    }
}
