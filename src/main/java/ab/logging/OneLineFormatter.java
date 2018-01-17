package ab.logging;

import java.io.StringWriter;
import java.io.PrintWriter;

import java.lang.StringBuilder;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class OneLineFormatter extends Formatter {

  private static String newLine = System.getProperty("line.separator");

  /**
   * Format the given LogRecord.
   * @param record the log record to be formatted.
   * @return a formatted log record
   */
  public synchronized String format(LogRecord record) {
    StringBuilder sb = new StringBuilder();

    // Date and time
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS ");
    sb.append(sdf.format(new Date(record.getMillis())));

    // Level
    //sb.append(record.getLevel().getLocalizedName());
    sb.append(record.getLevel().getName()+"   ");

    // Thread ID
    sb.append("["+record.getThreadID()+"] ");

    // Class name
    sb.append(" - ");
    if (record.getSourceClassName() != null) {
      sb.append(record.getSourceClassName());
    } else {
      sb.append(record.getLoggerName());
    }
    // Method name
    if (record.getSourceMethodName() != null) {
      sb.append(".");
      sb.append(record.getSourceMethodName());
    }
    // Indent
    for (int i = sb.length(); i < 78;  i++ ){
      sb.append(" ");
    }
    sb.append(" - ");

    // Message
    sb.append(record.getMessage());
    sb.append(newLine);
    if (record.getThrown() != null) {
      try {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        record.getThrown().printStackTrace(pw);
        pw.close();
        sb.append(sw.toString());
      } catch (Exception ex) {
      }
    }
    return sb.toString();
  }
}
