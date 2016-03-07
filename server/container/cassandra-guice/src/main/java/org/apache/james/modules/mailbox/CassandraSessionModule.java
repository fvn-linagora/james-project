/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.modules.mailbox;

import java.io.FileNotFoundException;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.modules.server.DefaultRetryer;
import org.apache.james.modules.server.Retryer;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
//import com.github.rholder.retry.Retryer;
//import com.github.rholder.retry.RetryerBuilder;
//import com.github.rholder.retry.StopStrategies;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class CassandraSessionModule extends AbstractModule {

//    private static Retryer<Cluster> retryer = RetryerBuilder.<Cluster>newBuilder()
//            .retryIfExceptionOfType(NoHostAvailableException.class)
//            .withStopStrategy(StopStrategies.stopAfterDelay(2, TimeUnit.MINUTES))
//            .build();

    @Override
    protected void configure() {
    }
    
    @Provides
    @Singleton
    CassandraModule composeDataDefinitions(Set<CassandraModule> modules) {
        return new CassandraModuleComposite(modules.toArray(new CassandraModule[0]));
    }

    @Provides
    @Singleton
    Session provideSession(FileSystem fileSystem, Cluster cluster, CassandraModule cassandraModule)
            throws FileNotFoundException, ConfigurationException{
        PropertiesConfiguration configuration = getConfiguration(fileSystem);
        String keyspace = configuration.getString("cassandra.keyspace");
        return new SessionWithInitializedTablesFactory(cassandraModule).createSession(cluster, keyspace);
    }

    @Provides
    @Singleton
    Cluster provideCluster(FileSystem fileSystem) throws FileNotFoundException, ConfigurationException {
        PropertiesConfiguration configuration = getConfiguration(fileSystem);

        return buildRetryer(configuration).retry(getClusterProvider(), configuration);
    }

    private Retryer<Cluster> buildRetryer(PropertiesConfiguration configuration) {
        return DefaultRetryer.<Cluster>builder()
                .retryIfExceptionOfType(NoHostAvailableException.class)
                .withMaxRetries(configuration.getInt("cassandra.retryConnection.maxAttempt", 5))
                .withFixedDelay(configuration.getInt("cassandra.retryConnection.delayBetweenAttempts", 7000))
                .build();
    }

    private Function<PropertiesConfiguration, Cluster> getClusterProvider() {
        return conf -> ClusterWithKeyspaceCreatedFactory.clusterWithInitializedKeyspace(
                ClusterFactory.createClusterForSingleServerWithoutPassWord(
                        conf.getString("cassandra.ip"),
                        conf.getInt("cassandra.port")),
                conf.getString("cassandra.keyspace"),
                conf.getInt("cassandra.replication.factor"));
    }

    private PropertiesConfiguration getConfiguration(FileSystem fileSystem) throws FileNotFoundException, ConfigurationException {
        return new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "cassandra.properties"));
    }

//        Callable<Cluster> callable = new Callable<Cluster>() {
//            @Override
//            public Cluster call() throws Exception {
//                return null;
//            }
//        }
//
//        Function<PropertiesConfiguration, Cluster> clusterProvider = (conf) -> retryer.call(ClusterWithKeyspaceCreatedFactory.clusterWithInitializedKeyspace(
//                ClusterFactory.createClusterForSingleServerWithoutPassWord(
//                        conf.getString("cassandra.ip"),
//                        conf.getInt("cassandra.port")),
//                conf.getString("cassandra.keyspace"),
//                conf.getInt("cassandra.replication.factor")));
//
//        return clusterProvider.get(configuration);
}