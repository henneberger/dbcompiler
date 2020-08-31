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

    public Optimizer(LogicalPlan.Workload workload, DomainModel model) {
        this.workload = workload;
        this.allIndices = WorkloadUtil.getAllIndicies(workload.plans);
        this.model = model;
        this.uniqueIndices = new HashSet<>();

        //todo: this is incorrect, fix this soon
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
        for (LogicalPlan.QueryPlan plan : workload.plans) {
            System.out.println(plan.query.selections);
            for (QPlan qplan : plan.plans) {
                printCostTree("  ", qplan);
            }
        }
    }


    public void findBestPlan() {
        MPSolver solver = MPSolver.createSolver("Optimizer", "CBC");
        printPlan();

        for (UniqueIndex index : uniqueIndices) {
            index.variable = solver.makeBoolVar("u" + index.toString());
        }

        for (Index index : allIndices) {
            index.variable = solver.makeBoolVar(index.toString());
        }

        //Associate unique with index var
        for (Index index : allIndices) {
            MPConstraint constraint = solver.makeConstraint(0, Cost.infinity);
            constraint.setCoefficient(index.uniqueIndex.variable, 1);
            constraint.setCoefficient(index.variable, -1);
        }

        createMutationCostConstraint(solver);
        createTotalSizeConstraint(solver);

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
         * Find minimum cluster cost
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

    private void createTotalSizeConstraint(MPSolver solver) {
        //Total size constraint:
        // For each unique index, estimate size
        // 0 <= size_estimate_x1 * x1 + size_estimate_x2 * x2 + ... <= max_size
    }

    private void createMutationCostConstraint(MPSolver solver) {
        //For each mutation in workload
        //Get all unique indexes that satisfy the workload
        //1 <= x1 + x2 + ... <= max_cost
        // e.g.
        // x1: todo + user
        // x2: todo
        // x3: user

        //Only do first entity for now.. Need better graph queries to represent this stuff
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

    /**
     * Find the minimum number of tables followed by the minimum cost
     */
    public int solveMinTables() {
        MPSolver solver = MPSolver.createSolver("Optimizer", "CBC");


        /*
         * Generate index variables: x1, x2, x3, ...
         */
        for (UniqueIndex index : uniqueIndices) {
            index.variable = solver.makeBoolVar("u" + index.toString());
        }

        for (Index index : allIndices) {
            MPVariable uniqueVariable = index.uniqueIndex.variable;
            MPVariable indexVariable = solver.makeBoolVar(index.toString());

            MPConstraint constraint = solver.makeConstraint(0, Cost.infinity);
            constraint.setCoefficient(uniqueVariable, 1);
            constraint.setCoefficient(indexVariable, -1);
            index.variable = indexVariable;
        }

        for (LogicalPlan.QueryPlan queryPlan : workload.plans) {
            setPathConstraintsForIndex(solver, queryPlan.plans);
        }

        /*
         * Generate the cost object function:
         *  min(freq * cost * x1q1, ...)
         */
        MPObjective objective = solver.objective();
        for (UniqueIndex index : uniqueIndices) {
            objective.setCoefficient(index.variable, 1);
        }
        objective.setMinimization();

        /*
         * Set a size cost:
         *   size * x1 + size * x2 + ... < total_size
         */

        /*
         * Set a maximum write cost:
         *   for each entity's table
         *   x1 + x3 + ... < write_limit_per_table
         */

        solveAndPrint(solver);
        next((int)solver.objective().value());
        return (int)solver.objective().value();
    }
    public void next(int minTables) {

        MPSolver solver = MPSolver.createSolver("Optimizer", "CBC");

        MPConstraint minTableConstraint = solver.makeConstraint(minTables, minTables);
        for (UniqueIndex index : uniqueIndices) {
            index.variable = solver.makeBoolVar("u"+index.toString());
            minTableConstraint.setCoefficient(index.variable, 1);
        }
        /*
         * Generate index+query variables: x1q1, x2q1, x3q1, ...
         *
         */
        for (Index index : allIndices) {
            /*
             * Assign index & index+query constraints: x1q1 <= x1, x2q1 <= x2, ...
             * 0 <= -x1 + x1q1 <= inf
             */
            MPVariable uniqueVariable = index.uniqueIndex.variable;
            MPVariable indexVariable = solver.makeBoolVar(index.toString());

            MPConstraint constraint = solver.makeConstraint(0, Cost.infinity);
            constraint.setCoefficient(uniqueVariable, 1);
            constraint.setCoefficient(indexVariable, -1);
            index.variable = indexVariable;
        }

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
         * Set solution as total cost
         * prev_solution <= freq * cost * x1q1, ...
         */

        /*
         * Find minimum cluster cost
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

    public void setPathConstraintsForIndex(MPSolver solver, List<QPlan> plan) {
        //1 <= x1q2 + x2q2 <= inf
        MPConstraint constraint = solver.makeConstraint(1, Cost.infinity);
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
        for (UniqueIndex index : uniqueIndices) {
            System.out.println(index.toString() + " = " + index.variable.solutionValue());
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
            return "u"+ partitionKey + clusteringKey;
        }
    }
}