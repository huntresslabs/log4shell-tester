package com.huntresslabs.log4shell;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import com.huntresslabs.log4shell.LDAPServer;
import com.huntresslabs.log4shell.HTTPServer;

/**
 * Main application code
 */
public class App 
{
    public static String indexHTML;
    public static String viewHTML;

    public static void main( String[] args )
    {
        String redis_uri, hostname, ldap_url;
        int ldap_port, http_port;

        if( args.length < 3 || args.length > 4 ) {
            System.err.println("error: usage: log4shell <hostname> <http_port> <ldap_port> [<redis_url>]");
            System.exit(1);
        }

        if( args.length == 4 ) {
            redis_uri = args[3];
        } else {
            redis_uri = "redis://localhost:6379";
        }

        try{
            http_port = Integer.parseInt(args[1]);
        } catch( Exception e ) {
            System.err.println("error: http_port: must be an integer");
            System.exit(1);
            return;
        }

        try {
            ldap_port = Integer.parseInt(args[2]);
        } catch( Exception e ) {
            System.err.println("error: ldap_port: must be an integer");
            System.exit(1);
            return;
        }

        hostname = args[0];
        ldap_url = "ldap://" + hostname + ":" + ldap_port;

        // Pre-load these to improve performance at scale
        indexHTML = App.readResource("index.html");
        viewHTML = App.readResource("view.html");

        HTTPServer http = new HTTPServer(http_port, redis_uri, ldap_url);
        http.run();

        LDAPServer ldap = new LDAPServer(ldap_port, redis_uri);
        ldap.run();
    }

    public static String readResource(String name) {

        ClassLoader cLoader = App.class.getClassLoader();

        return new BufferedReader(
            new InputStreamReader(cLoader.getResourceAsStream(name))
        ).lines().collect(Collectors.joining("\n"));
    }
}
