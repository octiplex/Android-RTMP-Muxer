package com.octiplex.android.rtmp;

import android.support.annotation.NonNull;

/**
 * Interface that defines an H264 video frame, extracted from {@link android.media.MediaCodec}.
 *
 * @author Benoit LETONDOR
 */
public interface H264VideoFrame
{
    /**
     * Is this frame a video header (config).<p>
     * This method should return true if the {@link android.media.MediaCodec} buffer contains the
     * {@link android.media.MediaCodec#BUFFER_FLAG_CODEC_CONFIG} flag.
     *
     * @return true if the frame is a config frame, false otherwise
     */
    boolean isHeader();

    /**
     * Returns the timestamp of the frame in microseconds, extracted from the {@link android.media.MediaCodec}
     * using the {@link android.media.MediaCodec.BufferInfo#presentationTimeUs} buffer data and made
     * relative to the beginning of the stream. (First frame = 0)
     *
     * @return the timestamp of the frame in milliseconds
     */
    long getTimestamp();

    /**
     * Return the data of the frame, extracted using the {@link android.media.MediaCodec#getOutputBuffers()}
     * method.
     *
     * @return bytes of data for the frame.
     */
    @NonNull
    byte[] getData();

    /**
     * Is this frame an H264 video keyframe.<p>
     * This method should return true if the {@link android.media.MediaCodec} buffer contains the
     * {@link android.media.MediaCodec#BUFFER_FLAG_KEY_FRAME} flag.
     *
     * @return true if the frame is a keyframe, false otherwise
     */
    boolean isKeyframe();
}
