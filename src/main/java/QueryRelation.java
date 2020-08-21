
public class QueryRelation extends QuerySelection {
    private QuerySelectionSet querySelectionSet;

    public QueryRelation(String name, QuerySelectionSet selection) {
        super(name);
        querySelectionSet = selection;
    }

    @Override
    public String toString() {
        return "QueryRelation{" +
                "querySelectionSet=" + querySelectionSet +
                '}';
    }
}
