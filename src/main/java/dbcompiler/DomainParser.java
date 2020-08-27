package dbcompiler;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.Token;

import java.util.*;
import java.util.stream.Collectors;

import static dbcompiler.DomainModel.*;

public class DomainParser extends GraphQLBaseVisitor {
    private DomainModel model;

    public DomainParser() {
        model = new DomainModel();
    }
    public DomainModel visit(GraphQLParser.DocumentContext ctx) {
        super.visit(ctx);
        return model;
    }

    @Override
    public Object visitObjectTypeDefinition(GraphQLParser.ObjectTypeDefinitionContext ctx) {
        Entity entity = new Entity();
        entity.entityName = ctx.name().getText();
        Map<String,Map<String, Object>> directives = visitDirectives(ctx.directives());
        entity.fieldMap = ctx.fieldsDefinition().fieldDefinition()
                .stream()
                .map(f -> visitFieldDefinition(f, entity))
                .collect(Collectors.toMap(e->e.name, e->e));
        entity.size = entity.new EntitySizeDirective();
        entity.size.max = Integer.parseInt(directives.get("size").get("max").toString());
        entity.selectivityMap = parseSelectivityMap((List<Map<String, Object>>)directives.get("selectivity").get("fields"), entity);
        model.entities.put(entity.entityName, entity);
        return entity;
    }

    private Map<Set<FieldPath>, Selectivity> parseSelectivityMap(List<Map<String, Object>> fields, Entity entity) {
        Map<Set<FieldPath>, Selectivity> map = new HashMap<>();
        for (Map<String, Object> selectivityField : fields) {
            Selectivity selectivity = parseSelectivity(selectivityField, entity);
            map.put(selectivity.fields, selectivity);
        }
        return map;
    }

    public Entity.Field visitFieldDefinition(GraphQLParser.FieldDefinitionContext ctx, Entity entity) {
        Map<String,Map<String, Object>> directives = visitDirectives(ctx.directives());

        Entity.Field field = entity.new Field(ctx.name().getText(), visitType_(ctx.type_()), !directives.containsKey("mutable"));
        return field;
    }

    private Selectivity parseSelectivity(Map<String, Object> directive, Entity rootEntity) {
        Selectivity selectivity = new Selectivity();
        if (directive == null) {
            return selectivity;
        }
        Set<FieldPath> fields = ((List<String>)directive.get("field")).stream()
                .map(e-> parseFieldPath(e, rootEntity))
                .collect(Collectors.toSet());

        Preconditions.checkNotNull(directive.get("distinct"), "Selectivity must have distinct");
//        Preconditions.checkNotNull(directive.get("distribution"), "Selectivity must have distribution");
        selectivity.distinct = Integer.parseInt(directive.get("distinct").toString());
        selectivity.distribution = parseDistribution((Map<String, Object>)directive.get("distribution"));
        selectivity.fields = fields;
        return selectivity;
    }

    private Distribution parseDistribution(Map<String, Object> directive) {
        return new BinomialDistribution();
    }

    @Override
    public Object visitQueryRootDefinition(GraphQLParser.QueryRootDefinitionContext ctx) {
        TypeDef typeDef = visitType_(ctx.type_());
        Map<String, Map<String, Object>> directives = visitDirectives(ctx.directives());
        QueryDefinition queryDefinition = new QueryDefinition(
                ctx.name().getText(),
                typeDef,
                visitSqlDirective(directives, typeDef.entity));
        model.queryDefinitionMap.put(queryDefinition.name, queryDefinition);
        return queryDefinition;
    }

    @Override
    public TypeDef visitType_(GraphQLParser.Type_Context ctx) {
        if (ctx.namedType() != null) {
            return visitNamedType(ctx.namedType(), ctx.nonnull);
        } else if (ctx.listType() != null) {
            return visitListType(ctx.listType(), ctx.nonnull);
        }
        return null;
    }

    public TypeDef visitNamedType(GraphQLParser.NamedTypeContext ctx, Token token) {
        return new TypeDef(ctx.name().getText(), model.entities.get(ctx.name().getText()), TypeDef.Multiplicity.SINGLE,
                token != null);
    }

    public TypeDef visitListType(GraphQLParser.ListTypeContext ctx, Token token) {
        return new TypeDef(ctx.type_().namedType().name().getText(), model.entities.get(ctx.type_().namedType().name().getText()),
            TypeDef.Multiplicity.LIST, token != null);
    }

    @Override
    public Object visitOperationDefinition(GraphQLParser.OperationDefinitionContext ctx) {
        if (ctx.operationType().getText().equals("query")) {
            return visitQuery(ctx);
        } else if (ctx.operationType().getText().equals("mutation")) {
            //return visitMutation(ctx);
        }
        return null;
    }

    private Query visitQuery(GraphQLParser.OperationDefinitionContext ctx) {
        Query query = new Query();
        query.name = ctx.name().getText();
        query.selections = visitDefinitionSelection(ctx.selectionSet(), query);
        Map<String,Map<String, Object>> directives = visitDirectives(ctx.directives());
        query.sla = query.new SLADirective();
        if (directives.get("sla") != null ) {
            String throughput_per_second;
            if ((throughput_per_second = directives.get("sla").get("throughput_per_second").toString()) != null) {
                query.sla.throughput_per_second = Integer.parseInt(throughput_per_second);
            }
            String latency_ms;
            if ((latency_ms = directives.get("sla").get("latency_ms").toString()) != null) {
                query.sla.latency_ms = Integer.parseInt(latency_ms);
            }
        }
        model.queries.add(query);
        return null;
    }

    @Override
    public Map<String, Map<String, Object>> visitDirectives(GraphQLParser.DirectivesContext ctx) {
        Map map = new HashMap();
        if (ctx == null || ctx.directive() == null) return ImmutableMap.of();
        for (GraphQLParser.DirectiveContext directiveContext : ctx.directive()) {
            map.put(directiveContext.name().getText(), visitArguments(directiveContext.arguments()));
        }

        return map;
    }

    @Override
    public Map<String, Object> visitArguments(GraphQLParser.ArgumentsContext ctx) {
        Map<String, Object> args = new HashMap<>();
        if (ctx == null || ctx.argument() == null) return ImmutableMap.of();
        for (GraphQLParser.ArgumentContext argument : ctx.argument()) {
            args.put(argument.name().getText(), visitArgument(argument));
        }
        return args;
    }

    @Override
    public Object visitArgument(GraphQLParser.ArgumentContext ctx) {
        return super.visitArgument(ctx);
    }

    @Override
    public Object visitListValue(GraphQLParser.ListValueContext ctx) {
        List list = new ArrayList();
        for (GraphQLParser.ValueContext val : ctx.value()) {
            list.add(visitValue(val));
        }
        return list;
    }

    @Override
    public Object visitObjectValue(GraphQLParser.ObjectValueContext ctx) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (GraphQLParser.ObjectFieldContext objectFieldContext : ctx.objectField()) {
            map.put(objectFieldContext.name().getText(), visitValue(objectFieldContext.value()));
        }
        return map;
    }

    @Override
    public Object visitStringValue(GraphQLParser.StringValueContext ctx) {
        return ctx.STRING().getSymbol().getText().substring(1, ctx.STRING().getSymbol().getText().length() - 1);
    }

    @Override
    public Object visitIntValue(GraphQLParser.IntValueContext ctx) {
        return Integer.parseInt(ctx.INT().getSymbol().getText());
    }

    @Override
    public Object visitFloatValue(GraphQLParser.FloatValueContext ctx) {
        return Float.parseFloat(ctx.FLOAT().getSymbol().getText());
    }

    @Override
    public Object visitBooleanValue(GraphQLParser.BooleanValueContext ctx) {
        return ctx.getText().equals("true") ? true : false;
    }

    public List<Query.QueryDefinitionSelection> visitDefinitionSelection(GraphQLParser.SelectionSetContext ctx, Query query) {
        List<Query.QueryDefinitionSelection> queryDefinitionSelections = new ArrayList<>();
        for (GraphQLParser.SelectionContext sel : ctx.selection()) {
            Query.QueryDefinitionSelection queryDefinitionSelection = query.new QueryDefinitionSelection();
            queryDefinitionSelection.definition = model.queryDefinitionMap.get(sel.field().name().getText());
            Preconditions.checkNotNull(queryDefinitionSelection.definition, "dbcompiler.Query definition was not defined {}", sel.field().name().getText());
            queryDefinitionSelections.add(queryDefinitionSelection);
        }
        return queryDefinitionSelections;
    }

    @Override
    public Object visitField(GraphQLParser.FieldContext ctx) {
        return null;
    }

    @Override
    public Object visitFragmentDefinition(GraphQLParser.FragmentDefinitionContext ctx) {
        Mutation mutation = new Mutation();
        mutation.name = ctx.fragmentName().getText();
        model.mutations.add(mutation);
        return mutation;
    }

    public QueryDefinition.SqlClause visitSqlDirective(Map<String, Map<String, Object>> directives, Entity rootEntity) {
        if (!directives.containsKey("sql")) return null;
        return new QueryDefinition.SqlClause(rootEntity,
                parseConjunctions(directives.get("sql"), rootEntity),
                parseOrderBy(directives.get("sql"), rootEntity));
    }

    private List<OrderBy> parseOrderBy(Map<String, Object> directive, Entity rootEntity) {
        List<OrderBy> orderByList = new ArrayList<>();
        List<Map<String, Object>> orders = (List<Map<String, Object>>)directive.get("order");
        if (orders == null || orders.isEmpty()) return null;
        for (Map<String, Object> order : orders) {
            Preconditions.checkNotNull(order.get("field"), "'field' must be present in order by statement");
            Preconditions.checkNotNull(order.get("direction"), "'direction' must be present in order by statement");
            orderByList.add(new OrderBy(parseFieldPath(order.get("field").toString(), rootEntity),
                    Direction.valueOf(order.get("direction").toString())));
        }

        return orderByList;
    }

    private List<QueryDefinition.SqlClause.Conjunction> parseConjunctions(Map<String, Object> directive, Entity rootEntity) {
        String where = directive.get("where").toString();
        String[] clause = where.split(" AND ");

        List<QueryDefinition.SqlClause.Conjunction> conjunctions = new ArrayList<>();
        for (String part : clause) {
            String[] p = part.split(" = ");
            conjunctions.add(new QueryDefinition.SqlClause.Conjunction(parseFieldPath(p[0], rootEntity), p[1]));
        }
        return conjunctions;
    }

    public static FieldPath parseFieldPath(String text, Entity rootEntity) {
        List<Entity.Field> fieldPath = new ArrayList<>();
        String[] path = text.split("\\.");
        Entity entity = rootEntity;
        Entity.Field lastField = null;
        for (int i = 0; i < path.length; i++) {
            String fieldRef = path[i];
            Entity.Field field = entity.fieldMap.get(fieldRef);
            Preconditions.checkNotNull(field, "Field %s is empty for %s", fieldRef, text);
            fieldPath.add(field);
            Preconditions.checkState(i < path.length - 1 && field.typeDef.entity != null || ((i + 1) == path.length && field.typeDef.entity == null),
                    "Expression does not end with scalar field: {}", text);
            entity = field.typeDef.entity;
            lastField = field;
        }
        return new FieldPath(fieldPath, text, rootEntity, lastField.immutable);
    }

}
