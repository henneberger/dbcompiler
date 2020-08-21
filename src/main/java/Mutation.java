public class Mutation extends ModelObject {
    private String name;

    public Mutation(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Mutation{" +
                "name='" + name + '\'' +
                '}';
    }
}
