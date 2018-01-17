package ab.squirrel;

import ab.logging.Log;
import ab.squirrel.FileServer;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.StringTokenizer;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.IStatus;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

public class HttpServer extends NanoHTTPD {

    private final String  root;

    public HttpServer(String host, int port, String webroot) {
      super(host, port);
      this.root = webroot;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> header = session.getHeaders();
        Map<String, String> parms = session.getParms();
        String uri = session.getUri();
        String query = (session.getQueryParameterString() == null) ? "" : "?" + session.getQueryParameterString();
        Main.LOG.info(session.getMethod() + " " + uri + query);

        if (Main.LOG.getLevel() == Level.FINE) {
            Iterator<String> e = header.keySet().iterator();
            while (e.hasNext()) {
                String value = e.next();
                Main.LOG.fine("  HDR: '" + value + "' = '" + header.get(value) + "'");
            }
            e = parms.keySet().iterator();
            while (e.hasNext()) {
                String value = e.next();
                Main.LOG.fine("  PRM: '" + value + "' = '" + parms.get(value) + "'");
            }
        }

        // Serve services at first
        if (uri.startsWith("/service/")) {
            Main.LOG.info("SERVICE '" + uri + "' ");
            return getForbiddenResponse("Not implemented.");
        }
        // Redirect to home page
        if ("".equals(uri) || "/".equals(uri)) {
            uri = "/index.html";
            Response res = Response.newFixedLengthResponse(Status.REDIRECT, NanoHTTPD.MIME_HTML,
                  "<html><body>Redirected: <a href=\"" + uri + "\">" + uri + "</a></body></html>");
            res.addHeader("Accept-Ranges", "bytes");
            res.addHeader("Location", uri);
            return res;
        }
        // Prohibit getting out of current directory
        if (uri.contains("../")) {
            return getForbiddenResponse("Won't serve ../ for security reasons.");
        }
        String mime = getMimeTypeForFile(uri);
        Response res = new FileServer().getResponse(Collections.unmodifiableMap(header), uri, mime, root);
        if (res == null) {
            return getNotFoundResponse();
        }
        return res;
    }

    protected Response getForbiddenResponse(String s) {
        return Response.newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: " + s);
    }

    protected Response getInternalErrorResponse(String s) {
        return Response.newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "INTERNAL ERROR: " + s);
    }

    protected Response getNotFoundResponse() {
        return Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.");
    }

}
