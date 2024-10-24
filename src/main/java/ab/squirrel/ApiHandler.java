package ab.squirrel;

import ab.eazy.http.HttpHeader;
import ab.eazy.server.Request;
import ab.eazy.server.Response;
import ab.eazy.util.BufferUtil;
import ab.eazy.util.Callback;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
//import java.io.PrintWriter;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApiHandler extends ab.eazy.server.Handler.Abstract
{
    private static final Logger LOG = LoggerFactory.getLogger(ApiHandler.class);

    public ApiHandler()
    {
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        String uri = request.getHttpURI().getPath();

        String contentType = "text/plain; charset=utf-8";
        String content = "";

        if (uri.contains("../")) {
          LOG.info(uri);
          LOG.error("Error 503 FORBIDDEN: Won't serve ../ for security reasons.");
          response.setStatus(503);
        } else if (uri.equals("/version")) {
          LOG.info(uri);
          contentType = "application/json";
          content = "{\"version\": \"1.0\"}";
          response.setStatus(200);
        } else {
          // try another handler
          return false;
        }

        /*
        } else if (uri.startsWith("/projects")) {
          contentType = "application/json");
          Projects projects = new Projects(out);
          response.setStatus(projects.list());
        } else if (uri.startsWith("/settings")) {
          contentType = "application/json");
          Settings settings = new Settings(out);
          response.setStatus(settings.list());
        } else if (uri.startsWith("/api/")) {
          contentType = "application/json");
          Api api = new Api(uri);
          response.setStatus(api.status(out));
        } else {
          // String mime = "application/octet-stream";
          if (uri.endsWith(".css")) {
            contentType = "text/css");
          } else if (uri.endsWith(".png")) {
            contentType = "image/png");
          } else if (uri.endsWith(".js")) {
            contentType = "application/javascript");
          } else {
            contentType = "application/octet-stream");
          }
          response.setStatus(serveFile(uri, out));
        }
        */
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType);
        response.write(true, ByteBuffer.wrap(content.getBytes()), callback);
        callback.succeeded();
        return true;
    }

}

/*
package ab.squirrel;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class Api
{
    private String request;

    public Api(String uri) {
      request = uri.substring(5);
    }

    public int status(PrintWriter out) {
      out.print(request);
      return 200;
    }

}
*/
