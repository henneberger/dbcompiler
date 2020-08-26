package dbcompiler;

import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Map;

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
        //Cost:
        //  Frequency of query * cost of index lookup & scan
        //Cost of index lookup:
        // 1 * (scan) Number of elements in partition
        if (!index.merkle.isEmpty() && index.remaining.isEmpty()) {
            return 1;
        }
        return getScanCost(index);
    }

    public static double getScanCost(LogicalPlan.Index index) {
        if (index.merkle.isEmpty()) {
            //Scan cost is likelihood we'll find (remaining) scalars.
            //e.g. a boolean can be found quickly
            return index.rootEntity.size.max * Cost.row_scan_cost;
        }
        return estimatePartitionSize(index) * Cost.row_scan_cost;
    }

    public static int estimatePartitionSize(LogicalPlan.Index index) {
        DomainModel.QueryDefinition.SqlClause.Conjunction.FieldPath field = index.merkle.iterator().next();

        if (field.fields.size() > 1) {
            return 1;
        }

        DomainModel.Entity.Field f = index.rootEntity.fieldMap.get(field.fields.get(0).name);
        Preconditions.checkNotNull(f, "Field is missing: {}", field);
        return f.selectivity.estimate;
    }
}
