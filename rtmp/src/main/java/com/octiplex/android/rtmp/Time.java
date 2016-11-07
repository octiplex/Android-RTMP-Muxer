package com.octiplex.android.rtmp;

/**
 * Interface that contains methods for an object that is responsible of giving the current time.
 * This could return the absolute time or a relative one, it's up to the implementation. This object
 * is responsible of keeping the time linear and should always return a valid time.
 *
 * @author Benoit LETONDOR
 */
public interface Time
{
    /**
     * Return the current timestamp that the muxer should use
     *
     * @return the current time
     */
    long getCurrentTimestamp();
}
