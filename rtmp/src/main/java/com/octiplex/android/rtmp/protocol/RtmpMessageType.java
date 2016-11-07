package com.octiplex.android.rtmp.protocol;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Type of RTMP messages
 *
 * @author Benoit LETONDOR
 */
public enum RtmpMessageType
{
    /**
     * Set chunk size message
     */
    SET_CHUNK_SIZE(1),
    /**
     * Acknowledgement message
     */
    ACK(3),
    /**
     * User control message
     */
    USER_CONTROL_MESSAGE(4),
    /**
     * Window acknowledgement size
     */
    WINDOW_ACK_SIZE(5),
    /**
     * Peer bandwidth
     */
    PEER_BANDWIDTH(6),
    /**
     * Audio data message
     */
    AUDIO(8),
    /**
     * Video data message
     */
    VIDEO(9),
    /**
     * Meta-data message
     */
    AMF_0_META_DATA(18),
    /**
     * Command message
     */
    COMMAND(20);

// ------------------------------------------------>

    final int value;

    RtmpMessageType(int value)
    {
        this.value = value;
    }

// ------------------------------------------------>

    @NonNull
    private static final String TAG = "RtmpMessageType";

    /**
     * Find the {@link RtmpMessageType} associated with this value.
     *
     * @param value the value
     * @return the enum object, or null
     */
    @Nullable
    public static RtmpMessageType fromValue(int value)
    {
        for(RtmpMessageType type : values())
        {
            if( type.value == value )
            {
                return type;
            }
        }

        Log.w(TAG, "Unable to find MessageType for value: " + value);

        return null;
    }
}
