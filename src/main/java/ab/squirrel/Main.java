package ab.squirrel;

import ab.logging.Log;
import ab.squirrel.Handler;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;


public class Main {

    public static void main(String[] args) throws Exception {
        Log log = new Log();
        log.info("Jetty embedded server");
        Server server = new Server();
        server.setHandler(new Handler());

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion( false );
        HttpConnectionFactory httpFactory = new HttpConnectionFactory( httpConfig );
        ServerConnector httpConnector = new ServerConnector( server,httpFactory );
        httpConnector.setPort(8080);
        server.setConnectors( new Connector[] { httpConnector } );

        // Start Server
        log.info("Start...");
        server.start();
        log.info("Join...");
        server.join();
    }
}

