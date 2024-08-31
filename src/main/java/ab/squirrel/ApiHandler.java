package ab.squirrel;

import ab.squirrel.Api;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
//import java.io.PrintWriter;

import java.nio.ByteBuffer;

/*
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
*/
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApiHandler extends org.eclipse.jetty.server.Handler.Abstract
{
    private static final Logger LOG = LoggerFactory.getLogger(ApiHandler.class);

    public ApiHandler()
    {
        LOG.info("loaded");
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        String uri = request.getHttpURI().getPath();
        LOG.info(uri);

        String contentType = "text/plain; charset=utf-8";
        String content = "";

        if (uri.contains("../")) {
          LOG.error("Error 503 FORBIDDEN: Won't serve ../ for security reasons.");
          response.setStatus(503);
        } else if (uri.equals("/version")) {
          contentType = "application/json";
          content = "{\"version\": \"1.0\"}";
          response.setStatus(200);
        } else {
          // try another handler
          LOG.info("return FALSE");
          return false;
//          return super.handle(request, response, callback);
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
        LOG.info("return TRUE");
        return true;
    }

}

