package github.jackutil.compiler.ir;

import java.util.List;

import github.jackutil.compiler.ir.enums.ValueType;

public record VariableDef(int id,
                           String name,
                           ValueType type,
                           boolean required,
                           boolean nullable,
                           List<String> constraintRefs,
                           DerivedValue derive,
                           Object defaultValue) {

    public static final class Builder {
        private int id;
        private String name;
        private ValueType type;
        private boolean required;
        private boolean nullable;
        private List<String> constraintRefs = List.of();
        private DerivedValue derive;
        private Object defaultValue;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(ValueType type) {
            this.type = type;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder nullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public Builder constraintRefs(List<String> constraintRefs) {
            this.constraintRefs = constraintRefs;
            return this;
        }

        public Builder derive(DerivedValue derive) {
            this.derive = derive;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public VariableDef build() {
            return new VariableDef(id, name, type, required, nullable, constraintRefs, derive, defaultValue);
        }
    }
}
