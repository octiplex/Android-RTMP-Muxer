package com.octiplex.android.rtmp.protocol;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Enumerate the different kinds of event type a User control message can contain.
 *
 * @author Benoit LETONDOR
 */
public enum RtmpUserControlEventType
{
    /**
     * The server sends this event to notify the client that a stream has become functional and
     * can be used for communication
     */
    STREAM_BEGIN(0),
    /**
     * The server sends this event to notify the client that the playback of data is over as
     * requested on this stream
     */
    STREAM_EOF(1),
    /**
     * The server sends this event to notify the client that there is no more data on the stream
     */
    STREAM_DRY(2),
    /**
     * The client sends this event to inform the server of the buffer size (in milliseconds) that is
     * used to buffer any data coming over a stream
     */
    SET_BUFFER_LENGTH(3),
    /**
     * The server sends this event to notify the client that the stream is a recorded stream
     */
    STREAM_IS_RECORDED(4),

    // Where is 5?!

    /**
     * The server sends this event to test whether the client is reachable
     */
    PING_REQUEST(6),
    /**
     * The client sends this event to the server in response to the ping request
     */
    PING_RESPONSE(7);

// ---------------------------------------->

    @NonNull
    private final static String TAG = "UserControlEventType";

    private final int value;

    RtmpUserControlEventType(int value)
    {
        this.value = value;
    }

    public int getValue()
    {
        return value;
    }

    /**
     * Find the {@link RtmpUserControlEventType} associated with this value.
     *
     * @param value the value
     * @return the enum object, or null
     */
    @Nullable
    public static RtmpUserControlEventType fromValue(int value)
    {
        for(RtmpUserControlEventType type : values())
        {
            if( type.value == value )
            {
                return type;
            }
        }

        Log.w(TAG, "Unable to find RtmpUserControlEventType for value: " + value);

        return null;
    }
}
