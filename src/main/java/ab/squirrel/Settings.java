package ab.squirrel;

import ab.logging.Log;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;


public class Settings
{
    private PrintWriter out;

    public Settings(PrintWriter out) {
     this.out = out;
    }

    public int list() {
      Log log = new Log();
      log.info("Read settings");

      out.print("{\"settings\": [ ");

        try {
          String line;
          BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/settings.properties"));
          while ((line = reader.readLine()) != null) {
            String trimed = line.trim();
            if ( !(trimed.startsWith("#") || trimed.isEmpty()) ) {
              log.info(trimed);
              String[] parts = trimed.split("=",2);
              String name = parts[0].trim();
              String value = parts[1].trim();

              out.print("{ \"name\": \"" + name + "\", \"value\": \"" + value + "\" }, ");
            }
          }
          reader.close();
        } catch ( FileNotFoundException e ) {
          log.info( e.getMessage() );
        } catch ( IOException e ) {
          log.info( e.getMessage() );
        }

      out.print("{ \"name\": \"\", \"value\": \"\" }");
      out.print(" ]}");
      return 200;
    }
}
