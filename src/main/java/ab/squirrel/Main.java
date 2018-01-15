package ab.squirrel;

import ab.squirrel.WebServer;
import java.io.IOException;
import org.nanohttpd.protocols.http.NanoHTTPD;

public class Main {
    /**
     * Starts as a standalone file server and waits for Enter.
     */
    public static void main(String[] args) {
        // Defaults
        int port = 8080;
        String host = null; // bind to all interfaces by default
        String home = "target/webapp";

        NanoHTTPD server = new WebServer(host, port, home);
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
            System.exit(-1);
        }

        System.out.println("Server started, Hit Enter to stop.\n");

        try {
            System.in.read();
        } catch (Throwable ignored) {
        }

        server.stop();
        System.out.println("Server stopped.\n");
    }

}
