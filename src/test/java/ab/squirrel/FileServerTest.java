package ab.squirrel;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

public class FileServerTest {

  @Test
  public void testFileIsAbsent()
  {
    FileServer fs = new FileServer();
    Map<String, String> header = new HashMap<String, String>();
    header.put("Accept-Ranges", "bytes");
    //Response resp = getResponse(Map<String, String> header, String uri, String mime, String root);
    Response resp = fs.getResponse(header, "/void", "text/plain", "./src/test/resources");
    assertNull(resp);
  }

  @Test
  public void testFileIsPresent()
  {
    FileServer fs = new FileServer();
    Map<String, String> header = new HashMap<String, String>();
    header.put("Accept-Ranges", "bytes");
    //Response resp = getResponse(Map<String, String> header, String uri, String mime, String root);
    Response resp = fs.getResponse(header, "/plain.txt", "text/plain", "./src/test/resources");
    assertNotNull(resp);
    assertEquals(resp.getStatus(), Status.OK);
  }
}
