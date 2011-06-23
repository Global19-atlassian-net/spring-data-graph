/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.graph.neo4j.repository;

import org.springframework.core.GenericCollectionTypeResolver;
import org.springframework.data.graph.annotation.GraphQuery;
import org.springframework.data.graph.annotation.NodeEntity;
import org.springframework.data.graph.annotation.RelationshipEntity;
import org.springframework.data.graph.core.NodeBacked;
import org.springframework.data.graph.core.RelationshipBacked;
import org.springframework.data.graph.neo4j.support.GenericTypeExtractor;
import org.springframework.data.graph.neo4j.support.GraphDatabaseContext;
import org.springframework.data.graph.neo4j.support.query.QueryExecutor;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.util.Assert;
import scala.annotation.target.field;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

/**
 * @author mh
 * @since 28.03.11
 */
public class GraphRepositoryFactory extends RepositoryFactorySupport {


    private final GraphDatabaseContext graphDatabaseContext;

    public GraphRepositoryFactory(GraphDatabaseContext graphDatabaseContext) {
        Assert.notNull(graphDatabaseContext);
        this.graphDatabaseContext = graphDatabaseContext;
    }


    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.data.repository.support.RepositoryFactorySupport#
     * getTargetRepository(java.lang.Class)
     */
    @Override
    protected Object getTargetRepository(RepositoryMetadata metadata) {
        return getTargetRepository(metadata, graphDatabaseContext);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Object getTargetRepository(RepositoryMetadata metadata, GraphDatabaseContext graphDatabaseContext) {
        Class<?> repositoryInterface = metadata.getRepositoryInterface();
        Class<?> type = metadata.getDomainClass();
        GraphEntityInformation entityInformation = (GraphEntityInformation)getEntityInformation(type);

        if (entityInformation.isNodeEntity()) {
            return new NodeGraphRepository(type,graphDatabaseContext);
        } else {
            return new RelationshipGraphRepository(type,graphDatabaseContext);
        }
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata repositoryMetadata) {
        Class<?> domainClass = repositoryMetadata.getDomainClass();
        if (findAnnotation(domainClass, NodeEntity.class) !=null) {
            return NodeGraphRepository.class;
        }
        if (findAnnotation(domainClass, RelationshipEntity.class) !=null) {
            return RelationshipGraphRepository.class;
        }
        throw new IllegalArgumentException("Invalid Domain Class "+ domainClass+" neither Node- nor RelationshipEntity");
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <T, ID extends Serializable> EntityInformation<T, ID> getEntityInformation(Class<T> type) {
        return new GraphMetamodelEntityInformation(type,graphDatabaseContext);
    }


    @Override
    protected QueryLookupStrategy getQueryLookupStrategy(QueryLookupStrategy.Key key) {
        return new QueryLookupStrategy() {

            @Override
            public RepositoryQuery resolveQuery(final Method method, final RepositoryMetadata metadata, final NamedQueries namedQueries) {
                final GraphQuery queryAnnotation = method.getAnnotation(GraphQuery.class);
                if (queryAnnotation==null) return null;
                return new QueryAnnotationRepositoryQuery(queryAnnotation, method, metadata, graphDatabaseContext);
            }
        };
    }

    private static class QueryAnnotationRepositoryQuery implements RepositoryQuery {
        private final GraphQuery queryAnnotation;
        private QueryExecutor queryExecutor;
        private final Method method;
        private final RepositoryMetadata metadata;
        private boolean iterableResult;
        private Class<?> target;

        public QueryAnnotationRepositoryQuery(GraphQuery queryAnnotation, Method method, RepositoryMetadata metadata, final GraphDatabaseContext graphDatabaseContext) {
            queryExecutor = new QueryExecutor(graphDatabaseContext);
            this.queryAnnotation = queryAnnotation;
            this.method = method;
            this.metadata = metadata;
            this.iterableResult = Iterable.class.isAssignableFrom(method.getReturnType());
            this.target = resolveTarget(queryAnnotation, method);
        }

        private Class<?> resolveTarget(GraphQuery graphQuery, Method method) {
            if (!graphQuery.elementClass().equals(Object.class)) return graphQuery.elementClass();
            return GenericTypeExtractor.resolveReturnedType(method);
        }

        @Override
        public Object execute(Object[] parameters) {
            final String queryString = prepareQuery(parameters);
            return executeQuery(queryString);
        }

        private Object executeQuery(String queryString) {
            if (!iterableResult) return queryExecutor.queryForObject(queryString, target);
            if (Map.class.isAssignableFrom(target)) return queryExecutor.query(queryString);
            return queryExecutor.query(queryString,target);
        }

        private String prepareQuery(Object[] parameters) {
            Object[] resolvedParameters=resolveParameters(parameters);
            return String.format(queryAnnotation.value(), (Object[]) resolvedParameters);
        }

        private Object[] resolveParameters(Object[] parameters) {
            final Object[] result = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                result[i] = resolveParameter(parameters[i]);
            }
            return result;
        }

        private Object resolveParameter(Object parameter) {
            if (parameter instanceof NodeBacked) {
                return ((NodeBacked)parameter).getNodeId();
            }
            if (parameter instanceof RelationshipBacked) {
                return ((RelationshipBacked)parameter).getRelationshipId();
            }
            return parameter;
        }

        @Override
        public QueryMethod getQueryMethod() {
            return new QueryMethod(method, metadata);
        }
    }
}