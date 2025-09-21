package github.jackutil.schema;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SchemaGeneratorCli {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SchemaGenerator generator = new SchemaGenerator(MAPPER);

    int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            printUsage(out);
            return 64;
        }
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                printUsage(out);
                return 0;
            }
        }

        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException ex) {
            err.println("Error: " + ex.getMessage());
            printUsage(err);
            return 64;
        }

        if (!Files.isRegularFile(options.config())) {
            err.println("Configuration file not found: " + options.config());
            return 66;
        }

        try {
            ObjectNode schema = generator.generate(options.config());
            if (options.output().isPresent()) {
                Path output = options.output().get();
                Path parent = output.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                    if (options.pretty()) {
                        MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, schema);
                    } else {
                        MAPPER.writeValue(writer, schema);
                    }
                }
            } else {
                try (JsonGenerator generator = MAPPER.getFactory().createGenerator(out)) {
                    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                    if (options.pretty()) {
                        generator.useDefaultPrettyPrinter();
                    }
                    MAPPER.writeValue(generator, schema);
                    generator.flush();
                }
                if (!options.pretty()) {
                    out.println();
                }
            }
            return 0;
        } catch (IOException ex) {
            err.println("I/O error: " + ex.getMessage());
            return 74;
        } catch (RuntimeException ex) {
            err.println("Failed to generate schema: " + ex.getMessage());
            return 70;
        }
    }

    private void printUsage(PrintStream stream) {
        stream.println("Usage: java -jar schema-generator.jar --config <file> [--output <file>] [--pretty]");
        stream.println();
        stream.println("Options:");
        stream.println("  --config <file>   Path to mapping DSL configuration (JSON).");
        stream.println("  --output <file>   Optional destination for the generated schema; defaults to stdout.");
        stream.println("  --pretty          Enable pretty-printed JSON output.");
        stream.println("  --help            Show this message.");
    }

    private record CliOptions(Path config, Optional<Path> output, boolean pretty) {

        private static CliOptions parse(String[] args) {
            Path config = null;
            Path output = null;
            boolean pretty = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--config" -> config = Path.of(requireValue("--config", args, ++i));
                    case "--output" -> output = Path.of(requireValue("--output", args, ++i));
                    case "--pretty" -> pretty = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }

            if (config == null) {
                throw new IllegalArgumentException("Missing required option --config");
            }

            return new CliOptions(config, Optional.ofNullable(output), pretty);
        }

        private static String requireValue(String option, String[] args, int index) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}
