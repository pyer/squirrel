package ab.squirrel.logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.net.URL;
//import java.util.Arrays;
import java.util.Properties;
import java.util.TimeZone;

import org.slf4j.event.Level;

/**
 * SqLogger specific configuration:
 * <ul>
 *  <li>{@code <name>.LEVEL=(String:LevelName)}</li>
 *  <li>{@code <name>.STACKS=(boolean)}</li>
 * </ul>
 */
public class SqLoggerConfiguration
{
    private static final String DEFAULT_PROPERTIES_FILE = "logging.properties";
    private static final Level DEFAULT_LEVEL = Level.INFO;
    private static final String SUFFIX_LEVEL = ".LEVEL";
    private static final Boolean DEFAULT_HIDE_STACKS = false;
    private static final String SUFFIX_STACKS = ".STACKS";

    private final Properties properties = new Properties();

    /**
     * Default SqLogger configuration (empty)
     * or the content of the file logging.properties if it exists
     */
    public SqLoggerConfiguration()
    {
        try {
            // load the properties file
            InputStream input = new FileInputStream(DEFAULT_PROPERTIES_FILE);
            properties.load(input);
        } catch (IOException ex) {
            //System.err.printf("WARNING: File '%s' not found.\n", DEFAULT_PROPERTIES_FILE);
        }
    }

    /**
     * SqLogger configuration from provided Properties
     *
     * @param props A set of properties to base this configuration off of
     */
    public SqLoggerConfiguration(Properties props)
    {
        if (props != null) {
            for (String name : props.stringPropertyNames()) {
                String val = props.getProperty(name);
                // Protect against application code insertion of non-String values (returned as null).
                if (val != null)
                    properties.setProperty(name, val);
            }
        }
    }

    /**
     * <p>Returns the HideStacks status for the provided log name.</p>
     * <p>Uses the FQCN first, then each package segment from longest to shortest.</p>
     *
     * @param name the name to get log for
     * @return the status
     */
    public boolean getHideStacks(String name)
    {
        if (properties.isEmpty())
            return DEFAULT_HIDE_STACKS;

        String startName = name != null ? name : "";
        // strip trailing dot
        while (startName.endsWith(".")) {
            startName = startName.substring(0, startName.length() - 1);
        }

        // strip ".STACKS" suffix (if present)
        if (startName.endsWith(SUFFIX_STACKS)) {
            startName = startName.substring(0, startName.length() - SUFFIX_STACKS.length());
        }

        return findHideStacksThroughLoggerNames(startName);
    }

    private Boolean findHideStacksThroughLoggerNames(String loggerName)
    {
        // Checking with FQCN first, then each package segment from longest to shortest.
        String nameSegment = loggerName;
        while (nameSegment.length() > 0) {
            String statusStr = properties.getProperty(nameSegment + SUFFIX_STACKS);
            if (statusStr != null) {
                return Boolean.parseBoolean(statusStr);
            }
            // Trim and try again.
            int idx = nameSegment.lastIndexOf('.');
            if (idx >= 0)
                nameSegment = nameSegment.substring(0, idx);
            else
                break;
        }

        return DEFAULT_HIDE_STACKS;
    }

    /**
     * <p>Returns the Logging Level for the provided log name.</p>
     * <p>Uses the FQCN first, then each package segment from longest to shortest.</p>
     *
     * @param name the name to get log for
     * @return the logging level
     */
    public Level getLevel(String name)
    {
        if (properties.isEmpty())
            return DEFAULT_LEVEL;

        String startName = name != null ? name : "";
        // Strip trailing dot.
        while (startName.endsWith(".")) {
            startName = startName.substring(0, startName.length() - 1);
        }

        // Strip ".LEVEL" suffix (if present).
        if (startName.endsWith(SUFFIX_LEVEL)) {
            startName = startName.substring(0, startName.length() - SUFFIX_LEVEL.length());
        }

        return findLevelThroughLoggerNames(startName);
    }

    private Level findLevelThroughLoggerNames(String loggerName)
    {
        // Checking with FQCN first, then each package segment from longest to shortest.
        String nameSegment = loggerName;
        while (nameSegment.length() > 0) {
            String levelStr = properties.getProperty(nameSegment + SUFFIX_LEVEL);
            if (levelStr != null) {
                for (Level level : Level.values()) {
                    if (level.name().equals(levelStr))
                        return level;
                }
//                System.err.printf("Unknown Logger/SLF4J Level [%s], expecting only %s as values.\n",
//                    levelStr, Arrays.toString(Level.values()));
            }
            // Trim and try again.
            int idx = nameSegment.lastIndexOf('.');
            if (idx >= 0)
                nameSegment = nameSegment.substring(0, idx);
            else
                break;
        }

        return DEFAULT_LEVEL;
    }
/*
  private static final Set<String> VALUES = Set.of(
    "AB","BC","CD","AE"
);
"Given String s, is there a good way of testing whether VALUES contains s?"

VALUES.contains(s)
*/

    public TimeZone getTimeZone(String key)
    {
        String zoneIdStr = properties.getProperty(key);
        if (zoneIdStr == null)
            return null;
        return TimeZone.getTimeZone(zoneIdStr);
    }


    public boolean getBoolean(String key, boolean defValue)
    {
        String val = properties.getProperty(key, Boolean.toString(defValue));
        return Boolean.parseBoolean(val);
    }

    public int getInt(String key, int defValue)
    {
        String val = properties.getProperty(key, Integer.toString(defValue));
        if (val == null)
            return defValue;
        try
        {
            return Integer.parseInt(val);
        }
        catch (NumberFormatException e)
        {
            return defValue;
        }
    }

    public String getString(String key, String defValue)
    {
        return properties.getProperty(key, defValue);
    }

}
