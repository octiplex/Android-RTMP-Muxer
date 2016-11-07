package com.octiplex.android.rtmp.io;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class that wraps the output stream to the server by controlling the amount of data sent.
 * <p>
 * You <b>must</b> call {@link #stop()} after using it to release the thread.
 *
 * @author Benoit LETONDOR
 */
public final class RtmpWriter
{
    /**
     * Number of bytes we can send before waiting for an ACK from the server
     */
    private static final long DEFAULT_ACK_WINDOW_SIZE = 5000000;

// ----------------------------------------->

    /**
     * Used ack window size
     */
    private long ackWindowSize = DEFAULT_ACK_WINDOW_SIZE;
    /**
     * Number of bytes sent since last ACK
     */
    private long dataSentSinceLastAck = 0;
    /**
     * Number of bytes sent
     */
    private long totalDataSent = 0;
    /**
     * Output stream to send data to the server
     */
    @NonNull
    private final TimeoutSocket socket;
    /**
     * Is the writer currently writing
     */
    @NonNull
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * Timeout for write operations (in ms)
     */
    private int writeTimeout;
    /**
     * Timeout for waiting for ack (in ms)
     */
    private int ackWaitTimeout;

// ---------------------------------------->

    public RtmpWriter(@NonNull TimeoutSocket socket, int writeTimeout, int ackWaitTimeout)
    {
        this.socket = socket;
        this.writeTimeout = writeTimeout;
        this.ackWaitTimeout = ackWaitTimeout;
    }

    /**
     * Finish the sender
     */
    public void stop()
    {
        // no-op at this time
    }

// ---------------------------------------->

    /**
     * Set the number of bytes we can send before waiting for a server ACK
     *
     * @param size window size sent by the server
     */
    public void setAckWindow(long size)
    {
        ackWindowSize = size;
    }

    /**
     * Get the currently used ack window size
     *
     * @return the currently used ack window size
     */
    public long getAckWindowSize()
    {
        return ackWindowSize;
    }

    /**
     * Set the write timeout (in ms).
     *
     * @param writeTimeout timeout for write operations in ms
     */
    public void setWriteTimeout(int writeTimeout)
    {
        this.writeTimeout = writeTimeout;
    }

    /**
     * Set the ack wait timeout (in ms).
     *
     * @param ackWaitTimeout timeout for ack waiting (in ms)
     */
    public void setAckWaitTimeout(int ackWaitTimeout)
    {
        this.ackWaitTimeout = ackWaitTimeout;
    }

// ---------------------------------------->

    /**
     * Should be called when we receive an ACK from the server
     *
     * @param bytesReceived number of bytes the server saw
     */
    public void onAck(long bytesReceived)
    {
        Log.d("RtmpWriter", "Ack received from server after "+dataSentSinceLastAck+" bytes. We sent "+totalDataSent+" bytes, the server saw "+bytesReceived+" bytes");

        dataSentSinceLastAck = 0;
    }

    /**
     * Send the given bytes to the server.
     * This command may block until ACK is received (if needed).
     *
     * @param data the data to send
     * @param forceSend shall we bypass the ACK waiting
     * @throws IOException if connection fail or if ACK is needed and timeout
     */
    @WorkerThread
    public void send(@NonNull final byte[] data, boolean forceSend) throws IOException
    {
        waitForAckIfNeeded(forceSend);

        totalDataSent += data.length;
        dataSentSinceLastAck += data.length;

        sendWithTimeout(data);
    }

    /**
     * Send the given bytes to the server.
     * This command may block until ACK is received.
     *
     * @param data the data to send
     * @throws IOException if connection fail or if ACK is needed and timeout
     */
    @WorkerThread
    public void send(@NonNull byte[] data) throws IOException
    {
        send(data, false);
    }

    /**
     * Send bytes contained in this buffer (from 0 to {@link ByteBuffer#limit()}).
     * This command may block until ACK is received (if needed).
     *
     * @param buffer buffer containing the data to send
     * @param forceSend shall we bypass the ACK waiting
     * @throws IOException if connection fail or if ACK is needed and timeout
     */
    @WorkerThread
    public void send(@NonNull ByteBuffer buffer, boolean forceSend) throws IOException
    {
        waitForAckIfNeeded(forceSend);

        totalDataSent += buffer.limit();
        dataSentSinceLastAck += buffer.limit();

        sendWithTimeout(buffer);
    }

    /**
     * Send bytes contained in this buffer (from 0 to {@link ByteBuffer#limit()}).
     * This command may block until ACK is received (if needed).
     *
     * @param buffer buffer containing the data to send
     * @throws IOException if connection fail or if ACK is needed and timeout
     */
    @WorkerThread
    public void send(@NonNull ByteBuffer buffer) throws IOException
    {
        send(buffer, false);
    }

    /**
     * Method that will block the thread waiting for ACK. Will throw if timeout is reach
     *
     * @param forceSend shall we wait of not
     * @throws IOException if timeout is reach
     */
    @WorkerThread
    private void waitForAckIfNeeded(boolean forceSend) throws IOException
    {
        if( !forceSend )
        {
            /*
             * If we sent too much data, wait for ACK from server.
             * We use a 1.2 multiplier just to allow a bit of latency without cutting the stream.
             */
            if( dataSentSinceLastAck >= ackWindowSize*1.2d )
            {
                Log.d("RtmpWriter", "Waiting for ACK...");

                new IOTimeoutAction(ackWaitTimeout)
                {
                    @Override
                    public boolean condition() throws IOException
                    {
                        return dataSentSinceLastAck >= ackWindowSize;
                    }

                }.execute();
            }
        }
    }

    /**
     * Try to send the given data synchronously and throws {@link IOException} if the timeout is reached.
     *
     * @param data the data to send
     * @throws IOException on error or on timeout
     */
    @WorkerThread
    private void sendWithTimeout(@NonNull final byte[] data) throws IOException
    {
        if( !running.compareAndSet(false, true) )
        {
            throw new IOException("send called while already sending");
        }

        try
        {
            if( writeTimeout > 0 )
            {
                socket.write(data, writeTimeout, TimeUnit.MILLISECONDS);
            }
            else
            {
                socket.write(data, 60, TimeUnit.SECONDS); // 1 min max
            }
        }
        catch (Exception e)
        {
            if (e instanceof IOException) { throw (IOException) e; } else { throw new IOException(e); }
        }
        finally
        {
            running.set(false);
        }
    }

    /**
     * Try to send the given data synchronously and throws {@link IOException} if the timeout is reached.
     *
     * @param data the data to send
     * @throws IOException on error or on timeout
     */
    @WorkerThread
    private void sendWithTimeout(@NonNull final ByteBuffer data) throws IOException
    {
        if( !running.compareAndSet(false, true) )
        {
            throw new IOException("send called while already sending");
        }

        try
        {
            if( writeTimeout > 0 )
            {
                socket.write(data.array(), data.arrayOffset(), data.limit(), writeTimeout, TimeUnit.MILLISECONDS);
            }
            else
            {
                socket.write(data.array(), data.arrayOffset(), data.limit(), 60, TimeUnit.SECONDS); // 1 min max
            }
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
        finally
        {
            running.set(false);
        }
    }
}
