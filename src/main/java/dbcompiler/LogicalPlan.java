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

    public List<Index> search() {
        List<Index> allIndices = new ArrayList<>();
        Set<Set<SqlClause.Conjunction.FieldPath>> merkle = new HashSet<>();
        List<Plan> allPlans = new ArrayList<>();
        for (Map.Entry<DomainModel.Query.QueryDefinitionSelection, DomainModel.Query> selection : expandClauses(model.queries).entrySet()) {
            SqlClause clause = selection.getKey().definition.sqlClause;
            PermuteResult result = permute(clause, selection.getValue());
            if (result.plan == null) continue;
            allPlans.add(result.plan);
            allIndices.addAll(result.indices);
            for (Index index : result.indices) { //todo remove permute result
                merkle.add(index.merkle);
            }
        }

        Set<UniqueIndex> uniqueIndices = new HashSet<>();
        Map<Set<SqlClause.Conjunction.FieldPath>, UniqueIndex> uniqueSetMap = new HashMap<>();
        Map<Index, UniqueIndex> uniqueIndexMap = new HashMap<>();

        for (Index index : allIndices) {
            UniqueIndex uniqueIndex;
            if ((uniqueIndex = uniqueSetMap.get(index.merkle)) == null) {
                uniqueIndex = new UniqueIndex(index.merkle);
                uniqueSetMap.put(index.merkle, uniqueIndex);
                uniqueIndices.add(uniqueIndex);
            }

            uniqueIndexMap.put(index, uniqueIndex);
        }

        new Optimizer(uniqueIndices, uniqueIndexMap, allIndices, allPlans).optimize();

        return null;

    }

    private Map<DomainModel.Query.QueryDefinitionSelection, DomainModel.Query> expandClauses(List<DomainModel.Query> queries) {
        Map<DomainModel.Query.QueryDefinitionSelection, DomainModel.Query> map = new HashMap<>();

        for (DomainModel.Query q : queries) {
            for (DomainModel.Query.QueryDefinitionSelection selection : q.selections) {
                map.put(selection, q);
            }
        }

        return map;
    }

    public PermuteResult permute(SqlClause clause, Query query) {
        /*
         * Queries with a generated ID on its root path can always be found with a direct lookup
         */
        if (hasRootId(clause)) {
            return new PermuteResult(ImmutableSet.of(), null);
        }

        Plan plan = new Plan();

        Set<SqlClause.Conjunction.FieldPath> sargable = getSargablePredicates(clause);
        Set<Index> indices = new HashSet<>();

        for (int i = 0; i <= sargable.size(); i++) {
            for (Set<SqlClause.Conjunction.FieldPath> comb : Sets.combinations(sargable, i)) {
                Set<SqlClause.Conjunction.FieldPath> remaining = new HashSet<>(getAllPredicates(clause));
                remaining.removeAll(comb);
                Index index = new Index(query, comb, ImmutableSet.of(), remaining, clause.rootEntity, clause);
                indices.add(index);
                Plan childPlan = new Plan();
                childPlan.index = index;
                plan.children.add(childPlan);
            }
        }

        return new PermuteResult(indices, plan);
    }

    private boolean hasRootId(SqlClause clause) {
        for (SqlClause.Conjunction conjunction : clause.conjunctions) {
            if (conjunction.fieldPath.fields.get(0).typeDef.typeName.equals("ID")) {
                return true;
            }
        }
        return false;
    }

    public class PermuteResult {
        public final Set<Index> indices;
        public final Plan plan;

        public PermuteResult(Set<Index> indices, Plan plan) {
            this.indices = indices;
            this.plan = plan;
        }
    }


    public Set<SqlClause.Conjunction.FieldPath> getSargablePredicates(SqlClause clause) {
        Set<SqlClause.Conjunction.FieldPath> sargable = new HashSet<>();
        for (SqlClause.Conjunction conjunction : clause.conjunctions) {
            sargable.add(conjunction.fieldPath);
        }

        return sargable;
    }

    public Set<SqlClause.Conjunction.FieldPath> getAllPredicates(SqlClause clause) {
        return clause.conjunctions.stream()
                .map(e->e.fieldPath)
                .collect(Collectors.toSet());
    }

    public static class Plan {
        public Index index;
        public List<Plan> children = new ArrayList<>();
    }

    public static class UniqueIndex {
        private final Set<SqlClause.Conjunction.FieldPath> merkle;
        public UniqueIndex(Set<SqlClause.Conjunction.FieldPath> merkle) {
            this.merkle = merkle;
        }

        @Override
        public String toString() {
            return "u_idx"+ merkle;
        }
    }

    @AllArgsConstructor
    public static class Index {
        public Query query;
        public Set<SqlClause.Conjunction.FieldPath> merkle;
        public Set<SqlClause.Conjunction.FieldPath> bTree;
        public Set<SqlClause.Conjunction.FieldPath> remaining;
        public Entity rootEntity;
        public SqlClause sqlClause;

        public String toString() {
            return "i"+merkle.toString();
        }
    }
}
