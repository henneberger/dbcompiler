package dbcompiler;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.*;

public class DomainModel {
    public Map<String, Entity> entities = new HashMap<>();
    public Map<String, QueryDefinition> queryDefinitionMap = new HashMap<>();
    public List<Query> queries = new ArrayList<>();
    public List<Mutation> mutations = new ArrayList<>();

    /**
     * query Name(arguments)
     * \@sla(throughput_per_second: 1, latency_ms: 1000)
     * {
     *     ...
     * }
     */
    public static class Query {
        public String name;
        public List<QueryDefinitionSelection> selections;
        public List<Argument> arguments;
        public SLADirective sla;

        /**
         * SLA Directive
         */
        public class SLADirective {
            public int throughput_per_second = 1;
            public int latency_ms = 10000;
        }

        /**
         * A query or mutation argument
         */
        public class Argument {
            public String name;
            public String variable;
        }

        /**
         * A root query definition
         */
        public class QueryDefinitionSelection extends QuerySelection {
            public QueryDefinition definition;
            public int pageSize = 10;

            public Query getQuery() {
                return Query.this;
            }

            @Override
            public String toString() {
                return definition.sqlClause.toString();
            }
        }

        /**
         * Selection with children
         */
        public class QuerySelection {
            public Entity.Field field;
            public List<QuerySelection> selections;

            /**
             * A query selection's variable.
             * user_id: $user_id
             */
            public class Variable {
                String ref;
                Argument argument;
            }
        }
    }

    /**
     * type Name
     *   \@size(max: 10000) {
     *     field: field \@selectivity(max: 1000)
     * }
     */
    public static class Entity {
        public String entityName;
        public Map<String, Field> fieldMap;
        public EntitySizeDirective size;
        public Map<Set<FieldPath>, Selectivity> selectivityMap;

        @AllArgsConstructor
        @ToString
        public class Field {
            public String name;
            public TypeDef typeDef;
            public boolean immutable;
        }

        public class EntitySizeDirective {
            int max = 999999999;
        }
    }

    @ToString
    public static class Mutation {
        public String name;
        public MutationSla sla;
        public Map<String, Selection> selectionSet;
        @ToString.Exclude
        public Entity entity;
        public MutationType mutationType;
        public List<QueryDefinition.SqlClause.Conjunction> clause;

        @AllArgsConstructor
        @ToString
        public class MutationSla {
            public int max_tables;
        }
    }

    @AllArgsConstructor
    @ToString
    public static class TypeDef {
        public String typeName;
        @ToString.Exclude
        public DomainModel model;
        public Multiplicity multiplicity;
        public boolean nonnull;

        //Lazy since entity may not be available during parsing
        public Entity getEntity() {
            return model.entities.get(typeName);
        }

        public enum Multiplicity {
            SINGLE, LIST
        }
    }

    /**
     * Query definition roots
     * type Query {
     *     query1(a: ID): Entity @sql(where: "a=$a")
     * }
     *
     */
    @AllArgsConstructor
    public static class QueryDefinition {
        public String name;
        public TypeDef type;
        public SqlClause sqlClause;

        @AllArgsConstructor
        public static class SqlClause {
            public Entity rootEntity;
            public List<Conjunction> conjunctions;
            public List<OrderBy> orders;
            public String toStr;

            public static class Conjunction {
                public final FieldPath fieldPath;
                public final Object value;
                public final Op op = Op.eq;

                public Conjunction(FieldPath fieldPath, Object value) {
                    this.value = value;
                    this.fieldPath = fieldPath;
                }
                enum Op {
                    eq
                }
            }

            @Override
            public String toString() {
                return toStr;
            }
        }

    }

    @EqualsAndHashCode
    @AllArgsConstructor
    public static class FieldPath {
        public List<Entity.Field> fields;
        public String toStringVal;
        public Entity entity;
        public boolean sargable;

        public boolean isSargable() {
            return sargable;
        }

        @Override
        public String toString() {
            return toStringVal;
        }
    }

    /**
     * Selectivity directive for fields and entities
     */
    public static class Selectivity {
        public int distinct = 0;
        public Distribution distribution;
        public Set<FieldPath> fields;
    }

    public static interface Distribution {
        public double calculateExpected(int needed_items, int distinct);
    }
    public static class BinomialDistribution implements Distribution {

        @Override
        public double calculateExpected(int needed_items, int distinct) {
            //todo: Actual calculation for expected search
            return (double) (1.645 * (needed_items / (1d / distinct)));
        }
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    public static class OrderBy {
        public FieldPath path;
        public Direction direction;

        @Override
        public String toString() {
            return path.toStringVal + " " + direction.name().charAt(0);
        }
    }

    public static enum Direction {
        DESC, ASC, ANY
    }

    @ToString
    public static class SelectionSet {
        Map<String, Selection> selections;
    }

    @ToString(callSuper=true)
    public static class Selection extends SelectionSet {
        Entity.Field field;
    }

    public static enum MutationType {
        INSERT, UPDATE, DELETE
    }
}