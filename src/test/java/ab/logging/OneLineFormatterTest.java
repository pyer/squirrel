package ab.logging;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;

public class OneLineFormatterTest {

  @Test
  public void testLineIsFormatted()
  {
    LogRecord log = new LogRecord(Level.INFO, "message");
    OneLineFormatter fmt = new OneLineFormatter();
    String msg = fmt.format(log);
    assertNotNull(msg);
    assertEquals(msg.length(),89);
    // 2018-02-05 21:41:40:746 INFO   [1]  - null                                     - message
    // skip date and time
    assertEquals(msg.substring(23,43), " INFO   [1]  - null ");
    assertEquals(msg.substring(78,89), " - message\n");
  }
}
