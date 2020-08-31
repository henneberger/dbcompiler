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

import com.google.common.collect.ArrayListMultimap;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.*;
import static dbcompiler.LogicalPlan.*;
import static dbcompiler.DomainModel.*;

public class Optimizer {
    static {
        System.loadLibrary("jniortools");
    }

    private LogicalPlan.Workload workload;

    private List<Index> allIndices;
    private final DomainModel model;

    private Set<UniqueIndex> uniqueIndices;
    public static double infinity = java.lang.Double.POSITIVE_INFINITY;

    public Optimizer(LogicalPlan.Workload workload, DomainModel model) {
        this.workload = workload;
        this.allIndices = getAllIndicies(workload.plans);
        this.model = model;
        this.uniqueIndices = new HashSet<>();

        Map<Index, UniqueIndex> uniqueSetMap = new HashMap<>();
        for (Index index : allIndices) {
            UniqueIndex uniqueIndex;
            if ((uniqueIndex = uniqueSetMap.get(index)) == null) {
                uniqueIndex = new UniqueIndex(index.partitionKey, index.clusteringKey, index.rootEntity);
                uniqueSetMap.put(index, uniqueIndex);
                uniqueIndices.add(uniqueIndex);
            }
            index.uniqueIndex = uniqueIndex;
        }
    }

    public void printPlan() {
        System.out.println("Plan:");
        for (LogicalPlan.QueryPlan plan : workload.plans) {
            System.out.println(plan.query.selections);
            for (QPlan qplan : plan.plans) {
                printCostTree("  ", qplan);
            }
        }
        System.out.println();
    }


    public void findBestPlan() {
        MPSolver solver = MPSolver.createSolver("Optimizer", "CBC");
        printPlan();

        /*
         * Generate index variables: x1, x2, x3, ...
         */
        for (UniqueIndex index : uniqueIndices) {
            index.variable = solver.makeBoolVar("u" + index.toString());
        }

        /*
         * Generate index+query variables: x1q1, x2q1, x3q1, ...
         */
        for (Index index : allIndices) {
            index.variable = solver.makeBoolVar(index.toString());
        }

        /*
         * Associate unique index with query index: x1 >= x1q1
         */
        for (Index index : allIndices) {
            MPConstraint constraint = solver.makeConstraint(0, infinity);
            constraint.setCoefficient(index.uniqueIndex.variable, 1);
            constraint.setCoefficient(index.variable, -1);
        }

        createMutationCostConstraint(solver);

        /*
         * Assign Path constraints:
         * e.g.
         * q1:
         *  x1
         *  x2:
         *    x3
         * q1:
         *  x1
         *  x2
         *
         * result:
         *   x1q1 + x2q1 >= 1, x2q1 <= x3q1
         *   x1q2 + x2q2 >= 1
         */
        for (LogicalPlan.QueryPlan queryPlan : workload.plans) {
            setPathConstraintsForIndex(solver, queryPlan.plans);
        }

        /*
         * Find minimum total cost
         */
        MPObjective objective = solver.objective();
        for (Index index : allIndices) {
            objective.setCoefficient(index.variable,
                    index.getRowScanCost() * index.query.sla.throughput_per_second
            );
        }
        objective.setMinimization();

        solveAndPrint(solver);
    }

    private void createMutationCostConstraint(MPSolver solver) {
        //For each mutation in workload
        //Get all unique indexes that satisfy the workload
        //1 <= x1 + x2 + ... <= max_cost
        // e.g.
        // x1: todo + user
        // x2: todo
        // x3: user

        ArrayListMultimap<Entity, UniqueIndex> entityMap = ArrayListMultimap.create();
        for (UniqueIndex index : uniqueIndices) {
            entityMap.put(index.rootEntity, index);
        }

        for (Mutation mutation : model.mutations) {
            if (mutation.mutationType != MutationType.INSERT) continue;
            List<UniqueIndex> uniqueIndices = entityMap.get(mutation.entity);
            if (uniqueIndices.size() > 0) { //todo fix
                MPConstraint constraint = solver.makeConstraint(1, mutation.sla.max_tables, mutation.name + "_max_tables");
                for (UniqueIndex index : uniqueIndices) {
                    constraint.setCoefficient(index.variable, 1);
                }
            }
        }
    }

    public void setPathConstraintsForIndex(MPSolver solver, List<QPlan> plan) {
        //1 <= x1q2 + x2q2 <= inf
        MPConstraint constraint = solver.makeConstraint(1, infinity);
        for (QPlan child : plan) {
            constraint.setCoefficient(child.index.variable, 1);
        }
    }

    public void printCostTree(String prefix, QPlan plan){
        System.out.println(prefix + plan.index.toString() + ":" + plan.index.getRowScanCost());
        if (plan.children == null) return;
        for (QPlan child : plan.children) {
            printCostTree(prefix + "  ", child);
        }
    }

    public void solveAndPrint(MPSolver solver) {
        System.out.println("Number of variables = " + solver.numVariables());
        System.out.println("Number of constraints = " + solver.numConstraints());

        String model = solver.exportModelAsLpFormat();
        System.out.println(model);

        final MPSolver.ResultStatus resultStatus = solver.solve();

        // Check that the problem has an optimal solution.
        if (resultStatus == MPSolver.ResultStatus.INFEASIBLE) {
            System.err.println("The problem does not have an optimal solution!");
            return;
        }

        // Verify that the solution satisfies all constraints (when using solvers
        // others than GLOP_LINEAR_PROGRAMMING, this is highly recommended!).
        if (!solver.verifySolution(/*tolerance=*/1e-7, /* log_errors= */ true)) {
            System.err.println("The solution returned by the solver violated the"
                    + " problem constraints by at least 1e-7");
            return;
        }

        System.out.println("Problem solved in " + solver.wallTime() + " milliseconds");

        // The objective value of the solution.
        System.out.println("Optimal objective value = " + solver.objective().value());

        for (Index index : allIndices) {
            System.out.println(index.toString() + " = " + index.variable.solutionValue() + "    ");
        }

        System.out.println("\nTables: ");
        for (UniqueIndex index : uniqueIndices) {
            System.out.println(index.toString() + " = " + index.variable.solutionValue());
        }
    }

    private static List<Index> getAllIndicies(List<LogicalPlan.QueryPlan> queries) {
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

    public static class UniqueIndex {
        private final Set<FieldPath> partitionKey;
        private final List<OrderBy> clusteringKey;
        private final Entity rootEntity;
        public MPVariable variable;

        public UniqueIndex(Set<FieldPath> partitionKey, List<OrderBy> clusteringKey, Entity rootEntity) {
            this.partitionKey = partitionKey;
            this.clusteringKey = clusteringKey;
            this.rootEntity = rootEntity;
        }

        @Override
        public String toString() {
            return "" + partitionKey + clusteringKey;
        }
    }
}