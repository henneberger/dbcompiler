import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphQLVisitorImpl extends GraphQLBaseVisitor {
    private DomainModel model;

    public GraphQLVisitorImpl() {
        model = new DomainModel();
    }
    public DomainModel visit(GraphQLParser.DocumentContext ctx) {
        super.visit(ctx);
        return model;
    }

    @Override
    public Object visitExecutableDefinition(GraphQLParser.ExecutableDefinitionContext ctx) {
        Object ex = super.visitExecutableDefinition(ctx);
        if (ex instanceof Mutation) {
            model.add((Mutation)ex);
        } else if (ex instanceof DomainModel.Query) {
            model.add((DomainModel.Query) ex);
//        } else if (ex instanceof QueryDefinition) {
//            model.add((QueryDefinition) ex);
        } else if (ex instanceof Entity) {
//            model.add((Entity) ex);
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
        Entity entity = new Entity(ctx.name().getText(), visitDirectives(ctx.directives()));
        List<Entity.Field> fields = ctx.fieldsDefinition().fieldDefinition()
                .stream()
                .map(f -> visitFieldDefinition(f, entity))
                .collect(Collectors.toList());
        entity.setFields(fields);
        model.add(entity);
        return entity;
    }

    public Entity.Field visitFieldDefinition(GraphQLParser.FieldDefinitionContext ctx, Entity entity) {
        return entity.new Field(ctx.name().getText(), ctx.type_().getText(), visitDirectives(ctx.directives()));
    }

    @Override
    public Object visitQueryRootDefinition(GraphQLParser.QueryRootDefinitionContext ctx) {
        QueryDefinition queryDefinition = new QueryDefinition(
                ctx.name().getText(),
                visitType_(ctx.type_()));
        queryDefinition.setSqlClause(visitSqlDirective(ctx.sqlDirective(), queryDefinition.getType().getEntity()));
        model.add(queryDefinition);
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
        return new TypeDef(model.getEntity(ctx.name().getText()), Multiplicity.SINGLE, token != null);
    }

    public TypeDef visitListType(GraphQLParser.ListTypeContext ctx, Token token) {
        return new TypeDef(model.getEntity(ctx.type_().namedType().name().getText()), Multiplicity.LIST, token != null);
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


    private DomainModel.Query visitQuery(GraphQLParser.OperationDefinitionContext ctx) {
        return model.new Query(ctx.name().getText(), visitSelectionSet(ctx.selectionSet()), visitDirectives(ctx.directives()));
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

    @Override
    public DomainModel.QuerySelectionSet visitSelectionSet(GraphQLParser.SelectionSetContext ctx) {
        String name = null;
        if (ctx.parent instanceof GraphQLParser.OperationDefinitionContext) {
            name = ((GraphQLParser.OperationDefinitionContext) ctx.parent).name().getText();
        } else if (ctx.parent instanceof GraphQLParser.FieldContext) {
            name = ((GraphQLParser.FieldContext) ctx.parent).name().getText();
        }

        List<DomainModel.QuerySelection> selections = ctx.selection().stream()
                .map(sel-> visitSelection(sel))
                .collect(Collectors.toList());
        return model.new QuerySelectionSet(name, selections);
    }

    @Override
    public DomainModel.QuerySelection visitSelection(GraphQLParser.SelectionContext ctx) {
        if (containsChildSelectionSet(ctx)) {
            return model.new QueryRelation(ctx.field().name().getText(), visitSelectionSet(ctx.field().selectionSet()), ImmutableMap.of());
        }
        return model.new QuerySelection(ctx.field().name().getText(), ImmutableMap.of());
    }

    @Override
    public Object visitField(GraphQLParser.FieldContext ctx) {
        return null;
    }

    private boolean containsChildSelectionSet(GraphQLParser.SelectionContext ctx) {
        return ctx.field().selectionSet() != null;
    }

    @Override
    public Object visitFragmentDefinition(GraphQLParser.FragmentDefinitionContext ctx) {
        return new Mutation(ctx.fragmentName().getText());
    }

    public SqlClause visitSqlDirective(GraphQLParser.SqlDirectiveContext ctx, Entity rootEntity) {
        SqlClause sqlClause = new SqlClause(rootEntity, model);
        List<Conjunction> conjunctionList = visitSqlBooleanExpression(ctx.expr, rootEntity);
        sqlClause.setConjunctions(conjunctionList);
        return sqlClause;
    }

    public List<Conjunction> visitSqlBooleanExpression(GraphQLParser.SqlBooleanExpressionContext ctx, Entity rootEntity) {
        if (ctx == null || ctx.left == null) return ImmutableList.of();

        List<Conjunction> conjunctions = new ArrayList<>();
        conjunctions.add(visitSqlExpression(ctx.left, rootEntity));
        if (ctx.right != null) {
            conjunctions.addAll(visitSqlBooleanExpression(ctx.right, rootEntity));
        }
        return conjunctions;
    }

    public Conjunction visitSqlExpression(GraphQLParser.SqlExpressionContext ctx, Entity rootEntity) {
        return new Conjunction(ctx.fieldExpression().getText(), ctx.sqlValue().getText(), rootEntity);
    }
}
