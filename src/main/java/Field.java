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
