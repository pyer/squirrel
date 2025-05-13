//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
// Example: private static final Logger LOG = LoggerFactory.getLogger(Server.class);


package ab.squirrel.logger;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.SubstituteLogger;

public class SqLogger implements Logger
{
    private final SqLoggerFactory factory;
    private final String name;
    private final SqAppender appender;
    private int levelInt;
    private boolean hideStacks;

    public SqLogger(SqLoggerFactory factory, String name, SqAppender appender)
    {
        this(factory, name, appender, Level.INFO, false);
    }

    public SqLogger(SqLoggerFactory factory, String name, SqAppender appender, Level level, boolean hideStacks)
    {
        this.factory = factory;
        this.name = name;
        this.appender = appender;
        this.levelInt = level.toInt();
        this.hideStacks = hideStacks;
    }

    /**
     * Tests that a provided level is included by the level value of this level.
     *
     * @param testLevel the level to test against.
     * @return true if includes this includes the test level.
     */
    private boolean levelIncludes(Level testLevel)
    {
        return (this.levelInt <= testLevel.toInt());
    }

    public void setLevel(Level level)
    {
        this.levelInt = level.toInt();
    }


    public SqAppender getAppender()
    {
        return appender;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public void debug(String msg)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, msg);
        }
    }

    @Override
    public void debug(String format, Object arg)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, format, arg);
        }
    }

    @Override
    public void debug(String format, Object arg1, Object arg2)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, format, arg1, arg2);
        }
    }

    @Override
    public void debug(String format, Object... arguments)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, format, arguments);
        }
    }

    @Override
    public void debug(String msg, Throwable throwable)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, msg, throwable);
        }
    }

    @Override
    public void debug(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(msg, t);
    }

    @Override
    public boolean isDebugEnabled()
    {
        return levelIncludes(Level.DEBUG);
    }

    @Override
    public boolean isDebugEnabled(Marker marker)
    {
        return isDebugEnabled();
    }

    @Override
    public void error(String msg)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, msg);
        }
    }

    @Override
    public void error(String format, Object arg)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, format, arg);
        }
    }

    @Override
    public void error(String format, Object arg1, Object arg2)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, format, arg1, arg2);
        }
    }

    @Override
    public void error(String format, Object... arguments)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, format, arguments);
        }
    }

    @Override
    public void error(String msg, Throwable throwable)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, msg, throwable);
        }
    }

    @Override
    public void error(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(msg, t);
    }

    @Override
    public boolean isErrorEnabled()
    {
        return levelIncludes(Level.ERROR);
    }

    @Override
    public boolean isErrorEnabled(Marker marker)
    {
        return isErrorEnabled();
    }

    @Override
    public void info(String msg)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, msg);
        }
    }

    @Override
    public void info(String format, Object arg)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, format, arg);
        }
    }

    @Override
    public void info(String format, Object arg1, Object arg2)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, format, arg1, arg2);
        }
    }

    @Override
    public void info(String format, Object... arguments)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, format, arguments);
        }
    }

    @Override
    public void info(String msg, Throwable throwable)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, msg, throwable);
        }
    }

    @Override
    public void info(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(msg, t);
    }

    @Override
    public boolean isInfoEnabled()
    {
        return levelIncludes(Level.INFO);
    }

    @Override
    public boolean isInfoEnabled(Marker marker)
    {
        return isInfoEnabled();
    }

    @Override
    public void trace(String msg)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, msg);
        }
    }

    @Override
    public void trace(String format, Object arg)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, format, arg);
        }
    }

    @Override
    public void trace(String format, Object arg1, Object arg2)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, format, arg1, arg2);
        }
    }

    @Override
    public void trace(String format, Object... arguments)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, format, arguments);
        }
    }

    @Override
    public void trace(String msg, Throwable throwable)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, msg, throwable);
        }
    }

    @Override
    public void trace(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(msg, t);
    }

    @Override
    public boolean isTraceEnabled()
    {
        return levelIncludes(Level.TRACE);
    }

    @Override
    public boolean isTraceEnabled(Marker marker)
    {
        return isTraceEnabled();
    }

    @Override
    public void warn(String msg)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, msg);
        }
    }

    @Override
    public void warn(String format, Object arg)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, format, arg);
        }
    }

    @Override
    public void warn(String format, Object... arguments)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, format, arguments);
        }
    }

    @Override
    public void warn(String format, Object arg1, Object arg2)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, format, arg1, arg2);
        }
    }

    @Override
    public void warn(String msg, Throwable throwable)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, msg, throwable);
        }
    }

    @Override
    public void warn(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(msg, t);
    }

    @Override
    public boolean isWarnEnabled()
    {
        return levelIncludes(Level.WARN);
    }

    @Override
    public boolean isWarnEnabled(Marker marker)
    {
        return isWarnEnabled();
    }

    private void emit(Level level, String msg)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        getAppender().emit(this, level, timestamp, threadName, null, msg);
    }

    private void emit(Level level, String format, Object arg)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        if (arg instanceof Throwable)
            getAppender().emit(this, level, timestamp, threadName, (Throwable)arg, format);
        else
            getAppender().emit(this, level, timestamp, threadName, null, format, arg);
    }

    private void emit(Level level, String format, Object arg1, Object arg2)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        if (arg2 instanceof Throwable)
            getAppender().emit(this, level, timestamp, threadName, (Throwable)arg2, format, arg1);
        else
            getAppender().emit(this, level, timestamp, threadName, null, format, arg1, arg2);
    }

    private void emit(Level level, String format, Object... arguments)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        getAppender().emit(this, level, timestamp, threadName, null, format, arguments);
    }

    private void emit(Level level, String msg, Throwable throwable)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        getAppender().emit(this, level, timestamp, threadName, throwable, msg);
    }

    /*
     * Entry point for {@link LocationAwareLogger}
    @Override
    public void log(Marker marker, String fqcn, int levelInt, String message, Object[] argArray, Throwable throwable)
    {
        if (this.levelInt <= levelInt)
        {
            long timestamp = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            getAppender().emit(this, Level.intToLevel(levelInt).toLevel(), timestamp, threadName, throwable, message, argArray);
        }
    }


    @Override
    public String toString()
    {
        return String.format("%s:%s:LEVEL=%s", SqLogger.class.getSimpleName(), name, level.name());
    }
     */
}
