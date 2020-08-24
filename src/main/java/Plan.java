import java.util.ArrayList;
import java.util.List;

public class Plan {
    private final SqlClause.Index index;
    private final List<Plan> children = new ArrayList<>();

    public Plan() {
        this(null);
    }

    public Plan(SqlClause.Index index) {
        this.index = index;
    }

    public void visit(PlanVisitor pathVisitor) {
        pathVisitor.visit(this);
    }

    public void addPlan(Plan plan) {
        children.add(plan);
    }

    public List<Plan> getChildren() {
        return children;
    }

    public SqlClause.Index getIndex() {
        return index;
    }

}
