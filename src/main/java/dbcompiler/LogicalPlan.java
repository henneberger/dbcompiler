package dbcompiler;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

import static dbcompiler.DomainModel.*;
import static dbcompiler.DomainModel.Direction.*;

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


        Set<FieldPath> sargable = getSargablePredicates(clause);
        List<Plan> plans = new ArrayList<>();
        Set<List<OrderBy>> clusteringKeys = getClusteringKeys(clause);

        for (int i = 1; i <= sargable.size(); i++) {
            for (Set<FieldPath> comb : Sets.combinations(sargable, i)) {
                Set<FieldPath> remaining = new HashSet<>(getAllPredicates(clause));
                remaining.removeAll(comb);
                Set<FieldPath> partitionKey = new HashSet<>(comb);
                for (List<OrderBy> clusteringKey : clusteringKeys) {
                    //Migrate remaining paths in clustering key to partition key
                    for (OrderBy order : clusteringKey) {
                        if (remaining.contains(order.path)) {
                            remaining.remove(order.path);
                            partitionKey.add(order.path);
                        } else {
                            break;
                        }
                    }

                    Index index = new Index(selection.getQuery(), comb, clusteringKey, remaining, clause.rootEntity, clause, partitionKey, selection);
                    plans.add(new Plan(index, null));
                }
            }
        }
        //TODO: Add a scan as root if possible

        return plans;
    }

    /**
     * Clustering keys are ordered and cannot contain non-sargable fields.
     *  Each permutation: [1, id], [1, 2, id], [1, 2, 3, id], ...
     */
    private Set<List<OrderBy>> getClusteringKeys(QueryDefinition.SqlClause clause) {
        Set<List<OrderBy>> comb = new HashSet<>();
        Set<List<OrderBy>> orders = getOrderByForCluster(clause.rootEntity);
        for (List<OrderBy> order : orders) {
            List<OrderBy> options = new ArrayList<>();
            for (OrderBy o : order) {
                if (!o.path.isSargable()) break;
                options.add(o);
                List<OrderBy> option = new ArrayList<>(options);

                /* Add a cardinality preserving ID at the end */
                FieldPath id = getId(clause.rootEntity);
                OrderBy idOrder = new OrderBy(id, DESC);
                if (!option.contains(idOrder)) {
                    option.add(idOrder);
                }
                comb.add(option);
            }
        }


        return comb;
    }

    /**
     * All order by fields for an entity
     */
    private Set<List<OrderBy>> getOrderByForCluster(Entity rootEntity) {
        Set<List<OrderBy>> orders = new HashSet<>();
        for (Query query : model.queries) {
            for (Query.QueryDefinitionSelection selection : query.selections) {
                if (selection.definition.type.entity == rootEntity && selection.definition.sqlClause.orders != null) {
                    orders.add(selection.definition.sqlClause.orders);
                }
            }
        }
        return orders;
    }


    Map<Entity, FieldPath> entityFieldPathMap = new HashMap<>();
    private FieldPath getId(Entity rootEntity) {
        if (entityFieldPathMap.containsKey(rootEntity)) {
            return entityFieldPathMap.get(rootEntity);
        }
        Entity.Field id = null;
        for (Map.Entry<String, Entity.Field> entry : rootEntity.fieldMap.entrySet()) {
            if (entry.getValue().typeDef.typeName.equals("ID")) {
                id = entry.getValue();
                break;
            }
        }
        Preconditions.checkNotNull(id, "Entity %s must have an id", rootEntity.entityName);

        FieldPath fieldPath = new FieldPath(ImmutableList.of(id), id.name, rootEntity, true);
        entityFieldPathMap.put(rootEntity, fieldPath);
        return fieldPath;
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

    public Set<FieldPath> getSargablePredicates(QueryDefinition.SqlClause clause) {
        return clause.conjunctions.stream()
                .map(e->e.fieldPath)
                .filter(FieldPath::isSargable)
                .collect(Collectors.toSet());
    }

    public Set<FieldPath> getAllPredicates(QueryDefinition.SqlClause clause) {
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

    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class Index {
        @EqualsAndHashCode.Exclude
        public final Query query;
        public final Set<FieldPath> merkle;
        public final List<OrderBy> bTree;
        @EqualsAndHashCode.Exclude
        public final Set<FieldPath> remaining;
        public final Entity rootEntity;
        @EqualsAndHashCode.Exclude
        public final QueryDefinition.SqlClause sqlClause;
        @EqualsAndHashCode.Exclude
        public final Set<FieldPath> partitionKey;
        @EqualsAndHashCode.Exclude
        public final Query.QueryDefinitionSelection selection;
        @EqualsAndHashCode.Exclude
        public Optimizer.UniqueIndex uniqueIndex;


        public String toString() {
            return "i"+merkle.toString() + "" + bTree.toString();
        }
    }

    @AllArgsConstructor
    public class Workload {
        public List<QueryPlan> plans;
    }
}
