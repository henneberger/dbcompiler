package dbcompiler;

import com.google.common.base.Preconditions;

import java.util.*;

public class Cost {
    public static double infinity = java.lang.Double.POSITIVE_INFINITY;

    public static double optimal_solution_margin = 1.1; //10%
    public static double row_scan_cost = 1.005;
    public static Map<LogicalPlan.Index, Double> indexCost = new HashMap<>();

    public double indexCost(LogicalPlan.Index index) {
        Double cost;
        if ((cost = indexCost.get(index)) != null) {
            return cost;
        }

        cost = computeIndexCost(index);
        indexCost.put(index, cost);
        return cost;
    }

    public static double computeIndexCost(LogicalPlan.Index index) {
        //If clause is fully satisfied by partition key: 1
        //If clause is partially satisfied: estimatePartitionSize(merkle) * row_scan_cost
        //If clause has an order, we must read all things, otherwise we need to read the expected number of things
        Set<DomainModel.FieldPath> conjPath = new HashSet<>();
        Set<DomainModel.FieldPath> ineqPath = new HashSet<>();
        for (DomainModel.QueryDefinition.SqlClause.Conjunction conjunction : index.sqlClause.conjunctions) {
            if (conjunction.op == DomainModel.QueryDefinition.SqlClause.Conjunction.Op.eq) {
                conjPath.add(conjunction.fieldPath);
            } else {
                ineqPath.add(conjunction.fieldPath);
            }
        }

        if (index.remaining.isEmpty()) {
            return 1;
        }

        return estimatePartitionSize(index) * Cost.row_scan_cost;
    }

    private static double estimatePartitionSize(LogicalPlan.Index index) {
        DomainModel.Selectivity selectivity = index.rootEntity.selectivityMap.get(index.partitionKey);
        Preconditions.checkNotNull(selectivity, "Selectivity required for %s", index.partitionKey);
        return Math.max(index.rootEntity.size.max,
                selectivity.distribution.calculateExpected(index.selection.pageSize, selectivity.distinct));
    }
}
