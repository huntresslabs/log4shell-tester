package com.huntresslabs.log4shell;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.lang.reflect.Field;
import java.lang.Thread;
import java.time.Instant;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;

import org.jboss.logging.Logger;

import io.lettuce.core.*;
import io.lettuce.core.api.*;
import io.lettuce.core.api.sync.*;

/**
 * LDAP Server Thread which caches validated requests
 **/
public class LDAPServer
{
    private int port;
    private RedisClient redis;
    private static final Logger logger = Logger.getLogger(LDAPServer.class);

    public LDAPServer(int port, String redis_connection_string) {
        this.port = port;
        this.redis = RedisClient.create(RedisURI.create(redis_connection_string));
    }

    public void run() {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(
                "dc=example,dc=com"
            );

            config.setListenerConfigs(new InMemoryListenerConfig(
                "listen",
                InetAddress.getByName("0.0.0.0"),
                this.port,
                ServerSocketFactory.getDefault(),
                SocketFactory.getDefault(),
                (SSLSocketFactory)SSLSocketFactory.getDefault()
            ));

            config.addInMemoryOperationInterceptor(new OperationInterceptor());
            InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);

            ds.startListening();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // this.redis.shutdown();
    }

    private class OperationInterceptor extends InMemoryOperationInterceptor {
        @Override
        public void processSearchResult ( InMemoryInterceptedSearchResult result ) {
            String key = result.getRequest().getBaseDN();
            StatefulRedisConnection<String, String> connection = redis.connect();
            RedisCommands<String, String> commands = connection.sync();
            LDAPListenerClientConnection conn;

            // Send an error response regardless
            result.setResult(new LDAPResult(0, ResultCode.OPERATIONS_ERROR));

            // This is a gross reflection block to get the client address
            try {
                Field field = result.getClass().getSuperclass().getDeclaredField("clientConnection");
                field.setAccessible(true);

                conn = (LDAPListenerClientConnection)field.get(result);
            } catch ( Exception e2 ) {
                connection.close();
                e2.printStackTrace();
                return;
            }

            // Build the resulting value, storing the UTC timestamp and the requestor address
            String when = Instant.now().toString();
            String addr = conn.getSocket().getInetAddress().toString().replaceAll("^/", "");
            String value = addr + "/" + when;

            // Only respect keys that exist
            if ( commands.exists(key) == 0 ) {
                logger.error("received ldap query from " + addr + " with invalid base DN '" + key + "'");
                return;
            } else {
                logger.info("dropped ldap query from " + addr + " with base DN '" + key + "'");
            }

            // Store this result
            commands.lpush(key, value);
            commands.expire(key, 1800);

            // Close the connection
            connection.close();
        }

    }
}
