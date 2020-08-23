import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SqlClause {
    private List<Conjunction> conjunctions;

    public SqlClause(List<Conjunction> conjunctions) {
        this.conjunctions = conjunctions;
    }

    public List<Conjunction> getConjunctions() {
        return conjunctions;
    }

    public Set<Plan> permute() {
        Set<String> sargable = getSargablePredicates();
        Set<Plan> plans = new HashSet<>();

        for (int i = 0; i <= sargable.size(); i++) {
            for (Set<String> comb : Sets.combinations(sargable, i)) {
                Set<String> remaining = new HashSet<>(getAllPredicates());
                remaining.removeAll(comb);
                plans.add(new Plan(comb, ImmutableSet.of(), remaining));
            }
        }

        return plans;
    }

    public class Plan {
        private final Set<String> merkle;
        private final Set<String> bTree;
        private final Set<String> remaining;
        private MPVariable var;

        public Plan(Set<String> merkle, Set<String> bTree, Set<String> remaining) {
            this.merkle = merkle;
            this.bTree = bTree;
            this.remaining = remaining;
        }

        @Override
        public String toString() {
            return "Plan["+ merkle +
                    "][" + bTree +
                    "](" + remaining +
                    ')';
        }

        public Set<String> getMerkle() {
            return merkle;
        }

        public Set<String> getbTree() {
            return bTree;
        }

        public Set<String> getRemaining() {
            return remaining;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Plan plan = (Plan) o;
            return Objects.equals(merkle, plan.merkle);
        }

        @Override
        public int hashCode() {

            return Objects.hash(merkle, bTree, remaining);
        }

        public MPVariable getOrCreateVariable(MPSolver solver) {
            if (this.var == null) {
                var = solver.makeNumVar(0.0, Optimizer.infinity, merkle.toString());
            }
            return var;
        }

        public double getCost() {
            return merkle.size() == 0 ? 100 : merkle.size();
        }
    }

    public Set<String> getSargablePredicates() {
        return ImmutableSet.of("user.name", "price");
    }

    public Set<String> getAllPredicates() {
        return ImmutableSet.of("user.name", "price", "quantity");
    }

    @Override
    public String toString() {
        return "SqlClause{" +
                "conjunctions=" + conjunctions +
                '}';
    }
}
