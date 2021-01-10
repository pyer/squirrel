package ab.squirrel;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class Handler extends AbstractHandler
{
    private static final Logger log = Logger.getLogger(Main.class.getName());

    public void handle( String target,
                        Request baseRequest,
                        HttpServletRequest request,
                        HttpServletResponse response )
        throws IOException, ServletException
    {
        String uri = request.getRequestURI();
        log.info(uri);
        PrintWriter out = response.getWriter();
        if ("".equals(uri) || "/".equals(uri)) {
          response.setContentType("text/html; charset=utf-8");
          response.setStatus(serveFile("/index.html", out));
        } else if (uri.contains("../")) {
          log.info("Error 503 FORBIDDEN: Won't serve ../ for security reasons.");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        } else {
          // String mime = "application/octet-stream";
          if (uri.endsWith(".css")) {
            response.setContentType("text/css");
          } else if (uri.endsWith(".png")) {
            response.setContentType("image/png");
          } else if (uri.endsWith(".js")) {
            response.setContentType("application/javascript");
          } else {
            response.setContentType("application/octet-stream");
          }
          response.setStatus(serveFile(uri, out));
        }
        baseRequest.setHandled(true);
    }

    private int serveFile(String path, PrintWriter out) {
        log.info("File " + path);
        try {
          FileInputStream fileInputStream = new FileInputStream("src/main/webapp" + path);
          int ret;
          char singleChar;
          while((ret = fileInputStream.read()) != -1) {
            out.print( (char) ret );
          }
          fileInputStream.close();
        } catch (IOException e) {
          log.info(e.getMessage()); 
          return HttpServletResponse.SC_FORBIDDEN;
        }
        return HttpServletResponse.SC_OK;
    }
}
