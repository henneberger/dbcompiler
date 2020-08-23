public class QueryDefinition {
    private String name;
    private String type;
    private final SqlClause sqlClause;

    public QueryDefinition(String name, String type, SqlClause sqlClause) {
        this.name = name;
        this.type = type;
        this.sqlClause = sqlClause;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public SqlClause getSqlClause() {
        return sqlClause;
    }

    @Override
    public String toString() {
        return "QueryDefinition{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", sqlClause=" + sqlClause +
                '}';
    }
}
