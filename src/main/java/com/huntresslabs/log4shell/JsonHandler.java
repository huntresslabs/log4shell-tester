package com.huntresslabs.log4shell;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public class JsonHandler implements HttpHandler {

    private RedisClient redis;

    public JsonHandler(RedisClient redis) {
        this.redis = redis;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        StatefulRedisConnection<String, String> connection = redis.connect();

        try {
            RedisCommands<String, String> commands = connection.sync();

            // Grab the UUID
            String uuid = Optional.ofNullable(exchange.getQueryParameters().get("uuid").getFirst()).orElse("");

            // Make sure this UUID is real
            if( commands.exists(uuid) == 0 ) {
                HTTPServer.notFoundHandler(exchange);
                return;
            }

            Gson gson = new Gson();
            Collection<Map<String,Object>> entries = new ArrayList<Map<String, Object>>();
            List<String> hits = commands.lrange(uuid, 0, commands.llen(uuid));

            for(String hit : hits) {
                if( hit == "exists" ) continue;

                Map<String, Object> entry;

                try {
                    Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
                    entry = gson.fromJson(hit, mapType);
                } catch ( JsonParseException e ) {
                    // Parse out datetime and IP
                    String[] values = hit.split("/");
                    if( values.length != 2 ) continue;

                    // Append to results
                    entry = new HashMap<String, Object>();
                    entry.put("ip", values[0]);
                    entry.put("timestamp", values[1]);
                    entry.put("keys", new String[] {});
                }

                entries.add(entry);
            }

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("uuid", uuid);
            result.put("hits", entries);

            // Send the response w/ HTML type
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(gson.toJson(result));

        } finally {
            connection.close();
        }
    }
}
