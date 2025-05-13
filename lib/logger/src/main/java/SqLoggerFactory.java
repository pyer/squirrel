
package ab.squirrel.logger;
// Example: private static final Logger LOG = LoggerFactory.getLogger(Server.class);

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;

public class SqLoggerFactory implements ILoggerFactory
{
    private final SqLoggerConfiguration configuration;
    private final ConcurrentMap<String, SqLogger> loggerMap;

    public SqLoggerFactory(SqLoggerConfiguration config)
    {
        configuration = Objects.requireNonNull(config, "SqLoggerConfiguration");
        loggerMap = new ConcurrentHashMap<>();
        SqAppender appender = new SqAppender(configuration);
    }

    /**
     * Get a {@link SqLogger} instance, creating if not yet existing.
     *
     * @param name the name of the logger
     * @return the SqLogger instance
     */
    public SqLogger getSqLogger(String name)
    {
        return loggerMap.computeIfAbsent(name, this::createLogger);
    }

    /**
     * Main interface for {@link ILoggerFactory}
     *
     * @param name the name of the logger
     * @return the Slf4j Logger
     */
    @Override
    public Logger getLogger(String name)
    {
        return getSqLogger(name);
    }

    private SqLogger createLogger(String name)
    {
        SqAppender appender = new SqAppender(this.configuration);
        Level level = this.configuration.getLevel(name);
        boolean hideStacks = this.configuration.getHideStacks(name);
        return new SqLogger(this, name, appender, level, hideStacks);
    }
}
