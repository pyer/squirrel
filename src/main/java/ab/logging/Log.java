package ab.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Date;

/**
 * Logger with "standard" output and error output stream.
 */

public class Log
{
    private static boolean debug = false;

    private Date startDate;

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    public void debugOn()
    {
        debug = true;
    }

    public void debugOff()
    {
        debug = false;
    }

    public void debug( CharSequence content )
    {
      if(debug)
        print( " debug ", content );
    }

    public void debug( CharSequence content, Throwable error )
    {
      if(debug)
        print( " debug ", content, error );
    }

    public void debug( Throwable error )
    {
      if(debug)
        print( " debug ", error );
    }

    public void info( CharSequence content )
    {
        print( "INFO", content );
    }

    public void info( CharSequence content, Throwable error )
    {
        print( "INFO", content, error );
    }

    public void info( Throwable error )
    {
        print( "INFO", error );
    }

    public void warn( CharSequence content )
    {
        System.out.print( "\033[1;33m" );
        print( "WARN", content );
        System.out.print( "\033[1;37m" );
    }

    public void warn( CharSequence content, Throwable error )
    {
        System.out.print( "\033[1;33m" );
        print( "WARN", content, error );
        System.out.print( "\033[1;37m" );
    }

    public void warn( Throwable error )
    {
        System.out.print( "\033[1;33m" );
        print( "WARN", error );
        System.out.print( "\033[1;37m" );
    }

    public void error( CharSequence content )
    {
        System.err.print( "\033[1;31m" );
        print( " error ", content );
        System.err.print( "\033[1;37m" );
    }

    public void error( CharSequence content, Throwable error )
    {
        System.err.print( "\033[1;31m" );
        print( "ERROR", content, error );
        System.err.print( "\033[0;37m" );
    }

    public void error( Throwable error )
    {
        System.err.print( "\033[1;31m" );
        print( "ERROR", error );
        System.err.print( "\033[0;37m" );
    }

    // ----------------------------------------------------------------------
    // Private methods
    // ----------------------------------------------------------------------
    private void print( String prefix, CharSequence content )
    {
        System.out.println( formattedTime() + formattedPrefix(prefix) + formattedClassName() + content.toString() );
    }

    private void print( String prefix, CharSequence content, Throwable error )
    {
        StringWriter sWriter = new StringWriter();
        PrintWriter pWriter = new PrintWriter( sWriter );
        error.printStackTrace( pWriter );
        System.out.println( formattedTime() + formattedPrefix(prefix) + formattedClassName() + content.toString() + "\n\n" + sWriter.toString() );
    }

    private void print( String prefix, Throwable error )
    {
        StringWriter sWriter = new StringWriter();
        PrintWriter pWriter = new PrintWriter( sWriter );
        error.printStackTrace( pWriter );
        System.out.println( formattedTime() + formattedPrefix(prefix) + formattedClassName() + sWriter.toString() );
    }

    private String formattedPrefix(String prefix)
    {
        return " [ " + prefix + " ] ";
    }

    private String formattedTime()
    {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    private String formattedClassName()
    {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
/*
        for (StackTraceElement e : elements) {
            System.out.print( "--- " + e.getClassName());
        }
*/
        return elements[4].getClassName() + " - ";
    }
}
