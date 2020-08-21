import java.util.ArrayList;
import java.util.List;

public class DomainModel {
    private List<Entity> entities = new ArrayList<>();
    private List<QueryDefinition> queryDefinitions = new ArrayList<>();
    private List<Query> queries = new ArrayList<>();
    private List<Mutation> mutations = new ArrayList<>();

    public void add(Entity entity) {
        entities.add(entity);
    }

    public void add(QueryDefinition queryDefinition) {
        this.queryDefinitions.add(queryDefinition);
    }

    public void add(Query query) {
        this.queries.add(query);
    }
    public void add(Mutation mutation) {
        this.mutations.add(mutation);
    }

    @Override
    public String toString() {
        return "DomainModel{" +
                "entities=" + entities +
                ", queryDefinitions=" + queryDefinitions +
                ", queries=" + queries +
                ", mutations=" + mutations +
                '}';
    }
}
