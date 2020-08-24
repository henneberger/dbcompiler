
import java.util.*;

public class LogicalPlan {
    private final DomainModel model;

    public LogicalPlan(DomainModel model) {
        this.model = model;
    }

    public List<SqlClause.Index> search() {
        Map<DomainModel.QuerySelection, DomainModel.Query> rootSelections = expandClauses(model.queries);

        List<SqlClause.Index> allIndices = new ArrayList<>();
        Set<Set<String>> merkle = new HashSet<>();
        List<Plan> allPlans = new ArrayList<>();
        for (Map.Entry<DomainModel.QuerySelection, DomainModel.Query> selection : rootSelections.entrySet()) {
            SqlClause clause = selection.getKey().getDefinition().getSqlClause();
            SqlClause.PermuteResult result = clause.permute(selection.getValue());
            if (result.plan == null) continue;
            allPlans.add(result.plan);
            for (SqlClause.Index index : result.indices) { //todo remove permute result
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

        new Optimizer(uniqueIndices, uniqueIndexMap, allIndices, rootSelections.keySet(), allPlans).optimize();

        return null;

    }

    private Map<DomainModel.QuerySelection, DomainModel.Query> expandClauses(List<DomainModel.Query> queries) {
        Map<DomainModel.QuerySelection, DomainModel.Query> map = new HashMap<>();
        for (DomainModel.Query q : queries) {
            for (DomainModel.QuerySelection selection : q.getQuerySelectionSet().getSelections()) {
                map.put(selection, q);
            }
        }

        return map;
    }
}
