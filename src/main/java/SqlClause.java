import java.util.List;

public class SqlClause {
    private List<Conjunction> conjunctions;

    public SqlClause(List<Conjunction> conjunctions) {

        this.conjunctions = conjunctions;
    }

    @Override
    public String toString() {
        return "SqlClause{" +
                "conjunctions=" + conjunctions +
                '}';
    }
}
