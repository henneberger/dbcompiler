import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.*;
import java.util.stream.Collectors;

public class SqlClause {
    private Plan plan;
    private List<Conjunction> conjunctions;
    private List<Index> indices;

    public SqlClause(List<Conjunction> conjunctions) {
        this.conjunctions = conjunctions;
        this.indices = new ArrayList<>();
    }

    public List<Conjunction> getConjunctions() {
        return conjunctions;
    }

    public Set<Index> permute() {
        Preconditions.checkState(plan == null, "Cannot rerun function (getPlan returns stateful object)");
        this.plan = new Plan();

        Set<String> sargable = getSargablePredicates();
        Set<Index> indices = new HashSet<>();

        for (int i = 0; i <= sargable.size(); i++) {
            for (Set<String> comb : Sets.combinations(sargable, i)) {
                Set<String> remaining = new HashSet<>(getAllPredicates());
                remaining.removeAll(comb);
                Index index = new Index(comb, ImmutableSet.of(), remaining);
                indices.add(index);
                plan.addPlan(new Plan(index));
                this.indices.add(index);
            }
        }

        return indices;
    }

    public SqlClause getSqlClause() {
        return this;
    }

    public Plan getPlan() {
        return plan;
    }

    public double getFrequency() {
        return 100;
    }

    public List<Index> getAllIndicies() {
        return this.indices;
    }

    public class Index {
        public final Set<String> merkle;
        private final Set<String> bTree;
        private final Set<String> remaining;
        private MPVariable var;
        private SqlClause clause = getSqlClause();

        public Index(Set<String> merkle, Set<String> bTree, Set<String> remaining) {
            this.merkle = merkle;
            this.bTree = bTree;
            this.remaining = remaining;
        }

        @Override
        public String toString() {
            return "Index["+ merkle +
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

        public SqlClause getClause() {
            return clause;
        }


        public double getCost() {
            return merkle.size() == 0 ? 100 : merkle.size();
        }

        public MPVariable getOrCreateVarForIndex(MPSolver solver) {
            if (this.var == null) {
                var = solver.makeNumVar(0.0, Optimizer.infinity, merkle.toString());
            }
            return var;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Index index = (Index) o;
            return Objects.equals(merkle, index.merkle);
        }

        @Override
        public int hashCode() {

            return Objects.hash(merkle);
        }
    }

    public Set<String> getSargablePredicates() {
        Set<String> sargable = new HashSet<>();
        for (Conjunction conjunction : conjunctions) {
            sargable.add(conjunction.getField());
        }

        return sargable;
    }

    public Set<String> getAllPredicates() {
        return conjunctions.stream()
                .map(e->e.getField())
                .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "SqlClause{" +
                "conjunctions=" + conjunctions +
                '}';
    }
}
