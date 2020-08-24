import com.google.common.base.Preconditions;

public class TypeDef {

    private final Entity name;
    private final Multiplicity multiplicity;
    private final boolean nonnull;

    public TypeDef(Entity name, Multiplicity multiplicity, boolean nonnull) {
        Preconditions.checkNotNull(name, "Entity is null for type def");
        this.name = name;
        this.multiplicity = multiplicity;
        this.nonnull = nonnull;
    }

    public Entity getName() {
        return name;
    }

    public Multiplicity getMultiplicity() {
        return multiplicity;
    }

    public boolean isNonnull() {
        return nonnull;
    }

    public Entity getEntity() {
        return name;
    }
}
