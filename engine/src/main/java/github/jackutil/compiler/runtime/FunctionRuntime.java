package github.jackutil.compiler.runtime;

import java.util.List;

interface FunctionRuntime {
    void validate(Object value);
    Object derive(List<Object> args);
}
