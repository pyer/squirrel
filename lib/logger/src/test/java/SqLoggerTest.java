
package ab.squirrel.logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import nut.annotations.Test;
import static nut.Assert.*;

import static java.nio.charset.StandardCharsets.UTF_8;

// Levels: TRACE, DEBUG, INFO, WARN, ERROR

public class SqLoggerTest
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
        String output = new String(test.toByteArray());
        // Skip date and time
        return output.substring(24);
      }

      public boolean isEmpty()
      {
        return test.size() == 0;
      }

      public void clear()
      {
        test.reset();
      }
    }

    @Test
    public void testLogName()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        SqLogger log = factory.getSqLogger("ab.squirrel.logger.LogTest");
        assertEquals(log.getName(), "ab.squirrel.logger.LogTest");
    }
/*
    public String getName()
    public void debug(String msg)
    public void debug(String format, Object arg)
    public void debug(String format, Object arg1, Object arg2)
    public void debug(String format, Object... arguments)
    public void debug(String msg, Throwable throwable)

*/
    @Test
    public void testLogFormat()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        SqLogger log = factory.getSqLogger("ab.squirrel.logger.LogTest");

        CapturedStream output = new CapturedStream();
        SqAppender appender = log.getAppender();
        appender.setStream(output);

        output.clear();
        log.info("testing");
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing\n");

        output.clear();
        log.info("testing:{},{}", "test", "format1");
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing:test,format1\n");

        output.clear();
        log.info("testing:{}", "test", "format2");
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing:test\n");

        output.clear();
        log.info("testing", "test", "format3");
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing\n");

        output.clear();
        log.info("testing:{},{}", "test", null);
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing:test,null\n");

        output.clear();
        log.info("testing {} {}", null, null);
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing null null\n");
        //assertEquals(output.toString(), "INFO :asl.LogTest:main: testing");

        output.clear();
        log.info("testing:{}", null, null);
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing:null\n");

        output.clear();
        log.info("testing", null, null);
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing\n");

        output.clear();
        String msg = null;
        log.info(msg, "test2", "format4");
        assertEquals(output.toString(), "INFO :asl.LogTest:main: \n");
    }

    @Test
    public void testLogTrace()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        SqLogger log = factory.getSqLogger("ab.squirrel.logger.LogTest");

        CapturedStream output = new CapturedStream();
        SqAppender appender = log.getAppender();
        appender.setStream(output);

        log.setLevel(Level.TRACE);
        output.clear();
        log.trace("testing:{}", "trace");
        assertEquals(output.toString(), "TRACE:asl.LogTest:main: testing:trace\n");

        output.clear();
        log.debug("testing:{}", "debug");
        assertEquals(output.toString(), "DEBUG:asl.LogTest:main: testing:debug\n");

        log.setLevel(Level.INFO);
        output.clear();
        log.trace("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());
    }

    @Test
    public void testLogDebug()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        SqLogger log = factory.getSqLogger("ab.squirrel.logger.LogTest");

        CapturedStream output = new CapturedStream();
        SqAppender appender = log.getAppender();
        appender.setStream(output);

        log.setLevel(Level.DEBUG);
        output.clear();
        log.debug("testing:{}", "debug");
        assertEquals(output.toString(), "DEBUG:asl.LogTest:main: testing:debug\n");

        output.clear();
        log.trace("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());

        log.setLevel(Level.INFO);
        output.clear();
        log.debug("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());
    }

    @Test
    public void testLogInfo()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        SqLogger log = factory.getSqLogger("ab.squirrel.logger.LogTest");

        CapturedStream output = new CapturedStream();
        SqAppender appender = log.getAppender();
        appender.setStream(output);

        log.setLevel(Level.INFO);
        output.clear();
        log.info("testing:{}", "info");
        assertEquals(output.toString(), "INFO :asl.LogTest:main: testing:info\n");

        output.clear();
        log.debug("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());

        log.setLevel(Level.WARN);
        output.clear();
        log.info("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());
    }

    @Test
    public void testLogWarn()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        SqLogger log = factory.getSqLogger("ab.squirrel.logger.LogTest");

        CapturedStream output = new CapturedStream();
        SqAppender appender = log.getAppender();
        appender.setStream(output);

        log.setLevel(Level.WARN);
        output.clear();
        log.warn("testing:{}", "warn");
        assertEquals(output.toString(), "WARN :asl.LogTest:main: testing:warn\n");

        output.clear();
        log.info("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());

        log.setLevel(Level.ERROR);
        output.clear();
        log.warn("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());
    }

    @Test
    public void testLogError()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.ZONEID_KEY, "UTC");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqLoggerFactory factory = new SqLoggerFactory(config);
        SqLogger log = factory.getSqLogger("ab.squirrel.logger.LogTest");

        CapturedStream output = new CapturedStream();
        SqAppender appender = log.getAppender();
        appender.setStream(output);

        log.setLevel(Level.ERROR);
        output.clear();
        log.error("testing:{}", "error");
        assertEquals(output.toString(), "ERROR:asl.LogTest:main: testing:error\n");

        output.clear();
        log.warn("YOU SHOULD NOT SEE THIS!");
        assertTrue(output.isEmpty());
    }

}
