package github.jackutil.compiler.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deduplicates strings for instruction operands. */
public final class StringTable {
    private final Map<String, Integer> index = new HashMap<>();
    private final List<String> values = new ArrayList<>();

    public int intern(String value) {
        return index.computeIfAbsent(value, key -> {
            values.add(key);
            return values.size() - 1;
        });
    }

    public List<String> values() {
        return values;
    }
}
