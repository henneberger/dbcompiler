import java.util.List;

public class Entity {
    private final String name;
    private final List<Field> fields;

    public Entity(String name, List<Field> fields) {
        this.name = name;
        this.fields = fields;
    }

    @Override
    public String toString() {
        return "Entity{" +
                "name='" + name + '\'' +
                ", fields=" + fields +
                '}';
    }
}
