public class Conjunction {
    private final String field;
    private final String value;
    private final Entity rootEntity; //todo: sloppy data model: needs fixing
    private final String[] split;

    public Conjunction(String field, String value, Entity rootEntity) {
        this.field = field;
        this.value = value;
        this.rootEntity = rootEntity;
        this.split = field.split("\\.");
    }

    public FieldPath getFieldPath() {
        return null;
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

    public boolean isGenId() {
        return rootEntity.getField(split[0]).isGenId();
    }
}
