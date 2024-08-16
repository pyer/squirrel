package ab.squirrel;

/*
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
*/
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
