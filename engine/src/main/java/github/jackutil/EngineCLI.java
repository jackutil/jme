package github.jackutil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.jackutil.compiler.CompiledMapping;
import github.jackutil.compiler.ConfigCompiler;
import github.jackutil.compiler.ConfigValidator;
import github.jackutil.compiler.ConfigValidationException;
import github.jackutil.compiler.diagnostics.MappingDiagnostic;
import github.jackutil.compiler.diagnostics.MappingException;
import github.jackutil.compiler.runtime.MappingEngine;

public final class EngineCLI {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonFactory FACTORY = MAPPER.getFactory();
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private EngineCLI() {
    }

    public static void main(String[] args) {
        int status = new EngineCLI().run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    private int run(String[] args, PrintStream out, PrintStream err) {
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
        if (options.input().isPresent() && !Files.isRegularFile(options.input().get())) {
            err.println("Input file not found: " + options.input().get());
            return 66;
        }
        if (options.payload().isPresent() && !Files.isRegularFile(options.payload().get())) {
            err.println("Payload file not found: " + options.payload().get());
            return 66;
        }

        try {
            byte[] configBytes = Files.readAllBytes(options.config());
            validateConfig(configBytes);
            CompiledMapping compiled = compileConfig(configBytes);
            MappingEngine engine = new MappingEngine(compiled);
            Map<String, Object> inputs = readBindings(options.input());
            Map<String, Object> payload = readBindings(options.payload());

            try (JsonGenerator generator = openGenerator(options.output(), options.pretty(), out)) {
                engine.execute(options.mapping(), inputs, payload, generator);
                generator.flush();
            }
            return 0;
        } catch (ConfigValidationException ex) {
            err.println("Configuration invalid: " + ex.getMessage());
            return 65;
        } catch (MappingException ex) {
            MappingDiagnostic diagnostic = ex.diagnostic();
            err.println("Mapping failed [" + diagnostic.code() + "]: " + diagnostic.message());
            if (diagnostic.pointer() != null && !diagnostic.pointer().isBlank()) {
                err.println("Pointer: " + diagnostic.pointer());
            }
            if (!diagnostic.details().isEmpty()) {
                err.println("Details: " + diagnostic.details());
            }
            return 70;
        } catch (IOException ex) {
            err.println("I/O error: " + ex.getMessage());
            return 74;
        } catch (RuntimeException ex) {
            err.println("Unexpected error: " + ex.getMessage());
            return 70;
        }
    }

    private void validateConfig(byte[] configBytes) {
        try (InputStream in = new ByteArrayInputStream(configBytes)) {
            ConfigValidator.validate(in);
        } catch (IOException ex) {
            throw new ConfigValidationException("Failed to validate configuration");
        }
    }

    private CompiledMapping compileConfig(byte[] configBytes) {
        try (InputStream in = new ByteArrayInputStream(configBytes)) {
            return ConfigCompiler.compile(in);
        } catch (IOException ex) {
            throw new ConfigValidationException("Failed to compile configuration");
        }
    }

    private Map<String, Object> readBindings(Optional<Path> source) throws IOException {
        if (source.isEmpty()) {
            return Map.of();
        }
        Path path = source.get();
        if (Files.size(path) == 0) {
            return Map.of();
        }
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> values = MAPPER.readValue(in, JSON_MAP_TYPE);
            return values != null ? values : Map.of();
        }
    }

    private JsonGenerator openGenerator(Optional<Path> outputPath, boolean pretty, PrintStream stdOut) throws IOException {
        JsonGenerator generator;
        if (outputPath.isPresent()) {
            Path path = outputPath.get();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            generator = FACTORY.createGenerator(writer);
        } else {
            generator = FACTORY.createGenerator(stdOut);
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        }
        if (pretty) {
            generator.useDefaultPrettyPrinter();
        }
        return generator;
    }

    private void printUsage(PrintStream stream) {
        stream.println("Usage: java -jar engine.jar --config <file> --mapping <name> [--input <file>] [--payload <file>] [--output <file>] [--pretty]");
        stream.println();
        stream.println("Options:");
        stream.println("  --config <file>   Path to mapping DSL configuration (JSON).");
        stream.println("  --mapping <name>  Mapping name to execute.");
        stream.println("  --input <file>    Optional JSON file providing INPUT values.");
        stream.println("  --payload <file>  Optional JSON file providing payload variables.");
        stream.println("  --output <file>   Optional destination for generated JSON; defaults to stdout.");
        stream.println("  --pretty          Enable pretty-printed JSON output.");
        stream.println("  --help            Show this message.");
        stream.println();
        stream.println("Enable instruction metrics by adding -Djme.profile.instructions=true when launching.");
    }

    private record CliOptions(Path config,
                              Optional<Path> input,
                              Optional<Path> payload,
                              Optional<Path> output,
                              String mapping,
                              boolean pretty) {

        private static CliOptions parse(String[] args) {
            Path config = null;
            Path input = null;
            Path payload = null;
            Path output = null;
            String mapping = null;
            boolean pretty = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--config" -> config = Path.of(requireValue("--config", args, ++i));
                    case "--input" -> input = Path.of(requireValue("--input", args, ++i));
                    case "--payload" -> payload = Path.of(requireValue("--payload", args, ++i));
                    case "--output" -> output = Path.of(requireValue("--output", args, ++i));
                    case "--mapping" -> mapping = requireValue("--mapping", args, ++i);
                    case "--pretty" -> pretty = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }

            if (config == null) {
                throw new IllegalArgumentException("Missing required option --config");
            }
            if (mapping == null) {
                throw new IllegalArgumentException("Missing required option --mapping");
            }

            return new CliOptions(config, Optional.ofNullable(input), Optional.ofNullable(payload), Optional.ofNullable(output), mapping, pretty);
        }

        private static String requireValue(String option, String[] args, int index) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}

