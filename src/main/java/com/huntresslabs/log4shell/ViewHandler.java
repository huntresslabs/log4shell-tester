package com.huntresslabs.log4shell;

import java.util.List;
import java.util.Optional;

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
            StringBuilder body = new StringBuilder();

            // Iterate over all hits
            List<String> hits = commands.lrange(uuid, 0, commands.llen(uuid));
            for(String hit : hits) {
                if( hit == "exists" ) continue;

                // Parse out datetime and IP
                String[] values = hit.split("/");
                if( values.length != 2 ) continue;

                // Append to results
                body.append("<tr><td>" + values[0] + "</td><td>" + values[1] + "</td></tr>");
            }

            String response = viewHTML.replace("BODY", body.toString());
            response = response.replace("UUID", uuid);
            response = response.replace("PAYLOAD", "${jndi:"+this.url+"/"+uuid+"}");

            // Send the response w/ HTML type
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
            exchange.getResponseSender().send(response);

        } finally {
            connection.close();
        }
    }
}
