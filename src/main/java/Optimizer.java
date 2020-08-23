import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;

import java.util.List;
import java.util.Set;

public class Optimizer {
    static {
        System.loadLibrary("jniortools");
    }
    public static double infinity = java.lang.Double.POSITIVE_INFINITY;
    private final Set<SqlClause.Plan> allPlans;
    private final List<DomainModel.QuerySelection> rootSelections;

    public Optimizer(Set<SqlClause.Plan> allPlans, List<DomainModel.QuerySelection> rootSelections) {
        this.allPlans = allPlans;
        this.rootSelections = rootSelections;
    }

    public void optimize() {
        MPSolver solver = MPSolver.createSolver("IntegerProgramming", "GLOP");

        for (DomainModel.QuerySelection selection : rootSelections) {
            //1 <= x1 + x2 <= inf
            MPConstraint c0 = solver.makeConstraint(1, infinity);

            SqlClause clause = selection.getDefinition().getSqlClause();
            for (SqlClause.Plan plan : allPlans) {
                if (clause.getAllPredicates().containsAll(plan.getMerkle())) {
                    c0.setCoefficient(plan.getOrCreateVariable(solver), 1);
                }
            }
        }


        MPObjective objective = solver.objective();
        for (SqlClause.Plan plan : allPlans) {
            objective.setCoefficient(plan.getOrCreateVariable(solver), plan.getCost());
        }
        objective.setMinimization();


        System.out.println("Number of variables = " + solver.numVariables());
        System.out.println("Number of constraints = " + solver.numConstraints());

        String model = solver.exportModelAsLpFormat();
        System.out.println(model);

        final MPSolver.ResultStatus resultStatus = solver.solve();

        // Check that the problem has an optimal solution.
        if (resultStatus != MPSolver.ResultStatus.OPTIMAL) {
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

        for (SqlClause.Plan plan : allPlans) {
            System.out.println("["+plan.getMerkle().toString() + "] = " + plan.getOrCreateVariable(solver).solutionValue());
        }

    }
}
