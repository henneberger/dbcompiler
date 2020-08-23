
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LogicalPlan {
    private final DomainModel model;

    public LogicalPlan(DomainModel model) {
        this.model = model;
    }

    public List<SqlClause.Plan> search() {
        List<DomainModel.QuerySelection> rootSelections = expandClauses(model.queries);

        Set<SqlClause.Plan> allPlans = new HashSet<>();
        for (DomainModel.QuerySelection selection : rootSelections) {
            SqlClause clause = selection.getDefinition().getSqlClause();
            allPlans.addAll(clause.permute());
        }

        System.out.println(allPlans);

        new Optimizer(allPlans, rootSelections).optimize();

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
