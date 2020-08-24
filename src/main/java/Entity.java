import java.util.List;

public class Entity {
    private final String name;
    private List<Field> fields;

    public Entity(String name) {
        this.name = name;
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

    public class Field {
        private String name;
        private final String type;

        public Field(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return "Field{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }
}
