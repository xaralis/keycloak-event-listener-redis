/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xixoio.keycloak.providers.events.redis;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import org.jboss.logging.Logger;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashSet;
import java.util.Set;
import java.time.Duration;

public class RedisEventListenerProviderFactory implements EventListenerProviderFactory {
    private Set<EventType> excludedEvents;
    private Set<OperationType> excludedAdminOperations;
    private Set<EventType> includedEvents;
    private Set<OperationType> includedAdminOperations;
    private String channel;
    private String host;
    private int port;
    private int db;
    private JedisPool jedisPool;

    private static Logger logger = Logger.getLogger(RedisEventListenerProviderFactory.class);

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new RedisEventListenerProvider(excludedEvents, excludedAdminOperations, jedisPool, db, channel);
    }

    @Override
    public void init(Config.Scope config) throws RuntimeException {
        excludedEvents = createEventSet(config.getArray("exclude-events"), EventType.class);
        includedEvents = createEventSet(config.getArray("include-events"), EventType.class);
        excludedAdminOperations = createEventSet(config.getArray("exclude-admin-operations"), OperationType.class);
        includedAdminOperations = createEventSet(config.getArray("include-admin-operations"), OperationType.class);

        HashSet<EventType> eventIntersection = new HashSet<EventType>(excludedEvents);
        eventIntersection.retainAll(includedEvents);

        HashSet<OperationType> adminOpIntersection = new HashSet<OperationType>(excludedAdminOperations);
        adminOpIntersection.retainAll(includedAdminOperations);

        if (!eventIntersection.isEmpty()) {
            logger.errorf("exclude-events and include-events contain conflicting items");
            throw new RuntimeException("exclude-events and include-events contain conflicting items");
        }

        if (!adminOpIntersection.isEmpty()) {
            logger.errorf("exclude-admin-operations and include-admin-operations contain conflicting items");
            throw new RuntimeException("exclude-admin-operations and include-admin-operations contain conflicting items");
        }

        host = config.get("host", "localhost");
        port = config.getInt("port", 6379);
        db = config.getInt("db", 1);
        channel = config.get("channel", "keycloak/events");

        logger.infof("Initialized Redis event listener, using 'redis://%s:%d/%d' Redis instance and '%s' channel", host, port, db, channel);

        if (excludedEvents.size() > 0) {
            logger.infof("Excluded events: %s", excludedEvents.toString());
        }
        if (includedEvents.size() > 0) {
            logger.infof("Included events: %s", includedEvents.toString());
        }
        if (excludedAdminOperations.size() > 0) {
            logger.infof("Excluded admin operations : %s", excludedAdminOperations.toString());
        }
        if (includedAdminOperations.size() > 0) {
            logger.infof("Included admin operations: %s", includedAdminOperations.toString());
        }

        jedisPool = new JedisPool(buildPoolConfig(), host, port);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {
        logger.debugf("Closing redis pool");
        jedisPool.close();
    }

    @Override
    public String getId() {
        return "redis";
    }

    private static <E extends Enum<E>> HashSet<E> createEventSet(String[] input, Class<E> enumType) {
        HashSet<E> output = new HashSet<E>();

        if (input != null) {
            for (String e : input) {
                output.add(Enum.valueOf(enumType, e));
            }
        }

        return output;
    }

    private JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();

        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);

        return poolConfig;
    }

}
