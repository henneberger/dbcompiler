
import java.util.*;

public class LogicalPlan {
    private final DomainModel model;

    public LogicalPlan(DomainModel model) {
        this.model = model;
    }

    public List<SqlClause.Index> search() {
        List<DomainModel.QuerySelection> rootSelections = expandClauses(model.queries);

        List<SqlClause.Index> allIndices = new ArrayList<>();
        Set<Set<String>> merkle = new HashSet<>();
        for (DomainModel.QuerySelection selection : rootSelections) {
            SqlClause clause = selection.getDefinition().getSqlClause();
            for (SqlClause.Index index : clause.permute()) {
                merkle.add(index.getMerkle());
                allIndices.add(index);
            }
        }

        Set<UniqueIndex> uniqueIndices = new HashSet<>();
        Map<Set<String>, UniqueIndex> uniqueSetMap = new HashMap<>();
        Map<SqlClause.Index, UniqueIndex> uniqueIndexMap = new HashMap<>();

        for (SqlClause.Index index : allIndices) {
            UniqueIndex uniqueIndex;
            if ((uniqueIndex = uniqueSetMap.get(index.getMerkle())) == null) {
                uniqueIndex = new UniqueIndex(index.getMerkle());
                uniqueSetMap.put(index.getMerkle(), uniqueIndex);
                uniqueIndices.add(uniqueIndex);
            }

            uniqueIndexMap.put(index, uniqueIndex);
        }

        new Optimizer(uniqueIndices, uniqueIndexMap, allIndices, rootSelections).optimize();

        return null;

    }

    private List<DomainModel.QuerySelection> expandClauses(List<DomainModel.Query> queries) {
        List<DomainModel.QuerySelection> list = new ArrayList<>();
        for (DomainModel.Query q : queries) {
            list.addAll(q.getQuerySelectionSet()
                    .getSelections());
        }

        return list;
    }
}
