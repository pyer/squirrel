package ab.squirrel;

import ab.logging.Log;

import ab.squirrel.HttpServer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.nanohttpd.protocols.http.NanoHTTPD;

public class Main {
    /**
     * logger to log to.
     */
    public static final Logger LOG = Logger.getLogger(Main.class.getName());

    /**
     * Starts as a standalone file server and waits for Enter.
     */
    public static void main(String[] args) {
        // Defaults
        int port = 8080;
        String host = null; // bind to all interfaces by default
        String home = "target/webapp";
        // Initialize logger
        Log.init(LOG);
        LOG.info("Home directory is '"+home+"'");
        LOG.info("Server listening on port "+port);

        NanoHTTPD server = new HttpServer(host, port, home);
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException ioe) {
            LOG.severe("Couldn't start server: " + ioe);
            System.exit(-1);
        }

        LOG.info("Server started, Hit Enter to stop.");

        try {
            System.in.read();
        } catch (Throwable ignored) {
        }

        server.stop();
        LOG.info("Server stopped.");
    }

}
