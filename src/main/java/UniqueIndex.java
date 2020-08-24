import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.Objects;
import java.util.Set;

public class UniqueIndex {
    private MPVariable var;
    private final Set<String> merkle;
    public UniqueIndex(Set<String> merkle) {
        this.merkle = merkle;
    }

    public MPVariable getOrCreateVarForIndex(MPSolver solver) {
        if (this.var == null) {
            var = solver.makeNumVar(0.0, Optimizer.infinity, this.toString());
        }
        return var;
    }

    @Override
    public String toString() {
        return "u_idx"+ merkle;
    }
}
