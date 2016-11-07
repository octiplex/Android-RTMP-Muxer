package com.octiplex.android.rtmp;

import android.support.annotation.NonNull;

import java.io.IOException;

/**
 * Listener for connection events. This listener will be called asynchronously after calling
 * {@link RtmpMuxer#start(RtmpConnectionListener, String, String, String)}.
 * <p>
 * Note that this {@link #onConnectionError(IOException)} is only called when an error occurs while
 * reading data, when writing an {@link IOException} will be thrown.
 *
 * @author Benoit LETONDOR
 */
public interface RtmpConnectionListener
{
    /**
     * Called when the connection to the server is ok and handshake has been done. You should call
     * {@link RtmpMuxer#createStream(String)} to continue the process of publish.
     */
    void onConnected();

    /**
     * Called when the stream is created server side and the muxer is ready to receive video/audio
     * data.
     */
    void onReadyToPublish();

    /**
     * Called on error reading data from the server.
     *
     * @param e the error
     */
    void onConnectionError(@NonNull IOException e);
}
