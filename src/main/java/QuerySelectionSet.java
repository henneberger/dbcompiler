import java.util.List;

public class QuerySelectionSet {
    private final String name;
    private final List<QuerySelection> selections;

    public QuerySelectionSet(String name, List<QuerySelection> selections) {

        this.name = name;
        this.selections = selections;
    }

    @Override
    public String toString() {
        return "QuerySelectionSet{" +
                "name=" + name +
                ", selections=" + selections +
                '}';
    }
}
