package com.octiplex.android.rtmp.io;

import java.io.IOException;

/**
 * Exception thrown that indicates the server sent an error message.
 *
 * @author Benoit LETONDOR
 */
final class ServerException extends IOException
{
    public ServerException()
    {
        super();
    }

    public ServerException(String detailMessage)
    {
        super(detailMessage);
    }

    public ServerException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ServerException(Throwable cause)
    {
        super(cause);
    }
}
