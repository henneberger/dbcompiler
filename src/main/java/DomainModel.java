import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DomainModel {
    protected List<Entity> entities = new ArrayList<>();
    protected List<QueryDefinition> queryDefinitions = new ArrayList<>();
    protected Map<String, QueryDefinition> queryDefinitionMap = new HashMap<>();
    protected List<Query> queries = new ArrayList<>();
    protected List<Mutation> mutations = new ArrayList<>();

    public void add(Entity entity) {
        entities.add(entity);
    }

    public void add(QueryDefinition queryDefinition) {
        this.queryDefinitions.add(queryDefinition);
        this.queryDefinitionMap.put(queryDefinition.getName(), queryDefinition);
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

    public Entity getEntity(String entity_name) {
        for (Entity entity : entities) {
            if (entity.getName().equals(entity_name)){
                return entity;
            }
        }
        return null;
    }

    public class Query extends QueryRelation {

        public Query(String name, DomainModel.QuerySelectionSet selection) {
            super(name, selection);
        }
    }

    public class QuerySelectionSet {
        private final String name;
        private final List<QuerySelection> selections;

        public QuerySelectionSet(String name, List<QuerySelection> selections) {
            this.name = name;
            this.selections = selections;
        }

        public String getName() {
            return name;
        }

        public List<QuerySelection> getSelections() {
            return selections;
        }

        @Override
        public String toString() {
            return "QuerySelectionSet{" +
                    "name=" + name +
                    ", selections=" + selections +
                    '}';
        }
    }

    public class QuerySelection {
        protected final String name;

        public QuerySelection(String name) {
            this.name = name;
        }

        public QueryDefinition getDefinition() {
            return queryDefinitionMap.get(name);
        }
        @Override
        public String toString() {
            return "QuerySelection{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }


    public class QueryRelation extends QuerySelection {
        private DomainModel.QuerySelectionSet querySelectionSet;

        public QueryRelation(String name, DomainModel.QuerySelectionSet selection) {
            super(name);
            querySelectionSet = selection;
        }

        public DomainModel.QuerySelectionSet getQuerySelectionSet() {
            return querySelectionSet;
        }

        @Override
        public String toString() {
            return "QueryRelation{" +
                    "querySelectionSet=" + querySelectionSet +
                    '}';
        }
    }
}