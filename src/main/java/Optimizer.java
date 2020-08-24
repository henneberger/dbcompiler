import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Optimizer {
    static {
        System.loadLibrary("jniortools");
    }
    public static double infinity = java.lang.Double.POSITIVE_INFINITY;
    private final List<SqlClause.Index> allIndices;
    private final Set<DomainModel.QuerySelection> rootSelections;
    private final List<Plan> allPlans;
    private final Set<UniqueIndex> uniqueIndices;
    private final Map<SqlClause.Index, UniqueIndex> uniqueIndexMap;
    private double optimal_solution_margin = 1.1; //10%

    public Optimizer(Set<UniqueIndex> uniqueIndices, Map<SqlClause.Index, UniqueIndex> uniqueIndexMap, List<SqlClause.Index> allIndices, Set<DomainModel.QuerySelection> rootSelections, List<Plan> allPlans) {
        this.uniqueIndices = uniqueIndices;
        this.uniqueIndexMap = uniqueIndexMap;
        this.allIndices = allIndices;
        this.rootSelections = rootSelections;
        this.allPlans = allPlans;
    }

    //1 <= x1 + x2 <= inf
    public void optimize() {
        MPSolver solver = MPSolver.createSolver("Optimizer", "CBC");

        /*
         * Generate index variables: x1, x2, x3, ...
         */
        for (UniqueIndex index : uniqueIndices) {
            index.getOrCreateVarForIndex(solver);
        }

        /*
         * Generate index+query variables: x1q1, x2q1, x3q1, ...
         *
         */
        for (DomainModel.QuerySelection selection : rootSelections) {
            SqlClause clause = selection.getDefinition().getSqlClause();
            for (SqlClause.Index index : clause.getAllIndicies()) {
                /*
                 * Assign index & index+query constraints: x1q1 <= x1, x2q1 <= x2, ...
                 */
                UniqueIndex uniqueIndex = uniqueIndexMap.get(index);
                MPVariable queryIndexVariable = uniqueIndex.getOrCreateVarForIndex(solver);
                MPConstraint constraint = solver.makeConstraint(0, infinity);
                constraint.setCoefficient(queryIndexVariable, 1);
                MPVariable indexVariable = index.getOrCreateVarForIndex(solver);
                constraint.setCoefficient(indexVariable, -1);
            }
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
        for (Plan plan : allPlans) {
            plan.visit(new PlanPathVisitor(solver));
        }

        /*
         * Generate the cost object function:
         *  min(freq * cost * x1q1, ...)
         */
        MPObjective objective = solver.objective();
        for (SqlClause.Index index : allIndices) {
            MPVariable queryIndexVariable = index.getOrCreateVarForIndex(solver);
            objective.setCoefficient(queryIndexVariable,
                    index.getQueryFrequency() * index.getCost()
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

        MPConstraint optimal = solver.makeConstraint(0, solver.objective().value() * optimal_solution_margin, "total_cost");
        for (SqlClause.Index index : allIndices) {
            MPVariable queryIndexVariable = index.getOrCreateVarForIndex(solver);

            optimal.setCoefficient(queryIndexVariable,
                    index.getQueryFrequency() * index.getCost()
            );
        }

        objective.clear();
        /*
         * Rerun function to find minimum number of indicies with previous solution as constraint
         * x1 + x2
         */
        for (UniqueIndex index : uniqueIndices) {
            objective.setCoefficient(index.getOrCreateVarForIndex(solver), 1);
        }
        objective.setMinimization();

        solveAndPrint(solver);

    }

    class PlanPathVisitor implements PlanVisitor {
        private MPSolver solver;
        public PlanPathVisitor(MPSolver solver) {
            this.solver = solver;
        }

        @Override
        public void visit(Plan plan) {
            //1 <= x1q2 + x2q2 <= inf
            MPConstraint constraint = solver.makeConstraint(1, infinity);
            for (Plan child : plan.getChildren()) {
                constraint.setCoefficient(child.getIndex().getOrCreateVarForIndex(solver), 1);
            }
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

        for (SqlClause.Index index : allIndices) {
            System.out.println(index.toString() + " = " + index.getOrCreateVarForIndex(solver).solutionValue());
        }
        for (UniqueIndex index : uniqueIndices) {
            System.out.println(index.toString() + " = " + index.getOrCreateVarForIndex(solver).solutionValue());
        }
    }
}