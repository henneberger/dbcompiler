import com.google.common.io.Resources;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.nio.charset.Charset;

public class Main {
    public static void main(String[] args) throws IOException {
        Main main = new Main();
        DomainModel model = main.parse(Resources.toString(
                Resources.getResource("todo.graphql"),
                    Charset.defaultCharset()));
        System.out.println(model);

    }

    public DomainModel parse(String graphql) {
        GraphQLLexer lexer = new GraphQLLexer(CharStreams.fromString(graphql));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        GraphQLParser parser = new GraphQLParser(tokenStream);
        GraphQLVisitorImpl visitor = new GraphQLVisitorImpl();

        return visitor.visit(parser.document());
    }
}