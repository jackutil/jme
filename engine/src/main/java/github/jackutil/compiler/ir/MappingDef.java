package github.jackutil.compiler.ir;

public record MappingDef(int id, String name, String ref, MapNode root) {

    public static final class Builder {
        private int id;
        private String name;
        private String ref;
        private MapNode root;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder ref(String ref) {
            this.ref = ref;
            return this;
        }

        public Builder root(MapNode root) {
            this.root = root;
            return this;
        }

        public MappingDef build() {
            return new MappingDef(id, name, ref, root);
        }
    }
}
