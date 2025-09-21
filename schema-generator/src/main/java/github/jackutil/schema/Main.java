package github.jackutil.schema;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int status = new SchemaGeneratorCli().run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }
}
