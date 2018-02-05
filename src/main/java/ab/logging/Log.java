package ab.logging;

import ab.logging.OneLineFormatter;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
The messages will have varying levels reflecting their varying importance. The levels, and their meanings, are:
  ERROR : an error condition
  WARN : a warning
  INFO : generic (useful) information about system operation
  DEBUG : low-level information for developers
So each message has a level, and the Logger itself has a level, which acts as a filter,
so you can control the amount of information emitted from the logger without having to remove actual messages.
*/

public class Log {

  public static void init(Logger logger) {
    OneLineFormatter oneLineFormatter = new OneLineFormatter();
    Handler consoleHandler = new ConsoleHandler();
    consoleHandler.setFormatter(oneLineFormatter);
    consoleHandler.setLevel(Level.ALL);
    logger.addHandler(consoleHandler);
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
  }



/******************************************

package logtesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

public interface Loggable extends Logger {
    default Logger logger(){
        return LoggerFactory.getLogger(this.getClass());
    }

    default String getName() {
        return logger().getName();
    }

    default boolean isTraceEnabled() {
        return logger().isTraceEnabled();
    }

    default void trace(String msg) {
        logger().trace(msg);
    }

    default void trace(String format, Object arg) {
        logger().trace(format, arg);
    }

    default void trace(String format, Object arg1, Object arg2) {
        logger().trace(format, arg1, arg2);
    }

    default void trace(String format, Object... arguments) {
        logger().trace(format, arguments);
    }

    default void trace(String msg, Throwable t) {
        logger().trace(msg, t);
    }

    default boolean isTraceEnabled(Marker marker) {
        return logger().isTraceEnabled(marker);
    }

    default void trace(Marker marker, String msg) {
        logger().trace(marker, msg);
    }

    default void trace(Marker marker, String format, Object arg) {
        logger().trace(marker, format, arg);
    }

    default void trace(Marker marker, String format, Object arg1, Object arg2) {
        logger().trace(marker, format, arg1, arg2);
    }

    default void trace(Marker marker, String format, Object... argArray) {
        logger().trace(marker, format, argArray);
    }

    default void trace(Marker marker, String msg, Throwable t) {
        logger().trace(marker, msg, t);
    }

    default boolean isDebugEnabled() {
        return logger().isDebugEnabled();
    }

    default void debug(String msg) {
        logger().debug(msg);
    }

********************************************/







/*
  public void error(String msg) {
    LOG.severe(msg);
  }

  public void warning(String msg) {
    LOG.warning(msg);
  }

  public void info(String msg) {
    LOG.info(msg);
  }

  public void debug(String msg) {
    LOG.fine(msg);
  }

  public boolean isDebug() {
    return (LOG.getLevel() == Level.FINE);
  }
*/
}
/*
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class LogCustomFormatter {
    public static void main(String[] args) {
        Logger logger = Logger.getLogger(LogCustomFormatter.class.getName());
        logger.setUseParentHandlers(false);

        MyFormatter formatter = new MyFormatter();
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(formatter);

        logger.addHandler(handler);
        logger.info("Example of creating custom formatter.");
        logger.warning("A warning message.");
        logger.severe("A severe message.");
    }
}

class MyFormatter extends Formatter {
    // Create a DateFormat to format the logger timestamp.
    private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");

    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder(1000);
        builder.append(df.format(new Date(record.getMillis()))).append(" - ");
        builder.append("[").append(record.getSourceClassName()).append(".");
        builder.append(record.getSourceMethodName()).append("] - ");
        builder.append("[").append(record.getLevel()).append("] - ");
        builder.append(formatMessage(record));
        builder.append("\n");
        return builder.toString();
    }

    public String getHead(Handler h) {
        return super.getHead(h);
    }

    public String getTail(Handler h) {
        return super.getTail(h);
    }
}
*/
