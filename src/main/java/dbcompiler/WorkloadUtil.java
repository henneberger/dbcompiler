package dbcompiler;

import java.util.ArrayList;
import java.util.List;

import static dbcompiler.LogicalPlan.*;

public class WorkloadUtil {
    public static List<Index> getAllIndicies(List<LogicalPlan.QueryPlan> queries) {
        List<Index> allIndicies = new ArrayList<>();
        for (LogicalPlan.QueryPlan queryPlan : queries) {
            for (QPlan plan : queryPlan.plans) {
                getAllIndicies(plan, allIndicies);
            }
        }
        return allIndicies;
    }

    private static void getAllIndicies(QPlan plan, List<Index> allIndicies) {
        if (plan.index != null) {
            allIndicies.add(plan.index);
        }
        if (plan.children == null) return;
        for (QPlan child : plan.children) {
            getAllIndicies(child, allIndicies);
        }
    }
}
