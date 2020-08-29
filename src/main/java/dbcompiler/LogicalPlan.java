package dbcompiler;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.ortools.linearsolver.MPVariable;
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
            for (Query.QueryDefinitionSelection selection : query.selections) {
                QueryPlan plan = new QueryPlan(query, permute(query, selection.definition.sqlClause, selection.pageSize));
                if (plan.plans == null) continue;
                plans.add(plan);
            }
        }
        return new Workload(plans);
    }

    public List<QPlan> permute(Query rootQuery, QueryDefinition.SqlClause clause, int pageSize) {
        /*
         * Queries with a generated ID on its root path can always be found with a direct lookup
         */
        if (hasRootId(clause)) {
            return null;
        }


        Set<FieldPath> sargable = getSargablePredicates(clause);
        List<QPlan> plans = new ArrayList<>();
        Set<List<OrderBy>> clusteringKeys = getClusteringKeys(clause);

        for (int i = 1; i <= sargable.size(); i++) {
            for (Set<FieldPath> comb : Sets.combinations(sargable, i)) {
                Set<FieldPath> remaining = new HashSet<>(getAllPredicates(clause));
                remaining.removeAll(comb);
                for (List<OrderBy> clusteringKey : clusteringKeys) {
                    Set<FieldPath> partitionKey = new HashSet<>(comb);
                    //Migrate remaining paths in clustering key to partition key
                    for (OrderBy order : clusteringKey) {
                        if (remaining.contains(order.path)) {
                            partitionKey.add(order.path);
                            remaining.remove(order.path);
                        } else if (clause.orders.contains(order)) {
                            partitionKey.add(order.path);
                        } else {
                            break;
                        }
                    }

                    Index index = new Index(rootQuery, comb, clusteringKey, remaining, clause.rootEntity, clause, partitionKey, pageSize);
                    if (index.getRowScanCost() < rootQuery.sla.latency_ms) {
                        plans.add(new QPlan(index, null));
                    }
                }
            }
        }

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
        public List<QPlan> plans;
    }

    @AllArgsConstructor
    public static class QPlan {
        public Index index;
        public List<QPlan> children;
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
        public final Set<FieldPath> primaryKey;
        @EqualsAndHashCode.Exclude
        public final int pageSize;
        @EqualsAndHashCode.Exclude
        public Optimizer.UniqueIndex uniqueIndex;
        public MPVariable variable;


        public String toString() {
            return "i:query:"+query.name+merkle.toString() + "" + bTree.toString();
        }

        public double getRowScanCost() {

            double sortCost = calculateSortRowSize(rootEntity, merkle, bTree, sqlClause.orders);
            double filterCost = calculateFilterRowSize(rootEntity, merkle, bTree, sqlClause.conjunctions);
            return Math.max(filterCost * Cost.row_scan_cost,
                    sortCost * Cost.row_scan_cost);
        }

        private double calculateFilterRowSize(Entity entity, Set<FieldPath> merkle, List<OrderBy> bTree, List<QueryDefinition.SqlClause.Conjunction> conjunctions) {
            Set<FieldPath> paths = conjunctions.stream().map(e->e.fieldPath).collect(Collectors.toSet());
            for (FieldPath m : merkle) {
                paths.remove(m);
            }

            for (OrderBy orderBy : bTree) {
                if (paths.contains(orderBy.path)) {
                    paths.remove(orderBy.path);
                } else {
                    break;
                }
            }

            if (paths.isEmpty()) {
                return 1;
            } else {
                Selectivity selectivity = entity.selectivityMap.get(paths);
                Preconditions.checkNotNull(selectivity, "Selectivity needed for %s", paths);

                return Math.min(selectivity.distinct, (int)1.645 * (pageSize / (1d / 2/*boolean*/)));
            }
        }

        private double calculateSortRowSize(Entity entity, Set<FieldPath> merkle, List<OrderBy> bTree, List<OrderBy> orders) {
            if (orders.isEmpty()) return 0;
            Set<FieldPath> all = new HashSet<>(merkle);
            int idx = -2;
            for (int i = 0; i < bTree.size() && i < orders.size(); i++) { //b-tree always has the last element as ID
                OrderBy bTreeOrder = bTree.get(i);
                OrderBy order = orders.get(i);
                if (!bTreeOrder.equals(order)) {
                    break;
                }
                idx = i;
                all.add(bTreeOrder.path);
            }

            if ((idx + 1)== orders.size()) {
                return 0; //all satisfied
            } else {
                Selectivity selectivity = entity.selectivityMap.get(all);
                Preconditions.checkNotNull(selectivity, "Selectivity needed for %s", all);

                return selectivity.distinct;
            }
        }

    }

    @AllArgsConstructor
    public class Workload {
        public List<QueryPlan> plans;
    }
}
