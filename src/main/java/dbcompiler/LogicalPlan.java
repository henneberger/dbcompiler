package dbcompiler;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

import static dbcompiler.DomainModel.*;

public class LogicalPlan {
    private final DomainModel model;

    public LogicalPlan(DomainModel model) {
        this.model = model;
    }

    public Workload search() {
        List<QueryPlan> plans = new ArrayList<>();
        for (Query query : model.queries) {
            for (Query.QueryDefinitionSelection definition : query.selections) {
                QueryPlan plan = new QueryPlan(query, permute(definition));
                if (plan.plans == null) continue;
                plans.add(plan);
            }
        }
        return new Workload(plans);
    }

    public List<Plan> permute(Query.QueryDefinitionSelection selection) {
        QueryDefinition.SqlClause clause = selection.definition.sqlClause;
        /*
         * Queries with a generated ID on its root path can always be found with a direct lookup
         */
        if (hasRootId(clause)) {
            return null;
        }


        Set<QueryDefinition.SqlClause.Conjunction.FieldPath> sargable = getSargablePredicates(clause);
        List<Plan> plans = new ArrayList<>();

        for (int i = 0; i <= sargable.size(); i++) {
            for (Set<QueryDefinition.SqlClause.Conjunction.FieldPath> comb : Sets.combinations(sargable, i)) {
                Set<QueryDefinition.SqlClause.Conjunction.FieldPath> remaining = new HashSet<>(getAllPredicates(clause));
                remaining.removeAll(comb);

                Index index = new Index(selection.getQuery(), comb, ImmutableSet.of(), remaining, clause.rootEntity, clause);
                plans.add(new Plan(index, null/*todo: recursively build joins if there are relationships in the remaining set*/));
            }
        }

        return plans;
    }

    //todo: query rewriting may have an issue with this
    private boolean hasRootId(QueryDefinition.SqlClause clause) {
        for (QueryDefinition.SqlClause.Conjunction conjunction : clause.conjunctions) {
            if (conjunction.fieldPath.fields.get(0).typeDef.typeName.equals("ID")) {
                return true;
            }
        }
        return false;
    }

    public Set<QueryDefinition.SqlClause.Conjunction.FieldPath> getSargablePredicates(QueryDefinition.SqlClause clause) {
        return clause.conjunctions.stream()
                .map(e->e.fieldPath)
                .filter(QueryDefinition.SqlClause.Conjunction.FieldPath::isSargable)
                .collect(Collectors.toSet());
    }

    public Set<QueryDefinition.SqlClause.Conjunction.FieldPath> getAllPredicates(QueryDefinition.SqlClause clause) {
        return clause.conjunctions.stream()
                .map(e->e.fieldPath)
                .collect(Collectors.toSet());
    }

    @AllArgsConstructor
    public class QueryPlan {
        public Query query;
        public List<Plan> plans;
    }

    @AllArgsConstructor
    public static class Plan {
        public Index index;
        public List<Plan> children;
    }


    @AllArgsConstructor
    public static class Index {
        public Query query;
        public Set<QueryDefinition.SqlClause.Conjunction.FieldPath> merkle;
        public Set<QueryDefinition.SqlClause.Conjunction.FieldPath> bTree;
        public Set<QueryDefinition.SqlClause.Conjunction.FieldPath> remaining;
        public Entity rootEntity;
        public QueryDefinition.SqlClause sqlClause;

        public String toString() {
            return "i"+merkle.toString();
        }
    }

    @AllArgsConstructor
    public class Workload {
        List<QueryPlan> plans;
    }
}
