package dbcompiler;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dbcompiler.DomainModel.*;

/**
 * Converts
 */
public class DomainParser extends GraphQLBaseVisitor {
    private DomainModel model;

    public DomainParser() {
        model = new DomainModel();
        model.entities = new HashMap<>();
        model.queryDefinitionMap = new HashMap<>();
        model.queries = new ArrayList<>();
        model.mutations = new ArrayList<>();
    }
    public DomainModel visit(GraphQLParser.DocumentContext ctx) {
        super.visit(ctx);
        return model;
    }

    @Override
    public Object visitExecutableDefinition(GraphQLParser.ExecutableDefinitionContext ctx) {
        Object ex = super.visitExecutableDefinition(ctx);
        if (ex instanceof Mutation) {
            model.mutations.add((Mutation)ex);
        } else if (ex instanceof Query) {
            model.queries.add((Query) ex);
        }

        return null;
    }

    @Override
    public Object visitQueryRootDefinitions(GraphQLParser.QueryRootDefinitionsContext ctx) {
        return super.visitQueryRootDefinitions(ctx);
    }

    @Override
    public Object visitTypeDefinition(GraphQLParser.TypeDefinitionContext ctx) {
        return super.visitTypeDefinition(ctx);
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

        model.entities.put(entity.entityName, entity);
        return entity;
    }

    public Entity.Field visitFieldDefinition(GraphQLParser.FieldDefinitionContext ctx, Entity entity) {
        Entity.Field field = entity.new Field();
        field.name = ctx.name().getText();
        field.typeDef = visitType_(ctx.type_());
        Map<String,Map<String, Object>> directives = visitDirectives(ctx.directives());
        field.selectivity = model.new Selectivity();
        if (directives.get("selectivity") != null && directives.get("selectivity").get("max") != null) {
            field.selectivity.estimate = Integer.parseInt(directives.get("selectivity").get("max").toString());
        }
        return field;
    }

    @Override
    public Object visitQueryRootDefinition(GraphQLParser.QueryRootDefinitionContext ctx) {
        DomainModel.QueryDefinition queryDefinition = new DomainModel.QueryDefinition(
                ctx.name().getText(),
                visitType_(ctx.type_()));
        queryDefinition.sqlClause = visitSqlDirective(ctx.sqlDirective(), queryDefinition.type.entity);
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
        TypeDef typeDef = new TypeDef();
        typeDef.typeName = ctx.name().getText();
        typeDef.entity = model.entities.get(ctx.name().getText());
        typeDef.multiplicity = TypeDef.Multiplicity.SINGLE;
        typeDef.nonnull = token != null;
        return typeDef;
    }

    public TypeDef visitListType(GraphQLParser.ListTypeContext ctx, Token token) {
        TypeDef typeDef = new TypeDef();
        typeDef.typeName = ctx.type_().namedType().name().getText();
        typeDef.entity = model.entities.get(ctx.type_().namedType().name().getText());
        typeDef.multiplicity = TypeDef.Multiplicity.LIST;
        typeDef.nonnull = token != null;
        return typeDef;
    }

    @Override
    public Object visitOperationDefinition(GraphQLParser.OperationDefinitionContext ctx) {
        if (ctx.operationType().getText().equals("query")) {
            return visitQuery(ctx);
        } else if (ctx.operationType().getText().equals("mutation")) {
            return null; // visitMutation(ctx);
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

        return query;
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
        return ctx.value().getText();
    }

    public List<Query.QueryDefinitionSelection> visitDefinitionSelection(GraphQLParser.SelectionSetContext ctx, Query query) {
        String name = null;
        if (ctx.parent instanceof GraphQLParser.OperationDefinitionContext) {
            name = ((GraphQLParser.OperationDefinitionContext) ctx.parent).name().getText();
        }

        List<Query.QueryDefinitionSelection> queryDefinitionSelections = new ArrayList<>();
        for (GraphQLParser.SelectionContext sel : ctx.selection()) {
            Query.QueryDefinitionSelection queryDefinitionSelection = query.new QueryDefinitionSelection();
            queryDefinitionSelection.definition = model.queryDefinitionMap.get(sel.field().name().getText());
            Preconditions.checkNotNull(queryDefinitionSelection.definition, "dbcompiler.Query definition was not defined {}", sel.field().name().getText());
            queryDefinitionSelections.add(queryDefinitionSelection);
//
//            List<dbcompiler.Query.QuerySelection> selections = sel.field().selectionSet().selection().stream()
//                    .map(sel-> visitSelectionSet(sel, query))
//                    .collect(Collectors.toList());

        }
        return queryDefinitionSelections;
    }
//    public List<dbcompiler.Query.QuerySelection> visitSelectionSet(GraphQLParser.SelectionSetContext ctx, dbcompiler.Query query) {
//        String name = null;
//        if (ctx.parent instanceof GraphQLParser.OperationDefinitionContext) {
//            name = ((GraphQLParser.OperationDefinitionContext) ctx.parent).typeName().getText();
//        } else if (ctx.parent instanceof GraphQLParser.FieldContext) {
//            name = ((GraphQLParser.FieldContext) ctx.parent).typeName().getText();
//        }
//
//        List<dbcompiler.Query.QuerySelection> selections = ctx.selection().stream()
//                .map(sel-> visitSelection(sel))
//                .collect(Collectors.toList());
//        return query.new QuerySelection(name, selections);
//    }
//
//    @Override
//    public dbcompiler.Query.QuerySelection visitSelection(GraphQLParser.SelectionContext ctx) {
//        if (containsChildSelectionSet(ctx)) {
//            return new QueryRelation(ctx.field().name().getText(), visitSelectionSet(ctx.field().selectionSet()), ImmutableMap.of());
//        }
//        return new dbcompiler.Query.QuerySelection(ctx.field().name().getText(), ImmutableMap.of());
//    }

    @Override
    public Object visitField(GraphQLParser.FieldContext ctx) {
        return null;
    }

    private boolean containsChildSelectionSet(GraphQLParser.SelectionContext ctx) {
        return ctx.field().selectionSet() != null;
    }

    @Override
    public Object visitFragmentDefinition(GraphQLParser.FragmentDefinitionContext ctx) {
        Mutation mutation = new Mutation();
        mutation.name = ctx.fragmentName().getText();
        return mutation;
    }

    public SqlClause visitSqlDirective(GraphQLParser.SqlDirectiveContext ctx, Entity rootEntity) {
        SqlClause sqlClause = new SqlClause();
        sqlClause.rootEntity = rootEntity;
        sqlClause.conjunctions = visitSqlBooleanExpression(ctx.expr, rootEntity);
        return sqlClause;
    }

    public List<SqlClause.Conjunction> visitSqlBooleanExpression(GraphQLParser.SqlBooleanExpressionContext ctx, Entity rootEntity) {
        if (ctx == null || ctx.left == null) return ImmutableList.of();

        List<SqlClause.Conjunction> conjunctions = new ArrayList<>();
        conjunctions.add(visitSqlExpression(ctx.left, rootEntity));
        if (ctx.right != null) {
            conjunctions.addAll(visitSqlBooleanExpression(ctx.right, rootEntity));
        }
        return conjunctions;
    }

    public SqlClause.Conjunction visitSqlExpression(GraphQLParser.SqlExpressionContext ctx, Entity rootEntity) {
        return new SqlClause.Conjunction(parseFieldPath(ctx.fieldExpression().getText(), rootEntity), null/*new dbcompiler.Query.Variable(ctx.sqlValue().getText())*/);
    }

    public static SqlClause.Conjunction.FieldPath parseFieldPath(String text, Entity rootEntity) {
        List<Entity.Field> fieldPath = new ArrayList<>();
        String[] path = text.split("\\.");
        Entity entity = rootEntity;
        for (int i = 0; i < path.length; i++) {
            String fieldRef = path[i];
            Entity.Field field = entity.fieldMap.get(fieldRef);
            fieldPath.add(field);
            Preconditions.checkState(i < path.length - 1 && field.typeDef.entity != null || ((i + 1) == path.length && field.typeDef.entity == null),
                    "Expression does not end with scalar field: {}", text);
            entity = field.typeDef.entity;
        }
        SqlClause.Conjunction.FieldPath fieldPath1 = new SqlClause.Conjunction.FieldPath();
        fieldPath1.fields = fieldPath;
        fieldPath1.toStringVal = text;
        fieldPath1.entity = rootEntity;
        return fieldPath1;
    }

}
