import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Map;

public class Entity {
    private final String name;
    private final Map<String, Map<String, Object>> directives;
    private List<Field> fields;

    public Entity(String name, Map<String, Map<String, Object>> directives) {
        this.name = name;
        this.directives = directives;
    }

    @Override
    public String toString() {
        return "Entity{" +
                "name='" + name + '\'' +
                ", fields=" + fields +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public Field getField(String field) {
        for (Field field1 : fields) {
            if (field1.getName().equals(field)) {
                return field1;
            }
        }
        Preconditions.checkNotNull(null, "Cannot find field in entity {}", field);
        return null;
    }

    public class Field {
        private final Map<String, Map<String, Object>> directives;
        private String name;
        private final String type;

        public Field(String name, String type, Map<String, Map<String, Object>> directives) {
            this.name = name;
            this.type = type;
            this.directives = directives;
        }

        public Map<String, Map<String, Object>> getDirectives() {
            return directives;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return "Field{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }

        public boolean isGenId() {
            return type.equals("ID");
        }
    }
}
