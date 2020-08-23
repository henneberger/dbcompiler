import java.util.ArrayList;
import java.util.List;
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
            model.add((Entity) ex);
        }
        return null;
    }

    @Override
    public Object visitTypeDefinition(GraphQLParser.TypeDefinitionContext ctx) {
        return super.visitTypeDefinition(ctx);
    }

    @Override
    public Object visitObjectTypeDefinition(GraphQLParser.ObjectTypeDefinitionContext ctx) {
        List<Field> fields = ctx.fieldsDefinition().fieldDefinition()
                .stream()
                .map(this::visitFieldDefinition)
                .collect(Collectors.toList());

        return new Entity(ctx.name().getText(), fields);
    }

    @Override
    public Field visitFieldDefinition(GraphQLParser.FieldDefinitionContext ctx) {
        return new Field(ctx.name().getText(), ctx.type_().getText());
    }

    @Override
    public Object visitQueryRootDefinition(GraphQLParser.QueryRootDefinitionContext ctx) {
        QueryDefinition queryDefinition = new QueryDefinition(
                ctx.name().getText(),
                ctx.type_().getText(),
                visitSqlDirective(ctx.sqlDirective()));
        model.add(queryDefinition);
        return queryDefinition;
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
        return model.new Query(ctx.name().getText(), visitSelectionSet(ctx.selectionSet()));
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
            return model.new QueryRelation(ctx.field().name().getText(), visitSelectionSet(ctx.field().selectionSet()));
        }
        return model.new QuerySelection(ctx.field().name().getText());
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

    @Override
    public SqlClause visitSqlDirective(GraphQLParser.SqlDirectiveContext ctx) {
        List<Conjunction> conjunctionList = visitSqlBooleanExpression(ctx.expr);
        return new SqlClause(conjunctionList);
    }

    @Override
    public List<Conjunction> visitSqlBooleanExpression(GraphQLParser.SqlBooleanExpressionContext ctx) {
        List<Conjunction> conjunctions = new ArrayList<>();
        conjunctions.add(visitSqlExpression(ctx.left));
        if (ctx.right != null) {
            conjunctions.addAll(visitSqlBooleanExpression(ctx.right));
        }
        return conjunctions;
    }

    @Override
    public Conjunction visitSqlExpression(GraphQLParser.SqlExpressionContext ctx) {
        return new Conjunction(ctx.fieldExpression().getText(), ctx.sqlValue().getText());
    }
}
