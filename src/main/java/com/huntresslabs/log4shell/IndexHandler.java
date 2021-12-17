package com.huntresslabs.log4shell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class IndexHandler implements HttpHandler {

    private RedisClient redis;
    private String url;
    private String indexHTML;

    public IndexHandler(RedisClient redis, String url) {
        this.redis = redis;
        this.url = url;
        this.indexHTML = App.readResource("index.html");
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {

        // Connect to redis cache
        StatefulRedisConnection<String, String> conn = redis.connect();

        try {
            RedisCommands<String, String> commands = conn.sync();

            // Generate a random UUID
            String uuid = UUID.randomUUID().toString();

            // Store the GUID
            commands.lpush(uuid, "exists");
            commands.expire(uuid, 1800);

            Map<String, Object> context = new HashMap<String, Object>();
            context.put("uuid", uuid);
            context.put("ldap_url", this.url);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
            exchange.getResponseSender().send(HTTPServer.jinjava.render(this.indexHTML, context));
        } finally {
            conn.close();
        }
    }
}
