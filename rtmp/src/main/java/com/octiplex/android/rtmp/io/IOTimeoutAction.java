package com.octiplex.android.rtmp.io;

import java.io.IOException;

/**
 * Helper to simply block a thread for a given period of time before timeout.
 *
 * @author Benoit LETONDOR
 */
abstract class IOTimeoutAction
{
    /**
     * The timeout (in ms)
     */
    private final long timeout;

// ------------------------------------>

    /**
     * Create a new action with the given timeout
     *
     * @param timeout timeout in ms (0 = infinite)
     */
    IOTimeoutAction(long timeout)
    {
        this.timeout = timeout;
    }

    /**
     * Block the current thread until {@link #condition()} returns true or we reach timeout
     *
     * @throws IOException if the thread is interrupted, condition throws or we reach the timeout
     */
    void execute() throws IOException
    {
        if( timeout > 0 )
        {
            long timeSpentWaiting = 0;
            long currentTS = System.currentTimeMillis();
            while( !condition() )
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    throw new IOException(e);
                }

                long newTS = System.currentTimeMillis();
                timeSpentWaiting += newTS - currentTS;
                currentTS = newTS;

                if( timeSpentWaiting >= timeout )
                {
                    throw new IOException("Timeout after "+timeSpentWaiting+" ms");
                }
            }
        }
        else
        {
            while( !condition() )
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    throw new IOException(e);
                }
            }
        }
    }

    /**
     * The condition we are waiting for. {@link #execute()} will block until this returns true.
     *
     * @return false to block the thread, true to continue
     * @throws IOException
     */
    public abstract boolean condition() throws IOException;
}
