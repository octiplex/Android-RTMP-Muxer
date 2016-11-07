package com.octiplex.android.rtmp;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable object that contains data frame configuration values
 *
 * @author Benoit LETONDOR
 */
public final class RtmpDataFrame
{
    /**
     * Audio sample rate
     */
    private final int audioSamplerate;
    /**
     * Video framerate (in fps)
     */
    private final int videoFramerate;
    /**
     * Video width (in pixels)
     */
    private final int videoWidth;
    /**
     * Video height (in pixels)
     */
    private final int videoHeight;
    /**
     * Id of the video codec
     */
    private final int videoCodecId;
    /**
     * Id of the audio codec
     */
    private final int audioCodecId;

    /**
     * Creates a new data frame object
     *
     * @param audioSamplerate the audio sample rate
     * @param videoFramerate the video framerate in fps
     * @param videoWidth the video width in pixels
     * @param videoHeight the video height in pixels
     * @param videoCodecId id of the video codec
     * @param audioCodecId id of the audio codec
     */
    public RtmpDataFrame(int audioSamplerate, int videoFramerate, int videoWidth, int videoHeight, int videoCodecId, int audioCodecId)
    {
        this.audioSamplerate = audioSamplerate;
        this.videoFramerate = videoFramerate;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoCodecId = videoCodecId;
        this.audioCodecId = audioCodecId;
    }

    /**
     * Serialize values to a map
     *
     * @return a map containing values for setDataFrame call
     */
    @NonNull
    public Map<String, Object> serialize()
    {
        final Map<String, Object> map = new HashMap<>(6);

        map.put("width", videoWidth);
        map.put("height", videoHeight);
        map.put("framerate", videoFramerate);
        map.put("audiosamplerate", audioSamplerate);
        map.put("videocodecid", videoCodecId);
        map.put("audiocodecid", audioCodecId);

        return map;
    }
}
