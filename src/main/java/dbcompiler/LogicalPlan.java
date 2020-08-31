/*
 *  Copyright (C) 2020 Daniel Henneberger
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
            //todo: has a filter cost if is list
            return null;
        }

        Set<FieldPath> sargable = getSargablePredicates(clause);
        List<QPlan> plans = new ArrayList<>();
        Set<List<OrderBy>> sargableClusteringKeys = getSargableClusteringKeys(clause);

        for (int i = 1; i <= sargable.size(); i++) {
            for (Set<FieldPath> comb : Sets.combinations(sargable, i)) {
                for (List<OrderBy> clusteringKey : sargableClusteringKeys) {
                    //Not all conjunctions can be fulfilled with sargable keys

                    Index index = new Index(rootQuery, comb, clusteringKey, clause.rootEntity, clause,  pageSize);
                    if (index.getRowScanCost() < rootQuery.sla.latency_ms) {
                        plans.add(new QPlan(index, null));
                    }
                }
            }
        }

        return plans;
    }

    public static Set<FieldPath> getPrimaryKey(Set<FieldPath> comb, List<OrderBy> clusteringKey, QueryDefinition.SqlClause clause) {
        Set<FieldPath> paths = clause.conjunctions.stream().map(e->e.fieldPath).collect(Collectors.toSet());
        for (FieldPath m : comb) {
            paths.remove(m);
        }

        for (OrderBy orderBy : clusteringKey) {
            if (paths.contains(orderBy.path)) {
                paths.remove(orderBy.path);
            } else {
                break;
            }
        }
        return paths;
    }

    /**
     * Clustering keys are ordered and cannot contain non-sargable fields.
     *  Each permutation: [1, id], [1, 2, id], [1, 2, 3, id], ...
     */
    private Set<List<OrderBy>> getSargableClusteringKeys(QueryDefinition.SqlClause clause) {
        Set<List<OrderBy>> comb = new HashSet<>();
        Set<List<OrderBy>> orders = getOrderByForCluster(clause.rootEntity);
        for (List<OrderBy> order : orders) {
            List<OrderBy> options = new ArrayList<>();
            for (OrderBy o : order) {
                if (!isSargable(o.path)) break;
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
                if (selection.definition.type.getEntity() == rootEntity && selection.definition.sqlClause.orders != null) {
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
        Set<FieldPath> fieldPaths = clause.conjunctions.stream()
                .map(e->e.fieldPath)
                .filter(LogicalPlan::isSargable)
                .collect(Collectors.toSet());

        return fieldPaths;
    }

    public static boolean isSargable(FieldPath e) {
        for (Entity.Field field : e.fields) {
            if (field.typeDef.getEntity() == null) {
                return field.typeDef.nonnull;
            } else if (!field.typeDef.nonnull){
                return false;
            }
        }

        return false;
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
        public final Set<FieldPath> partitionKey;
        public final List<OrderBy> clusteringKey;
        public final Entity rootEntity;
        @EqualsAndHashCode.Exclude
        public final QueryDefinition.SqlClause sqlClause;
        @EqualsAndHashCode.Exclude
        public final int pageSize;
        @EqualsAndHashCode.Exclude
        public Optimizer.UniqueIndex uniqueIndex;
        public MPVariable variable;



        public String toString() {
            return "i:query:"+query.name+ partitionKey.toString() + "" + clusteringKey.toString();
        }

        public double getRowScanCost() {
            double sortCost = calculateSortRowSize(rootEntity, partitionKey, clusteringKey, sqlClause.orders);
            double filterCost = calculateFilterRowSize(rootEntity, partitionKey, clusteringKey, sqlClause);
            return Math.max(filterCost * Cost.row_scan_cost,
                    sortCost * Cost.row_scan_cost);
        }

        private double calculateFilterRowSize(Entity entity, Set<FieldPath> merkle, List<OrderBy> bTree, QueryDefinition.SqlClause clause) {
            Set<FieldPath> paths = getPrimaryKey(merkle, bTree, clause);

            if (paths.isEmpty()) {
                return 1;
            } else {
                Selectivity selectivity = entity.selectivityMap.get(paths);
                Preconditions.checkNotNull(selectivity, "Selectivity needed for %s", paths);

                //Row scan cost and then query cost
                //This can get a bit complicated for scalars we can't denormalize

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
