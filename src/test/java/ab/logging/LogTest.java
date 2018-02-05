package ab.logging;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogTest
{
  public static final Logger LOG = Logger.getLogger(LogTest.class.getName());

  @Test
  public void testLogInit()
  {
    Log.init(LOG);
    assertNotNull( LOG.getHandlers() );
    assertFalse( LOG.getUseParentHandlers() );
    assertEquals( Level.ALL, LOG.getLevel() );
  }
}
