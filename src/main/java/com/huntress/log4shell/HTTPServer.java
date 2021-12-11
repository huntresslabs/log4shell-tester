package com.huntresslabs.log4shell;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.server.RoutingHandler;

import io.lettuce.core.*;
import io.lettuce.core.api.*;
import io.lettuce.core.api.sync.*;

import com.huntresslabs.log4shell.IndexHandler;

public class HTTPServer
{
    private int port;
    private RedisClient redis;
    private String ldap_url;


    public HTTPServer(int port, String redis_connection_string, String ldap_url) {
        this.port = port;
        this.redis = RedisClient.create(RedisURI.create(redis_connection_string));
        this.ldap_url = ldap_url;
    }

    public int run() {
        // Setup our routes
        HttpHandler ROUTES = new RoutingHandler()
            .get("/", new IndexHandler(redis, ldap_url))
            .get("/view/{uuid}", new ViewHandler(redis, ldap_url))
            .setFallbackHandler(HTTPServer::notFoundHandler);

        // Start the server
        Undertow server = Undertow.builder()
            .addHttpListener(port, "127.0.0.1", ROUTES)
            .build();

        server.start();
        return 0;
    }

    // 404 not found handler
    public static void notFoundHandler(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send("Not found.");
    }
}
