import com.google.common.io.Resources;
import dbcompiler.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        String file = "src/main/resources/todo.graphql";
        if (args.length >= 1) {
            file = args[0];
        }
        Main main = new Main();
        DomainModel model = main.parse(CharStreams.fromFileName(file));

        LogicalPlan.Workload workload = new LogicalPlan(model).search();
        Optimizer optimizer = new Optimizer(workload, model);
        optimizer.findBestPlan();
    }

    public DomainModel parse(CharStream charStream) {
        GraphQLLexer lexer = new GraphQLLexer(charStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        GraphQLParser parser = new GraphQLParser(tokenStream);
        DomainParser visitor = new DomainParser();

        return visitor.visit(parser.document());
    }
}