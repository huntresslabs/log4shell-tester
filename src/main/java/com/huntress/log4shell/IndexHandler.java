package com.huntresslabs.log4shell;

import java.util.UUID;
import java.lang.StringBuilder;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import io.undertow.util.Headers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import io.lettuce.core.*;
import io.lettuce.core.api.*;
import io.lettuce.core.api.sync.*;

import com.huntresslabs.log4shell.App;

public class IndexHandler implements HttpHandler {

    private RedisClient redis;
    private String url;

    public IndexHandler(RedisClient redis, String url) {
        this.redis = redis;
        this.url = url;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {

        // Connect to redis cache
        StatefulRedisConnection<String, String> conn = redis.connect();
        RedisCommands<String, String> commands = conn.sync();

        // Generate a random UUID
        String uuid = UUID.randomUUID().toString();

        // Store the GUID
        commands.lpush(uuid, "exists");

        String response = App.indexHTML.replace("GUID", uuid);
        response = response.replace("PAYLOAD", "${jndi:"+this.url+"/"+uuid+"}");

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(response.toString());

        conn.close();
    }
}
