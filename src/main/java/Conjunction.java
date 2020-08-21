public class Conjunction {
    private final String field;
    private final String value;

    public Conjunction(String field, String value) {

        this.field = field;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Conjunction{" +
                "field='" + field + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
