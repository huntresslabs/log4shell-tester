package com.huntresslabs.log4shell;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.google.gson.Gson;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;

import org.jboss.logging.Logger;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * LDAP Server Thread which caches validated requests
 **/
public class LDAPServer
{
    private static final Logger logger = Logger.getLogger(LDAPServer.class);

    private static void log_attempt(String address, String uuid, String[] keys, Boolean valid) {
        logger.infov("ldap query with {0} uuid \"{1}\" and keys [{2}] received from {3}; dropping request.", valid ? "valid" : "invalid", uuid, String.join(",", keys), address);
    }

    public static void run(String host, int port, RedisClient redis) {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(
                "dc=example,dc=com"
            );

            config.setListenerConfigs(new InMemoryListenerConfig(
                "listen",
                InetAddress.getByName(host),
                port,
                ServerSocketFactory.getDefault(),
                SocketFactory.getDefault(),
                (SSLSocketFactory)SSLSocketFactory.getDefault()
            ));

            config.addInMemoryOperationInterceptor(new InMemoryOperationInterceptor() {
                @Override
                public void processSearchResult ( InMemoryInterceptedSearchResult result ) {
                    String key = result.getRequest().getBaseDN();
                    String uuid = "";
                    StatefulRedisConnection<String, String> connection = redis.connect();

                    try {
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
                            e2.printStackTrace();
                            return;
                        }

                        // Parse out the key values
                        String[] keys = key.split("/");
                        if( keys.length != 0 ){
                            uuid = keys[keys.length-1];
                        }

                        // Build the resulting value, storing the UTC timestamp and the requestor address
                        String when = Instant.now().toString();
                        String addr = conn.getSocket().getInetAddress().toString().replaceAll("^/", "");
                        Boolean valid = (commands.exists(uuid) != 0);
                        String[] extra_keys = Arrays.copyOfRange(keys, 0, keys.length-1);

                        // Log any requests
                        log_attempt(addr, uuid, extra_keys, valid);

                        // Ignore requests with invalid UUIDs
                        if ( ! valid ) {
                            return;
                        }

                        Gson gson = new Gson();

                        // Construct the value map
                        Map<String, Object> value = new HashMap<String, Object>();
                        value.put("ip", addr);
                        value.put("timestamp", when);
                        value.put("keys", extra_keys);

                        // Store this result
                        commands.lpush(uuid, gson.toJson(value));

                        // Keys expire after 30 minutes from creation...
                        // commands.expire(key, 1800);
                    } finally {
                        connection.close();
                    }
                }
            });
            InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);

            ds.startListening();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
