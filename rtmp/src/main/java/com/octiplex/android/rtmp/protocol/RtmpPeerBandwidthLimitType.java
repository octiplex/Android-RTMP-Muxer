package com.octiplex.android.rtmp.protocol;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Type of peer bandwidth limit.
 *
 * @author Benoit LETONDOR
 */
public enum RtmpPeerBandwidthLimitType
{
    /**
     * The peer SHOULD limit its output bandwidth to the indicated
     * window size.
     */
    HARD(0),
    /**
     * The peer SHOULD limit its output bandwidth to the the
     * window indicated in this message or the limit already in effect,
     * whichever is smaller.
     */
    SOFT(1),
    /**
     * If the previous Limit Type was Hard, treat this message
     * as though it was marked Hard, otherwise ignore this message.
     */
    DYNAMIC(2);

// ------------------------------------------>

    final int value;

    RtmpPeerBandwidthLimitType(int value)
    {
        this.value = value;
    }

// ------------------------------------------>

    @NonNull
    private final static String TAG = "PeerBandwidthLimitType";

    /**
     * Find the {@link RtmpPeerBandwidthLimitType} associated with this value.
     *
     * @param value the value
     * @return the enum object, or null
     */
    @Nullable
    public static RtmpPeerBandwidthLimitType fromValue(int value)
    {
        for(RtmpPeerBandwidthLimitType type : values())
        {
            if( type.value == value )
            {
                return type;
            }
        }

        Log.w(TAG, "Unable to find PeerBandwidthLimitType for value: " + value);

        return null;
    }
}
