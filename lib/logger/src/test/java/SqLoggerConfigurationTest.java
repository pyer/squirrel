
package ab.squirrel.logger;

import java.util.Properties;
import org.slf4j.event.Level;

import nut.annotations.Test;
import static nut.Assert.*;

public class SqLoggerConfigurationTest
{
    private static final Level DEFAULT_LEVEL = Level.INFO;

    @Test
    public void testConfig()
    {
        Properties props = new Properties();
        props.setProperty(SqAppender.MESSAGE_ESCAPE_KEY, "false");
        props.setProperty(SqAppender.NAME_CONDENSE_KEY, "false");
        props.setProperty(SqAppender.MESSAGE_ALIGN_KEY, "10");
        props.setProperty("com.dummy.LEVEL", "WARN");
        props.setProperty("com.dummy.STACKS", "true");

        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqAppender appender = new SqAppender(config);

        assertFalse(appender.isEscapedMessages());
        assertFalse(appender.isCondensedNames());
        assertEquals(appender.getMessageAlignColumn(), 10);

        assertEquals(config.getLevel("com.dummy"), Level.WARN);
        assertTrue(config.getHideStacks("com.dummy"));
    }

    @Test
    public void testDefaultConfig()
    {
        Properties props = new Properties();
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        SqAppender appender = new SqAppender(config);

        assertTrue(appender.isEscapedMessages());
        assertTrue(appender.isCondensedNames());
        assertEquals(appender.getMessageAlignColumn(), 0);
        assertEquals(config.getLevel("com.dummy"), DEFAULT_LEVEL);
        assertFalse(config.getHideStacks("com.dummy"));
    }

    @Test
    public void testGetLevelExact()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.LEVEL", "WARN");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        Level level = config.getLevel("com.dummy");
        assertEquals(Level.WARN, level);
    }

    @Test
    public void testGetLevelDotEnd()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.LEVEL", "WARN");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        // extra trailing dot "."
        Level level = config.getLevel("com.dummy.");
        assertEquals(Level.WARN, level);
    }

    @Test
    public void testGetLevelWithLevel()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.LEVEL", "WARN");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        // asking for name with ".LEVEL"
        Level level = config.getLevel("com.dummy.LEVEL");
        assertEquals(Level.WARN, level);
    }

    @Test
    public void testGetLevelChild()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.LEVEL", "WARN");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        Level level = config.getLevel("com.dummy.Foo");
        assertEquals(Level.WARN, level);
    }

    @Test
    public void testGetLevelDefault()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.LEVEL", "WARN");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        // asking for name that isn't configured, returns default value
        Level level = config.getLevel("ab.squirrel");
        assertEquals(DEFAULT_LEVEL, level);
    }

    @Test
    public void testGetHideStacksExact()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.STACKS", "true");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        boolean val = config.getHideStacks("com.dummy");
        assertTrue(val);
    }

    @Test
    public void testGetHideStacksDotEnd()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.STACKS", "true");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        // extra trailing dot "."
        boolean val = config.getHideStacks("com.dummy.");
        assertTrue(val);
    }

    @Test
    public void testGetHideStacksWithStacks()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.STACKS", "true");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        // asking for name with ".STACKS"
        boolean val = config.getHideStacks("com.dummy.Bar.STACKS");
        assertTrue(val);
    }

    @Test
    public void testGetHideStacksChild()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.STACKS", "true");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        boolean val = config.getHideStacks("com.dummy.Foo");
        assertTrue(val);
    }

    @Test
    public void testGetHideStacksDefault()
    {
        Properties props = new Properties();
        props.setProperty("com.dummy.STACKS", "true");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);
        // asking for name that isn't configured, returns default value
        boolean val = config.getHideStacks("ab.squirrel");
        assertFalse(val);
    }

    @Test
    public void testGetLoggingLevelBad()
    {
        Properties props = new Properties();
        props.setProperty("ab.squirrel.bad.LEVEL", "EXPECTED_BAD_LEVEL");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);

        // Default Level (because of bad level value)
        assertEquals(DEFAULT_LEVEL, config.getLevel("ab.squirrel.bad"));
    }

    @Test
    public void testGetLoggingLevelUppercase()
    {
        Properties props = new Properties();
        props.setProperty("ab.squirrel.util.LEVEL", "DEBUG");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);

        // Default Level
        assertEquals(DEFAULT_LEVEL, config.getLevel("ab.squirrel"));
        // Specific Level
        assertEquals(Level.DEBUG, config.getLevel("ab.squirrel.util"));
    }

    @Test
    public void testGetLoggingLevelLowercase()
    {
        Properties props = new Properties();
        props.setProperty("ab.squirrel.util.LEVEL", "debug");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);

        // Default Level
        assertEquals(DEFAULT_LEVEL, config.getLevel("ab.squirrel"));
        // Specific Level is default because LEVEL is case sensitive
        assertEquals(DEFAULT_LEVEL, config.getLevel("ab.squirrel.util"));
    }

    @Test
    public void testGetLoggingLevelFQCN()
    {
        String name = SqLoggerConfiguration.class.getName();
        Properties props = new Properties();
        props.setProperty(name + ".LEVEL", "TRACE");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);

        // Default Levels
        assertEquals(Level.INFO, config.getLevel(null));
        assertEquals(Level.INFO, config.getLevel(""));
        assertEquals(Level.INFO, config.getLevel("ab.squirrel"));

        // Specified Level
        assertEquals(Level.TRACE, config.getLevel(name));
    }

    @Test
    public void testGetLoggingLevelUtilLevel()
    {
        Properties props = new Properties();
        props.setProperty("ab.squirrel.util.LEVEL", "DEBUG");
        SqLoggerConfiguration config = new SqLoggerConfiguration(props);

        // Default Levels
        assertEquals(config.getLevel(null), DEFAULT_LEVEL);
        assertEquals(config.getLevel(""), DEFAULT_LEVEL);
        assertEquals(config.getLevel("ab.squirrel"), DEFAULT_LEVEL);
        assertEquals(config.getLevel("ab.squirrel.server.BogusObject"), DEFAULT_LEVEL);
        assertEquals(config.getLevel(SqLoggerConfigurationTest.class.getName()), DEFAULT_LEVEL);

        // Configured Level
        assertEquals(config.getLevel("ab.squirrel.util"), Level.DEBUG);
        assertEquals(config.getLevel("ab.squirrel.util.Bogus"), Level.DEBUG);
        assertEquals(config.getLevel("ab.squirrel.util.resource.PathResource"), Level.DEBUG);
    }

    @Test
    public void testGetLoggingLevelMixedLevels()
    {
        Properties props = new Properties();
        props.setProperty("ab.squirrel.util.LEVEL", "WARN");
        props.setProperty("ab.squirrel.util.ConcurrentHashMap.LEVEL", "TRACE");

        SqLoggerConfiguration config = new SqLoggerConfiguration(props);

        // Default Levels
        assertEquals(config.getLevel(null), DEFAULT_LEVEL);
        assertEquals(config.getLevel(""), DEFAULT_LEVEL);
        assertEquals(config.getLevel("ab.squirrel"), DEFAULT_LEVEL);
        assertEquals(config.getLevel("ab.squirrel.server.BogusObject"), DEFAULT_LEVEL);
        assertEquals(config.getLevel(SqLoggerConfigurationTest.class.getName()), DEFAULT_LEVEL);

        // Configured Level
        assertEquals(Level.WARN, config.getLevel("ab.squirrel.util.MagicUtil"));
        assertEquals(Level.WARN, config.getLevel("ab.squirrel.util"));
        assertEquals(Level.WARN, config.getLevel("ab.squirrel.util.resource.PathResource"));

        assertEquals(Level.TRACE, config.getLevel("ab.squirrel.util.ConcurrentHashMap"));
    }
}
