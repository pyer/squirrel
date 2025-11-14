package ab.squirrel.http;

/**
 * <p>Exception thrown to indicate a Bad HTTP Message has either been received
 * or attempted to be generated.  Typically these are handled with either 400
 * responses.</p>
 */
public class BadMessageException extends java.lang.RuntimeException
{
    public BadMessageException()
    {
        this(null, null);
    }

    public BadMessageException(String reason)
    {
        this(reason, null);
    }

    public BadMessageException(String reason, Throwable cause)
    {
        this(HttpStatus.BAD_REQUEST_400, reason, cause);
    }

    public BadMessageException(int code)
    {
        this(code, null, null);
    }

    public BadMessageException(int code, String reason)
    {
        this(code, reason, null);
    }

    public BadMessageException(int code, String reason, Throwable cause)
    {
        super(code + ": " + reason, cause);
        assert code >= 400 && code < 500;
    }

}
