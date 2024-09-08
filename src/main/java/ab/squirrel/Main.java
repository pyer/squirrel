package ab.squirrel;

import ab.eazy.server.Server;
import ab.eazy.server.handler.ResourceHandler;

import ab.squirrel.ApiHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String rootDir = "target/";

        LOG.info("Jetty embedded server");
        Server server = new Server(8080);
        LOG.info("Listen on port 8080");

        /* 
         * Handlers
         * */
/*
 *     ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.addHandler(new ApiHandler());
        handlers.addHandler(new ResourceHandler(rootDir, server));
        server.setHandler(handlers);
*/
        server.addHandler(new ApiHandler());
        server.addHandler(new ResourceHandler(rootDir, server));

        /*
         * Start Server
         */
        LOG.info("Start...");
        server.start();
        LOG.info("Join...");
        server.join();
    }
}

