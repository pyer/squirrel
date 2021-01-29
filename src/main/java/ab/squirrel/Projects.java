package ab.squirrel;

//import ab.logging.Log;

/*
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
*/
import java.io.PrintWriter;

public class Projects
{
    private PrintWriter out;
    private String projects = "{\"projects\": [ { \"name\": \"a\" }, { \"name\": \"b\" }, { \"name\": \"c\" } ] }";

    public Projects(PrintWriter out) {
      this.out = out;
    }

    public int list() {
      out.print(projects);
      return 200;
    }

}
