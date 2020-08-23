public class Conjunction {
    private final String field;
    private final String value;

    public Conjunction(String field, String value) {

        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Conjunction{" +
                "field='" + field + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    public boolean isSargable() {
        return true;
    }
}
