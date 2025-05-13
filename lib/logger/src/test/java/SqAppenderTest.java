
package ab.squirrel.logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import nut.annotations.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SqAppenderTest
{

    private class CapturedStream extends PrintStream
    {
      private final ByteArrayOutputStream test;

      private CapturedStream()
      {
        super(new ByteArrayOutputStream(), true, UTF_8);
        test = (ByteArrayOutputStream)super.out;
      }

      @Override
      public String toString()
      {
        return new String(test.toByteArray());
      }
    }

    @Test(enabled=true)
    public void testSqLogFormat()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        CapturedStream output = new CapturedStream();
        SqAppender appender = (SqAppender)factory.getSqLogger("ROOT").getAppender();
        appender.setStream(output);
        SqLogger logger = factory.getSqLogger("ab.squirrel.logger.LogTest");

        String threadName = "tname";
        // Feb 17th, 2020 at 19:11:35 UTC (with 563 millis)
        long timestamp = 1581966695563L;
        appender.emit(logger, Level.INFO, timestamp, threadName, null, "testing:{},{}", "test log", "format");
        nut.Assert.assertEquals(output.toString(),"2020-02-17 19:11:35.563:INFO :asl.LogTest:tname: testing:test log,format\n");
    }

//    @Test(expectedExceptions = IllegalArgumentException.class)
    @Test
    public void testCircularThrowable()
    {
        SqLoggerConfiguration config = new SqLoggerConfiguration();
        SqLoggerFactory factory = new SqLoggerFactory(config);
        CapturedStream output = new CapturedStream();
        SqAppender appender = new SqAppender(config);
        appender.setStream(output);
        SqLogger logger = factory.getSqLogger("ab.squirrel.logger.LogTest");

        // Build an exception with circular refs.
        IllegalArgumentException commonCause = new IllegalArgumentException();
        Throwable thrown = new Throwable(commonCause);
        RuntimeException suppressed = new RuntimeException(thrown);
        thrown.addSuppressed(suppressed);

        appender.emit(logger, Level.INFO, System.currentTimeMillis(), "tname", thrown, "the message");
        nut.Assert.assertTrue(output.toString().contains("CIRCULAR REFERENCE"));
    }

    @Test
    public void testCondensedNames()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.NAME_CONDENSE_KEY, "false");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqAppender appender = new SqAppender(config);
        nut.Assert.assertFalse(appender.isCondensedNames());

        // Default value
        config = new SqLoggerConfiguration();
        appender = new SqAppender(config);
        nut.Assert.assertTrue(appender.isCondensedNames());
    }

    @Test
    public void testEscapedMessages()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.MESSAGE_ESCAPE_KEY, "false");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqAppender appender = new SqAppender(config);
        nut.Assert.assertFalse(appender.isEscapedMessages());

        // Default value
        config = new SqLoggerConfiguration();
        appender = new SqAppender(config);
        nut.Assert.assertTrue(appender.isEscapedMessages());
    }

    @Test
    public void testMessageAlignColumn()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.MESSAGE_ALIGN_KEY, "42");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqAppender appender = new SqAppender(config);
        nut.Assert.assertEquals(appender.getMessageAlignColumn(), 42);

        // Default value
        config = new SqLoggerConfiguration();
        appender = new SqAppender(config);
        nut.Assert.assertEquals(appender.getMessageAlignColumn(), 0);
    }

    @Test
    public void testPrintStream()
    {
        PrintStream output = new PrintStream(new ByteArrayOutputStream(), true, UTF_8);
        SqLoggerConfiguration config = new SqLoggerConfiguration();
        SqAppender appender = new SqAppender(config);
        nut.Assert.assertNotSame(output, appender.getStream());
        appender.setStream(output);
        nut.Assert.assertSame(output, appender.getStream());
    }

}
