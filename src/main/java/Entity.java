import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class Entity {
    private final String name;
    private final Map<String, Map<String, Object>> directives;
    private List<Field> fields;
    private ImmutableMap<String, Field> fieldMap;

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

    public int estimateCost(String... field) {
        if (field.length == 0) {
            return getMaxCount();
        }

        return getField(field[0]).getSelectivity();
    }

    private int getMaxCount() {
        return (int)directives.get("size").get("max");
    }

    public String getName() {
        return name;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
        this.fieldMap = Maps.uniqueIndex(fields, f->f.name);
    }

    public Field getField(String field) {
        Field f = fieldMap.get(field);
        Preconditions.checkNotNull(f, "Cannot find field in entity {}", field);
        return f;
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

        public int getSelectivity() {
            return 0;
        }
    }
}
