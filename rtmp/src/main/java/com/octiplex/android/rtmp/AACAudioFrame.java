package com.octiplex.android.rtmp;

import android.support.annotation.NonNull;

/**
 * Interface that defines an AAC audio frame, extracted from {@link android.media.MediaRecorder}.
 *
 * @author Benoit LETONDOR
 */
public interface AACAudioFrame
{
    /**
     * Return the timestamp of the frame in milliseconds, relative to the beginning of the stream
     * (first frame = 0)
     *
     * @return the timestamp in milliseconds.
     */
    long getTimestamp();

    /**
     * Return the AAC audio data.
     *
     * @return data of the stream
     */
    @NonNull
    byte[] getData();
}
