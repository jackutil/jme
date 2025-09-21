package github.jackutil.compiler.runtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import github.jackutil.compiler.ir.FunctionDef;

final class BuiltinFunctionRuntime implements FunctionRuntime {
    private static final ConcurrentHashMap<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    private final Builtin builtin;
    private final List<Object> defaultArgs;

    BuiltinFunctionRuntime(FunctionDef function) {
        this.builtin = Builtin.fromName((String) function.payload());
        this.defaultArgs = function.args() == null ? List.of() : function.args();
    }

    @Override
    public void validate(Object value) {
        builtin.validate(value);
    }

    @Override
    public Object derive(List<Object> args) {
        List<Object> effectiveArgs = mergeArgs(args);
        return builtin.derive(effectiveArgs);
    }

    private List<Object> mergeArgs(List<Object> callArgs) {
        boolean hasCallArgs = callArgs != null && !callArgs.isEmpty();
        if (!hasCallArgs) {
            return defaultArgs;
        }
        if (defaultArgs.isEmpty()) {
            return callArgs;
        }
        List<Object> merged = new ArrayList<>(callArgs);
        merged.addAll(defaultArgs);
        return merged;
    }

    private enum Builtin {
        DATE {
            @Override
            Object derive(List<Object> args) {
                String pattern = args.isEmpty() ? "yyyy-MM-dd'T'HH:mm:ss'Z'" : String.valueOf(args.get(0));
                DateTimeFormatter formatter = FORMATTERS.computeIfAbsent(pattern,
                    p -> DateTimeFormatter.ofPattern(p).withZone(ZoneOffset.UTC));
                return formatter.format(Instant.now());
            }
        },
        DATE_UTC {
            @Override
            Object derive(List<Object> args) {
                return DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now());
            }
        },
        DATE_NOW_MILLIS {
            @Override
            Object derive(List<Object> args) {
                return System.currentTimeMillis();
            }
        },
        POS_INT {
            @Override
            Object derive(List<Object> args) {
                int length = args.isEmpty() ? 1 : toInt(args.get(0));
                String prefix = args.size() > 1 ? String.valueOf(args.get(1)) : "";
                if (length <= 0) {
                    throw new IllegalArgumentException("pos_int length must be positive");
                }
                StringBuilder builder = new StringBuilder(prefix);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < length; i++) {
                    builder.append(random.nextInt(10));
                }
                return builder.toString();
            }
        },
        POS_INT_BETWEEN {
            @Override
            Object derive(List<Object> args) {
                if (args.size() < 2) {
                    throw new IllegalArgumentException("pos_int_between requires min and max arguments");
                }
                long min = toLong(args.get(0));
                long max = toLong(args.get(1));
                if (min > max) {
                    throw new IllegalArgumentException("pos_int_between min must be <= max");
                }
                return ThreadLocalRandom.current().nextLong(min, max + 1);
            }
        },
        UUID_FN {
            @Override
            Object derive(List<Object> args) {
                return java.util.UUID.randomUUID().toString();
            }
        },
        UUID_COMPACT {
            @Override
            Object derive(List<Object> args) {
                return java.util.UUID.randomUUID().toString().replace("-", "");
            }
        },
        UUID_URN {
            @Override
            Object derive(List<Object> args) {
                return "urn:uuid:" + java.util.UUID.randomUUID();
            }
        },
        UPPERCASE {
            @Override
            Object derive(List<Object> args) {
                return toStringArg(args, 0).toUpperCase(Locale.ROOT);
            }
        },
        LOWERCASE {
            @Override
            Object derive(List<Object> args) {
                return toStringArg(args, 0).toLowerCase(Locale.ROOT);
            }
        },
        TRIM {
            @Override
            Object derive(List<Object> args) {
                return toStringArg(args, 0).trim();
            }
        },
        CONCAT {
            @Override
            Object derive(List<Object> args) {
                StringBuilder builder = new StringBuilder();
                for (Object arg : args) {
                    builder.append(arg == null ? "" : arg);
                }
                return builder.toString();
            }
        },
        PAD_LEFT {
            @Override
            Object derive(List<Object> args) {
                if (args.size() < 2) {
                    throw new IllegalArgumentException("pad_left requires value and length");
                }
                String value = String.valueOf(args.get(0));
                int length = toInt(args.get(1));
                char pad = args.size() > 2 ? String.valueOf(args.get(2)).charAt(0) : ' ';
                return pad(value, length, pad, true);
            }
        },
        PAD_RIGHT {
            @Override
            Object derive(List<Object> args) {
                if (args.size() < 2) {
                    throw new IllegalArgumentException("pad_right requires value and length");
                }
                String value = String.valueOf(args.get(0));
                int length = toInt(args.get(1));
                char pad = args.size() > 2 ? String.valueOf(args.get(2)).charAt(0) : ' ';
                return pad(value, length, pad, false);
            }
        },
        LOOKUP {
            @Override
            Object derive(List<Object> args) {
                if (args.isEmpty()) {
                    throw new IllegalArgumentException("lookup requires at least a key argument");
                }
                Object key = args.get(0);
                Map<?, ?> map = Map.of();
                Object defaultValue = null;
                if (args.size() > 1 && args.get(1) instanceof Map<?, ?> m) {
                    map = m;
                    if (args.size() > 2) {
                        defaultValue = args.get(2);
                    }
                } else if (args.size() > 1) {
                    throw new IllegalArgumentException("lookup second argument must be a map");
                }
                Object value = map.get(key);
                return value != null ? value : defaultValue;
            }
        },
        ADD {
            @Override
            Object derive(List<Object> args) {
                ensureArgs(args, 2, "add requires at least two arguments");
                BigDecimal sum = BigDecimal.ZERO;
                for (Object arg : args) {
                    sum = sum.add(toBigDecimal(arg));
                }
                return reduceNumber(sum);
            }
        },
        SUBTRACT {
            @Override
            Object derive(List<Object> args) {
                ensureArgs(args, 2, "subtract requires at least two arguments");
                BigDecimal result = toBigDecimal(args.get(0));
                for (int i = 1; i < args.size(); i++) {
                    result = result.subtract(toBigDecimal(args.get(i)));
                }
                return reduceNumber(result);
            }
        },
        MULTIPLY {
            @Override
            Object derive(List<Object> args) {
                ensureArgs(args, 2, "multiply requires at least two arguments");
                BigDecimal result = BigDecimal.ONE;
                for (Object arg : args) {
                    result = result.multiply(toBigDecimal(arg));
                }
                return reduceNumber(result);
            }
        },
        DIVIDE {
            @Override
            Object derive(List<Object> args) {
                ensureArgs(args, 2, "divide requires at least two arguments");
                BigDecimal result = toBigDecimal(args.get(0));
                for (int i = 1; i < args.size(); i++) {
                    BigDecimal divisor = toBigDecimal(args.get(i));
                    result = result.divide(divisor);
                }
                return reduceNumber(result);
            }
        },
        MIN {
            @Override
            Object derive(List<Object> args) {
                ensureArgs(args, 1, "min requires at least one argument");
                BigDecimal min = toBigDecimal(args.get(0));
                for (int i = 1; i < args.size(); i++) {
                    BigDecimal current = toBigDecimal(args.get(i));
                    if (current.compareTo(min) < 0) {
                        min = current;
                    }
                }
                return reduceNumber(min);
            }
        },
        MAX {
            @Override
            Object derive(List<Object> args) {
                ensureArgs(args, 1, "max requires at least one argument");
                BigDecimal max = toBigDecimal(args.get(0));
                for (int i = 1; i < args.size(); i++) {
                    BigDecimal current = toBigDecimal(args.get(i));
                    if (current.compareTo(max) > 0) {
                        max = current;
                    }
                }
                return reduceNumber(max);
            }
        },
        ABS {
            @Override
            Object derive(List<Object> args) {
                ensureArgs(args, 1, "abs requires one argument");
                BigDecimal value = toBigDecimal(args.get(0));
                return reduceNumber(value.abs());
            }
        };

        void validate(Object value) {
            // default no-op
        }

        abstract Object derive(List<Object> args);

        static Builtin fromName(String name) {
            return switch (name) {
                case "date" -> DATE;
                case "date_utc" -> DATE_UTC;
                case "date_now_millis" -> DATE_NOW_MILLIS;
                case "pos_int" -> POS_INT;
                case "pos_int_between" -> POS_INT_BETWEEN;
                case "uuid" -> UUID_FN;
                case "uuid_compact" -> UUID_COMPACT;
                case "uuid_urn" -> UUID_URN;
                case "uppercase" -> UPPERCASE;
                case "lowercase" -> LOWERCASE;
                case "trim" -> TRIM;
                case "concat" -> CONCAT;
                case "pad_left" -> PAD_LEFT;
                case "pad_right" -> PAD_RIGHT;
                case "lookup" -> LOOKUP;
                case "add" -> ADD;
                case "subtract" -> SUBTRACT;
                case "multiply" -> MULTIPLY;
                case "divide" -> DIVIDE;
                case "min" -> MIN;
                case "max" -> MAX;
                case "abs" -> ABS;
                default -> throw new UnsupportedOperationException("Unsupported builtin function: " + name);
            };
        }

        static void ensureArgs(List<Object> args, int expected, String message) {
            if (args == null || args.size() < expected) {
                throw new IllegalArgumentException(message);
            }
        }

        static int toInt(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        }

        static long toLong(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        }

        static String toStringArg(List<Object> args, int index) {
            ensureArgs(args, index + 1, "Missing argument");
            Object value = args.get(index);
            return value == null ? "" : value.toString();
        }

        static BigDecimal toBigDecimal(Object value) {
            if (value instanceof BigDecimal bd) {
                return bd;
            }
            if (value instanceof Integer i) {
                return BigDecimal.valueOf(i);
            }
            if (value instanceof Long l) {
                return BigDecimal.valueOf(l);
            }
            if (value instanceof Double d) {
                return BigDecimal.valueOf(d);
            }
            if (value instanceof Float f) {
                return BigDecimal.valueOf(f);
            }
            if (value instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            return new BigDecimal(String.valueOf(value));
        }

        static Object reduceNumber(BigDecimal value) {
            BigDecimal stripped = value.stripTrailingZeros();
            if (stripped.scale() <= 0) {
                try {
                    return stripped.longValueExact();
                } catch (ArithmeticException ignored) {
                    // fall through
                }
            }
            try {
                return stripped.doubleValue();
            } catch (ArithmeticException ex) {
                return stripped;
            }
        }

        static String pad(String value, int length, char pad, boolean left) {
            if (value.length() >= length) {
                return value;
            }
            StringBuilder builder = new StringBuilder(length);
            int padCount = length - value.length();
            if (left) {
                for (int i = 0; i < padCount; i++) {
                    builder.append(pad);
                }
                builder.append(value);
            } else {
                builder.append(value);
                for (int i = 0; i < padCount; i++) {
                    builder.append(pad);
                }
            }
            return builder.toString();
        }
    }
}
