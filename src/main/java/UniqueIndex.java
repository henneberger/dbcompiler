import java.util.Set;

public class UniqueIndex {
    private final Set<String> merkle;
    public UniqueIndex(Set<String> merkle) {
        this.merkle = merkle;
    }

    @Override
    public String toString() {
        return "u_idx"+ merkle;
    }
}
