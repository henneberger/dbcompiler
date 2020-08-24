public class QueryDefinition {
    private String name;
    private TypeDef type;
    private SqlClause sqlClause;

    public QueryDefinition(String name, TypeDef type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public TypeDef getType() {
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

    public void setSqlClause(SqlClause sqlClause) {
        this.sqlClause = sqlClause;
    }
}
