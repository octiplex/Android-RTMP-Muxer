package com.octiplex.android.rtmp.protocol;

import android.support.annotation.NonNull;

/**
 * Object that wrap Amf0 value with the size of that value in the buffer
 *
 * @param <T> type of the value
 * @author Benoit LETONDOR
 */
public final class Amf0Value<T>
{
    /**
     * The actual value
     */
    @NonNull
    public final T value;
    /**
     * Length of the value in the buffer
     */
    public final int length;

// ----------------------------------------->

    /**
     * Create a new value
     *
     * @param value the actual value
     * @param length the length of this value in the buffer
     */
    Amf0Value(@NonNull T value, int length)
    {
        this.value = value;
        this.length = length;
    }
}
