import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.*;
import java.util.stream.Collectors;

public class SqlClause {
    private final Entity rootEntity;
    private final DomainModel model;
    private Plan plan;
    private List<Conjunction> conjunctions;
    private List<Index> indices;
    double partitionScanCost = 0.1;

    public SqlClause(Entity rootEntity, DomainModel model) {
        this.model = model;
        this.indices = new ArrayList<>();
        this.rootEntity = rootEntity;
    }

    public List<Conjunction> getConjunctions() {
        return conjunctions;
    }

    public PermuteResult permute(DomainModel.Query query) {
        if (hasRootGenID()) {
            return new PermuteResult(ImmutableSet.of(), null);
        }

        Plan plan = new Plan();

        Set<String> sargable = getSargablePredicates();
        Set<Index> indices = new HashSet<>();

        for (int i = 0; i <= sargable.size(); i++) {
            for (Set<String> comb : Sets.combinations(sargable, i)) {
                Set<String> remaining = new HashSet<>(getAllPredicates());
                remaining.removeAll(comb);
                Index index = new Index(query, comb, ImmutableSet.of(), remaining);
                indices.add(index);
                plan.addPlan(new Plan(index));
                this.indices.add(index);
            }
        }

        return new PermuteResult(indices, plan);
    }

    private boolean hasRootGenID() {
        for (Conjunction conjunction : conjunctions) {
            if (conjunction.isGenId()) {
                return true;
            }
        }
        return false;
    }

    public SqlClause getSqlClause() {
        return this;
    }

    public List<Index> getAllIndicies() {
        return this.indices;
    }

    public void setConjunctions(List<Conjunction> conjunctions) {
        this.conjunctions = conjunctions;
    }

    public class PermuteResult {
        public final Set<Index> indices;
        public final Plan plan;

        public PermuteResult(Set<Index> indices, Plan plan) {
            this.indices = indices;
            this.plan = plan;
        }
    }

    public class Index {
        private DomainModel.Query query;
        public final Set<String> merkle;
        private final Set<String> bTree;
        private final Set<String> remaining;
        private MPVariable var;
        private SqlClause clause = getSqlClause();

        public Index(DomainModel.Query query, Set<String> merkle, Set<String> bTree, Set<String> remaining) {
            this.query = query;
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
            //Cost:
            //Frequency of query * cost of index lookup & scan
            //Cost of index lookup:
            // 1 * (scan) Number of elements in partition
            if (!merkle.isEmpty() && remaining.isEmpty()) {
                return 1;
            }
            return getScanCost();
        }

        public double getScanCost() {
            return 1.05;
        }

        public MPVariable getOrCreateVarForIndex(MPSolver solver) {
            if (this.var == null) {
//                var = solver.makeIntVar(0, 1,  UUID.randomUUID().toString().substring(0, 4));
                var = solver.makeBoolVar("i"+UUID.randomUUID().toString().substring(0, 4));
            }
            return var;
        }

        public int getQueryFrequency() {
            return query.getFrequency();
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
