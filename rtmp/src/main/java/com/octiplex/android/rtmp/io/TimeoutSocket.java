package com.octiplex.android.rtmp.io;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Socket that handles writing to the OutputStream with a timeout.
 *
 * @author Nicolas DOUILLET
 * @author Benoit LETONDOR
 */
public final class TimeoutSocket extends Socket implements Runnable
{
    /**
     * Lock used for reading data
     */
    @NonNull
    private final Lock lock = new ReentrantLock();
    /**
     * Lock used for writing data
     */
    @NonNull
    private final Lock lock2 = new ReentrantLock();
    /**
     * Condition for data input
     */
    @NonNull
    private final Condition input = lock.newCondition();
    /**
     * Condition for data output (writing)
     */
    @NonNull
    private final Condition output = lock2.newCondition();
    /**
     * Thread used to send data
     */
    @NonNull
    private final Thread thread = new Thread(this);
    /**
     * Is the socket ready to write data
     */
    @NonNull
    private final AtomicBoolean ready = new AtomicBoolean(true);

    /**
     * Data to write
     */
    @Nullable
    private byte[] data = null;
    /**
     * Offset to read data
     */
    private int offset = -1;
    /**
     * Limit to read data
     */
    private int limit = -1;
    /**
     * Last exception we got
     */
    @Nullable
    private IOException lastError;

// ----------------------------------->

    @Override
    public void connect(SocketAddress remoteAddr, int timeout) throws IOException
    {
        super.connect(remoteAddr, timeout);

        thread.start();
    }

    /**
     * Write to the socket with a timeout
     *
     * @param b bytes to send
     * @param timeout timeout value
     * @param unit timeout unit
     * @throws InterruptedException
     * @throws IOException
     */
    public void write(@NonNull byte[] b, long timeout, @NonNull TimeUnit unit) throws InterruptedException, IOException
    {
        write(b, 0, b.length, timeout, unit);
    }

    /**
     * Write to the socket with a timeout
     *
     * @param b array containing bytes to send
     * @param offset offset to read into the array
     * @param limit limit to read into the array
     * @param timeout timeout value
     * @param unit timeout unit
     * @throws InterruptedException
     * @throws IOException
     */
    public void write(@NonNull byte[] b, int offset, int limit, long timeout, @NonNull TimeUnit unit) throws InterruptedException, IOException
    {
        final long t = System.nanoTime();
        long time = timeout;

        if ( !lock.tryLock(time, unit) )
        {
            throw new IOException("timeout 1, wait time (" + unit.convert(System.nanoTime() - t, TimeUnit.NANOSECONDS) + " " + unit + ") exceeded " + timeout + " " + unit);
        }

        try
        {
            if ( isClosed() )
            {
                throw new IOException("Socket closed");
            }

            if ( !ready.compareAndSet(true, false) )
            {
                throw new IllegalStateException("Socket is already busy writing");
            }

            this.data = b;
            this.offset = offset;
            this.limit = limit;

            input.signalAll();

            time = timeout - unit.convert(System.nanoTime() - t, TimeUnit.NANOSECONDS);
            if ( time <= 0 )
            {
                throw new IOException("timeout 2, wait time (" + unit.convert(System.nanoTime() - t, TimeUnit.NANOSECONDS) + " " + unit + ") exceeded " + timeout + " " + unit);
            }

            if ( !lock2.tryLock(time, unit) )
            {
                throw new IOException("timeout 3, wait time (" + unit.convert(System.nanoTime() - t, TimeUnit.NANOSECONDS) + " " + unit + ") exceeded " + timeout + " " + unit);
            }
        }
        finally
        {
            lock.unlock();
        }

        try
        {
            time = timeout - unit.convert(System.nanoTime() - t, TimeUnit.NANOSECONDS);
            if ( time <= 0 )
            {
                throw new IOException("timeout 4, wait time (" + unit.convert(System.nanoTime() - t, TimeUnit.NANOSECONDS) + " " + unit + ") exceeded " + timeout + " " + unit);
            }

            if ( !output.await(time, unit) && !ready.get() )
            {
                throw new IOException("timeout 5, wait time (" + unit.convert(System.nanoTime() - t, TimeUnit.NANOSECONDS) + " " + unit + ") exceeded " + timeout + " " + unit);
            }

            if( lastError != null )
            {
                IOException e = lastError;
                lastError = null;
                throw e;
            }
        }
        finally
        {
            lock2.unlock();
        }
    }

    @Override
    public synchronized void close() throws IOException
    {
        thread.interrupt();
        super.close();
    }

// ----------------------------------->

    @Override
    public void run()
    {
        lock.lock();
        try
        {
            while ( !Thread.interrupted() && !isClosed() )
            {
                try
                {
                    if ( ready.get() )
                    {
                        // just wait
                        input.await();
                        continue;
                    }

                    if( data != null )
                    {
                        getOutputStream().write(data, offset, limit);
                    }

                    if ( ready.compareAndSet(false, true) )
                    {
                        lock2.lock();
                        try
                        {
                            output.signalAll();
                        }
                        finally
                        {
                            lock2.unlock();
                        }
                    }
                }
                catch(InterruptedException e)
                {
                    break;
                }
                catch(IOException e)
                {
                    lastError = e;
                    ready.set(true);
                }
            }
        }
        finally
        {
            lock.unlock();
        }
    }
}
