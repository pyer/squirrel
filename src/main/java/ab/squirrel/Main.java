package ab.squirrel;

import ab.squirrel.ApiHandler;
import ab.squirrel.ResourceHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jetty.server.Connector;
//import org.eclipse.jetty.server.HttpConfiguration;
//import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
//import org.eclipse.jetty.server.handler.ResourceHandler;
//import org.eclipse.jetty.util.resource.ResourceFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String rootDir = "target/";

        LOG.info("Jetty embedded server");
        Server server = new Server();

        /*
         * Connector
         */
/*
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion( false );
        HttpConnectionFactory httpFactory = new HttpConnectionFactory( httpConfig );

        ServerConnector httpConnector = new ServerConnector( server,httpFactory );
*/
        ServerConnector httpConnector = new ServerConnector( server );
        // The address to bind to.
        httpConnector.setHost("127.0.0.1");
        // The port to listen to.
        LOG.info("Listen on port 8080");
        httpConnector.setPort(8080);
        server.addConnector(httpConnector);

        /* 
         * Handlers
         * */
        ContextHandlerCollection handlers = new ContextHandlerCollection();
        server.setHandler(handlers);

        // Add application handler
        handlers.addHandler(new ApiHandler());
/*
        // Add static resources handler
        Path rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootPath)) {
            LOG.error("ERROR: Unable to find " + rootPath);
            System.exit(-1);
        }
        ResourceFactory resourceFactory = ResourceFactory.of(server);
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setBaseResource(resourceFactory.newResource(rootPath));
        resourceHandler.setDirAllowed(true);
//        resourceHandler.setWelcomeFiles("index.html");
        LOG.info("Root directory is " + rootDir);
        */
    //    handlers.addHandler(new ResourceHandler(rootDir, server));

        /*
         * Start Server
         */
        LOG.info("Start...");
        server.start();
        LOG.info("Join...");
        server.join();
    }
}

