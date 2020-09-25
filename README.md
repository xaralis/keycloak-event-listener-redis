# keycloak-event-listener-redis

A Keycloak SPI that publishes events via Redis PubSub. Inspired by
[keycloak-event-listener-mqtt](https://github.com/softwarefactory-project/keycloak-event-listener-mqtt).

# Build

```
mvn clean install
```

# Deploy

* Copy `target/event-listener-redis-10.0.0-jar-with-dependencies.jar` to `{KEYCLOAK_HOME}/standalone/deployments`
* Edit `standalone.xml` to configure the Redis service settings. Find the following section in the configuration:

    ```
    <subsystem xmlns="urn:jboss:domain:keycloak-server:1.1">
        <web-context>auth</web-context>
    ```

    And add below a record for redis:

    ```
    <spi name="eventsListener">
        <provider name="redis" enabled="true">
            <properties>
                <property name="host" value="[REDIS HOST]"/>
                <property name="port" value="[REDIS PORT]"/>
                <property name="db" value="[REDIS DB ID]"/>
                <property name="channel" value="[PUBSUB CHANNEL NAME]"/>
            </properties>
        </provider>
    </spi>
    ```

    Note that you can have multiple `eventsListener` providers. If you have some
    already, just append the redis block.

    Individual values have some defaults:

    - `host` defaults to `localhost`
    - `port` defaults to `6379`
    - `db` defaults to `1`
    - `channel` defaults to `keycloak/events`.

* Restart your Keycloak server.


## Docker

This plays well with Docker deployments, too. Just spawn your custom Docker
file and add something like this:

```
FROM quay.io/keycloak/keycloak:10.0.2

COPY ./extensions/event-listener-redis-10.0.0-jar-with-dependencies.jar /opt/jboss/keycloak/standalone/deployments/
COPY ./standalone-ha.xml /opt/jboss/keycloak/standalone/configuration/standalone-ha.xml

CMD ["-b", "0.0.0.0"]
```

This assumes you have
`extensions/event-listener-redis-10.0.0-jar-with-dependencies.jar` and
`standalone-ha.xml` available next to your Dockerfile. Please note that Docker
deployments use `standalone-ha.xml` by default at the time of this writing.
