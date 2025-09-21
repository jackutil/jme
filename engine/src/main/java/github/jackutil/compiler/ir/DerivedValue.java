package github.jackutil.compiler.ir;

import java.util.List;

public record DerivedValue(String functionRef, List<Object> args) {
}
