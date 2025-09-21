package github.jackutil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import github.jackutil.compiler.CompiledMapping;

public class Main {
    public static void main(String[] args) throws IOException{

        try (InputStream in = Main.class.getResourceAsStream("/engine-binding-test-config.json")) {
            EngineBinding binding = EngineBinding.fromStream(in);
            EngineBinding.ExecutionResult result = binding.execute(
                "orderSummary",
                Map.of("orderId", "ORD-1001", "customerId", "C-42"),
                Map.of()
            );
            CompiledMapping compiled = result.compiledMapping();
            Map<String, Object> json = result.output();
            System.out.println(compiled);
            System.out.println(json);
            System.out.println(result.prettyOutput());
        }

    }
}
