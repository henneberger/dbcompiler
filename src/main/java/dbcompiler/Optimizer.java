package dbcompiler;

import com.google.ortools.constraintsolver.Solver;
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

    private Set<UniqueIndex> uniqueIndices;

    public Optimizer(LogicalPlan.Workload workload) {
        this.workload = workload;
        this.allIndices = WorkloadUtil.getAllIndicies(workload.plans);
        this.uniqueIndices = new HashSet<>();

        //todo: this is incorrect, fix this soon
        Map<Index, UniqueIndex> uniqueSetMap = new HashMap<>();
        for (Index index : allIndices) {
            UniqueIndex uniqueIndex;
            if ((uniqueIndex = uniqueSetMap.get(index)) == null) {
                uniqueIndex = new UniqueIndex(index.merkle, index.bTree, index.rootEntity);
                uniqueSetMap.put(index, uniqueIndex);
                uniqueIndices.add(uniqueIndex);
            }
            index.uniqueIndex = uniqueIndex;
        }
    }


    /**
     * Find the minimum number of tables followed by the minimum cost
     */
    public int optimize() {
        MPSolver solver = MPSolver.createSolver("Optimizer", "CBC");
        for (LogicalPlan.QueryPlan plan : workload.plans) {
            System.out.println(plan.query.selections);
            for (QPlan qplan : plan.plans) {
                printCostTree("  ", qplan);
            }
        }

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
        private final Set<FieldPath> merkle;
        private final List<OrderBy> bTree;
        private final Entity rootEntity;
        public MPVariable variable;

        public UniqueIndex(Set<FieldPath> merkle, List<OrderBy> bTree, Entity rootEntity) {
            this.merkle = merkle;
            this.bTree = bTree;
            this.rootEntity = rootEntity;
        }

        @Override
        public String toString() {
            return "u"+ merkle + bTree;
        }
    }
}