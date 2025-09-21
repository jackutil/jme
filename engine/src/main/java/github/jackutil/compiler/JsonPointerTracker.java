package github.jackutil.compiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.stream.Collectors;

/** Tracks the JSON pointer while streaming. */
final class JsonPointerTracker {
    private final Deque<String> segments = new ArrayDeque<>();

    void push(String segment) {
        segments.push(segment);
    }

    void pop() {
        if (!segments.isEmpty()) {
            segments.pop();
        }
    }

    String currentPointer() {
        if (segments.isEmpty()) {
            return "/";
        }
        Iterator<String> descending = segments.descendingIterator();
        return "/" + java.util.stream.StreamSupport.stream(((Iterable<String>) () -> descending).spliterator(), false)
            .collect(Collectors.joining("/"));
    }
}
