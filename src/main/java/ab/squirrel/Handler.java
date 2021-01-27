package ab.squirrel;

import ab.logging.Log;
import ab.squirrel.Api;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/*
#default mime types
###################
css=text/css
htm=text/html
html=text/html
xml=text/xml
java=text/x-java-source, text/java
md=text/plain
txt=text/plain
asc=text/plain
gif=image/gif
jpg=image/jpeg
jpeg=image/jpeg
png=image/png
svg=image/svg+xml
mp3=audio/mpeg
m3u=audio/mpeg-url
mp4=video/mp4
ogv=video/ogg
flv=video/x-flv
mov=video/quicktime
swf=application/x-shockwave-flash
js=application/javascript
pdf=application/pdf
doc=application/msword
ogg=application/x-ogg
zip=application/octet-stream
exe=application/octet-stream
class=application/octet-stream
m3u8=application/vnd.apple.mpegurl
json=application/json
*/

public class Handler extends AbstractHandler
{

    public void handle( String target,
                        Request baseRequest,
                        HttpServletRequest request,
                        HttpServletResponse response )
        throws IOException
    {
        Log log = new Log();
        String uri = request.getRequestURI();
        log.info(uri);
        PrintWriter out = response.getWriter();
        if ("".equals(uri) || "/".equals(uri)) {
          response.setContentType("text/html; charset=utf-8");
          response.setStatus(serveFile("/index.html", out));
        } else if (uri.contains("../")) {
          log.error("Error 503 FORBIDDEN: Won't serve ../ for security reasons.");
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } else if (uri.equals("/version")) {
          response.setContentType("application/json");
          out.print( "{\"version\": \"1.0\"}" );
          response.setStatus(HttpServletResponse.SC_OK);
        } else if (uri.equals("/favicon.ico")) {
          response.setContentType("image/png");
          response.setStatus(serveFile("/images/logo.png", out));
        } else if (uri.startsWith("/projects")) {
          response.setContentType("application/json");
          Projects projects = new Projects(out);
          response.setStatus(projects.list());
        } else if (uri.startsWith("/api/")) {
          response.setContentType("application/json");
          Api api = new Api(uri);
          response.setStatus(api.status(out));
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
        Log log = new Log();
        log.debug("File " + path);
        try {
          FileInputStream fileInputStream = new FileInputStream("src/main/webapp" + path);
          int ret;
          char singleChar;
          while((ret = fileInputStream.read()) != -1) {
            out.print( (char) ret );
          }
          fileInputStream.close();
        } catch (IOException e) {
          log.warn(e.getMessage()); 
          return HttpServletResponse.SC_FORBIDDEN;
        }
        return HttpServletResponse.SC_OK;
    }
}
