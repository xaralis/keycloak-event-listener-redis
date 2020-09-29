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

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;

import org.jboss.logging.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import org.json.JSONObject;

import java.util.Map;
import java.util.Set;
import java.lang.Exception;


public class RedisEventListenerProvider implements EventListenerProvider {
    private Set<EventType> excludedEvents;
    private Set<EventType> includedEvents;
    private Set<OperationType> excludedAdminOperations;
    private Set<OperationType> includedAdminOperations;

    private JedisPool connectionPool;
    private int db;
    private String channel;

    private static Logger logger = Logger.getLogger(RedisEventListenerProvider.class);

    public RedisEventListenerProvider(Set<EventType> excludedEvents, Set<OperationType> excludedAdminOperations, JedisPool connectionPool, int db, String channel) {
        this.excludedEvents = excludedEvents;
        this.excludedAdminOperations = excludedAdminOperations;
        this.connectionPool = connectionPool;
        this.db = db;
        this.channel = channel;
    }

    @Override
    public void onEvent(Event event) {
        if (excludedEvents.contains(event.getType())) {
            logger.debugf("Skipping publish of '%s' type event due to exclusion constraint", event.getType().toString());
            return;
        }
        if (includedEvents.size() > 0 && !includedEvents.contains(event.getType())) {
            logger.debugf("Skipping publish of '%s' type event due to inclusion constraint", event.getType().toString());
            return;
        }
        publishEvent(serialize(event));
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (excludedAdminOperations.contains(event.getOperationType())) {
            logger.debugf("Skipping publish of '%s' type admin event due to exclusion constraint", event.getOperationType().toString());
            return;
        }
        if (includedAdminOperations.size() > 0 && !includedAdminOperations.contains(event.getOperationType())) {
            logger.debugf("Skipping publish of '%s' type admin event due to inclusion constraint", event.getOperationType().toString());
            return;
        }
        publishEvent(serialize(event));
    }

    private void publishEvent(String payload) {
        Jedis redis = null;

        try {
            logger.debugf("Acquiring redis connection");
            redis = connectionPool.getResource();
            redis.select(db);
            logger.debugf("Publishing event: %s", payload);
            redis.publish(channel, payload);
            redis.close();
        } catch(Exception e) {
            logger.errorf("Could not publish Redis event: %s", e.toString());
            e.printStackTrace();
        } finally {
            if (redis != null && redis.isConnected()) {
                logger.debugf("Closing redis connection");
                redis.close();
            }
        }
    }

    private String serialize(Event event) {
        JSONObject main = new JSONObject()
            .put("type", event.getType())
            .put("source", "userAction")
            .put("realmId", event.getRealmId())
            .put("clientId", event.getClientId())
            .put("userId", event.getUserId())
            .put("ipAddress", event.getIpAddress());

        if (event.getError() != null) {
            main.put("error", event.getError());
        }

        JSONObject details = new JSONObject();

        if (event.getDetails() != null) {
            for (Map.Entry<String, String> e : event.getDetails().entrySet()) {
                details.put(e.getKey(), e.getValue());
            }
        }

        main.put("details", details);

        return main.toString();
    }

    private String serialize(AdminEvent adminEvent) {
        JSONObject main = new JSONObject()
            .put("type", adminEvent.getOperationType())
            .put("source", "adminAction")
            .put("realmId", adminEvent.getAuthDetails().getRealmId())
            .put("clientId", adminEvent.getAuthDetails().getClientId())
            .put("userId", adminEvent.getAuthDetails().getUserId())
            .put("ipAddress", adminEvent.getAuthDetails().getIpAddress())
            .put("resourcePath", adminEvent.getResourcePath());

        if (adminEvent.getError() != null) {
            main.put("error", adminEvent.getError());
        }

        return main.toString();
    }

    @Override
    public void close() {
    }

}
