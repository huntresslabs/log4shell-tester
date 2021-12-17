package com.huntresslabs.log4shell;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class ViewHandler implements HttpHandler {

    private RedisClient redis;
    private String url;
    private String viewHTML;

    public ViewHandler(RedisClient redis, String url) {
        this.redis = redis;
        this.url = url;
        this.viewHTML = App.readResource("view.html");
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

            // Build the page :sob:
            Gson gson = new Gson();
            Map<String, Object> context = new HashMap<String, Object>();
            Collection<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();

            // Iterate over all hits
            List<String> hits = commands.lrange(uuid, 0, commands.llen(uuid));
            for(String hit : hits) {
                if( hit == "exists" ) continue;

                try {
                    Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
                    Map<String, Object> entry = gson.fromJson(hit, mapType);
                    entries.add(entry);
                } catch ( JsonParseException e ) {
                    // Parse out datetime and IP
                    String[] values = hit.split("/");
                    if( values.length != 2 ) continue;

                    // Append to results
                    Map<String, Object> entry = new HashMap<String, Object>();
                    entry.put("ip", values[0]);
                    entry.put("timestamp", values[1]);
                    entry.put("keys", "[]");

                    entries.add(entry);
                }
            }

            context.put("entries", entries);
            context.put("uuid", uuid);
            context.put("payload", "${jndi:"+this.url+"/"+uuid+"}");
            context.put("ldap_url", this.url);

            // Send the response w/ HTML type
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
            exchange.getResponseSender().send(HTTPServer.jinjava.render(viewHTML, context));

        } finally {
            connection.close();
        }
    }
}
