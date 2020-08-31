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
