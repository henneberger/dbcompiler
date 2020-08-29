package dbcompiler;

import java.util.*;

public class Cost {
    public static double infinity = java.lang.Double.POSITIVE_INFINITY;

    public static double optimal_solution_margin = 1.0;
    public static double row_scan_cost = 1.000;
    public static Map<LogicalPlan.Index, Double> indexCost = new HashMap<>();
}
