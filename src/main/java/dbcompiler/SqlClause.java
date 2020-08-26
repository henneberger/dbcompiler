package dbcompiler;

import lombok.EqualsAndHashCode;

import java.util.List;

import static dbcompiler.DomainModel.Entity;

public class SqlClause {
    public Entity rootEntity;
    public List<Conjunction> conjunctions;

    public static class Conjunction {
        public final FieldPath fieldPath;
        public final Object value;

        public Conjunction(FieldPath fieldPath, Object value) {
            this.value = value;
            this.fieldPath = fieldPath;
        }

        @EqualsAndHashCode
        public static class FieldPath {
            public List<Entity.Field> fields;
            public String toStringVal;
            public Entity entity;
            public String toString() {
                return toStringVal;
            }
        }
    }
}
