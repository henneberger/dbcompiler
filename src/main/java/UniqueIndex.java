import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class UniqueIndex {
    private MPVariable var;
    private final Set<String> merkle;
    public UniqueIndex(Set<String> merkle) {
        this.merkle = merkle;
    }

    public MPVariable getOrCreateVarForIndex(MPSolver solver) {
        if (this.var == null) {
//            var = solver.makeIntVar(0.0, Optimizer.infinity, this.toString());
            var = solver.makeBoolVar("u"+UUID.randomUUID().toString().substring(0, 4));
        }
        return var;
    }

    @Override
    public String toString() {
        return "u_idx"+ merkle;
    }
}
