package dbcompiler;

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
    private MPSolver solver;

    private List<Index> allIndices;
    private Set<UniqueIndex> uniqueIndices;
    private Map<UniqueIndex, MPVariable> uniqueIndexVarMap = new HashMap<>();
    private Map<Index, MPVariable> indexVarMap = new HashMap<>();

    private Cost cost = new Cost();

    public Optimizer(LogicalPlan.Workload workload) {
        this.workload = workload;
        this.solver = MPSolver.createSolver("Optimizer", "CBC");
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


    //1 <= x1 + x2 <= inf
    public void optimize() {
        /*
         * Generate index variables: x1, x2, x3, ...
         */
        for (UniqueIndex index : uniqueIndices) {
            MPVariable variable = solver.makeBoolVar("u"+UUID.randomUUID().toString().substring(0, 4));
            uniqueIndexVarMap.put(index, variable);
        }

        /*
         * Generate index+query variables: x1q1, x2q1, x3q1, ...
         *
         */
        for (Index index : allIndices) {
            /*
             * Assign index & index+query constraints: x1q1 <= x1, x2q1 <= x2, ...
             */
            MPVariable queryIndexVariable = uniqueIndexVarMap.get(index.uniqueIndex);
            MPConstraint constraint = solver.makeConstraint(0, Cost.infinity);
            constraint.setCoefficient(queryIndexVariable, 1);
            MPVariable indexVariable = solver.makeBoolVar("i"+UUID.randomUUID().toString().substring(0, 4));
            indexVarMap.put(index, indexVariable);

            constraint.setCoefficient(indexVariable, -1);
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
            setPathConstraints(queryPlan.plans);
        }

        /*
         * Generate the cost object function:
         *  min(freq * cost * x1q1, ...)
         */
        MPObjective objective = solver.objective();
        for (Index index : allIndices) {
            MPVariable queryIndexVariable = indexVarMap.get(index);
            objective.setCoefficient(queryIndexVariable,
                    index.query.sla.throughput_per_second * cost.indexCost(index)
            );
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

        /*
         * Set solution as total cost
         * prev_solution <= freq * cost * x1q1, ...
         */

        MPConstraint optimal = solver.makeConstraint(0, solver.objective().value() * Cost.optimal_solution_margin, "total_cost");
        for (Index index : allIndices) {
            MPVariable queryIndexVariable = indexVarMap.get(index);

            optimal.setCoefficient(queryIndexVariable,
                    index.query.sla.throughput_per_second * cost.indexCost(index)
            );
        }

        objective.clear();
        /*
         * Rerun function to find minimum number of indicies with previous solution as constraint
         * x1 + x2
         */
        for (UniqueIndex index : uniqueIndices) {
            objective.setCoefficient(uniqueIndexVarMap.get(index), 1);
        }
        objective.setMinimization();

        solveAndPrint(solver);

    }

    public void setPathConstraints(List<Plan> plan) {
        //1 <= x1q2 + x2q2 <= inf
        MPConstraint constraint = solver.makeConstraint(1, Cost.infinity);
        for (Plan child : plan) {
            constraint.setCoefficient(indexVarMap.get(child.index), 1);
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
            System.out.println(index.toString() + " = " + indexVarMap.get(index).solutionValue() +"    " +cost.indexCost(index));
        }
        for (UniqueIndex index : uniqueIndices) {
            System.out.println(index.toString() + " = " + uniqueIndexVarMap.get(index).solutionValue());
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