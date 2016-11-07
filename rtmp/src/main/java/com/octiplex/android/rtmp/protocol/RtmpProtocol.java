package com.octiplex.android.rtmp.protocol;

import android.support.annotation.NonNull;

/**
 * RTMP protocol implementation.
 * <p>
 * See <a href="https://www.adobe.com/content/dam/Adobe/en/devnet/rtmp/pdf/rtmp_specification_1.0.pdf">RCF</a>
 * for specifications.
 *
 * @author Benoit LETONDOR
 */
public final class RtmpProtocol
{
    /**
     * Chunk stream ID that must be used for control messages
     */
    public static final int CONTROL_MESSAGE_CHUNK_STREAM_ID = 2;
    /**
     * Stream ID that must be used for control messages
     */
    public static final int CONTROL_MESSAGE_STREAM_ID = 0;

// ----------------------------------------->

    private RtmpProtocol()
    {
        // Not instanciable
    }

// ----------------------------------------->

    /**
     * Generate a Type 0 header, filled with the given data.<p>
     * This header is 12 bytes long
     *
     * @param chunkStreamId chunk stream ID
     * @param timestamp timestamp
     * @param messageLength length of the message
     * @param messageType type of the message
     * @param streamId stream id
     * @return a buffer containing the header (12 bytes long)
     */
    @NonNull
    public static byte[] generateType0Header(int chunkStreamId, long timestamp, long messageLength, @NonNull RtmpMessageType messageType, long streamId)
    {
        byte[] buffer = new byte[12];

        addBasicHeader(buffer, 0, chunkStreamId);

        /*
         * Type 0 header
         *
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                  A. timestamp                 | B. msg length |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |       msg length (cont)       | C. msg type id|  D. stream id |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                 stream id (cont)              |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

         /*
         * A. Timestamp, on 3 bytes, big-endian
         */
        buffer[1] = (byte) ((int) ((timestamp >> 16) & 255));
        buffer[2] = (byte) ((int) ((timestamp >> 8) & 255));
        buffer[3] = (byte) ((int) (255 & timestamp));

        /*
         * B. Length, on 3 bytes, big-endian
         */
        buffer[4] = (byte) ((messageLength >> 16) & 255);
        buffer[5] = (byte) ((messageLength >> 8) & 255);
        buffer[6] = (byte) (messageLength & 255);

        /*
         * C. Message type, 1 byte
         */
        buffer[7] = (byte) messageType.value;

        /*
         * D. Stream id, little-endian
         */
        buffer[8] = (byte) (streamId & 255);
        buffer[9] = (byte) ((streamId >> 8) & 255);
        buffer[10] = (byte) ((streamId >> 16) & 255);
        buffer[11] = (byte) ((streamId >> 24) & 255);

        return buffer;
    }

    /**
     * Generate a Type 1 header, filled with the given data.<p>
     * This header is 8 bytes long
     *
     * @param chunkStreamId chunk stream ID
     * @param timestampDelta timestamp delta since last message
     * @param messageLength length of the message
     * @param messageType type of the message
     * @return a buffer containing the header (8 bytes long)
     */
    @NonNull
    public static byte[] generateType1Header(int chunkStreamId, long timestampDelta, long messageLength, @NonNull RtmpMessageType messageType)
    {
        byte[] buffer = new byte[8];

        addBasicHeader(buffer, 1, chunkStreamId);

        /*
         * Message Header type 1
         *
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |            A.timestamp delta                  |  B.msg length |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |      msg length (cont)    |   C.msg type id   |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

        /*
         * A. Delta between this frame and last one, on 3 bytes, big endian
         */
        buffer[1] = (byte) ((int) (255 & (timestampDelta >> 16)));
        buffer[2] = (byte) ((int) (255 & (timestampDelta >> 8)));
        buffer[3] = (byte) ((int) (255 & timestampDelta));

        /*
         * B. Length of the message, on 3 bytes, big endian
         */
        buffer[4] = (byte) ((messageLength >> 16) & 255);
        buffer[5] = (byte) ((messageLength >> 8) & 255);
        buffer[6] = (byte) (messageLength & 255);

        /*
         * C. Message type, on 1 byte
         */
        buffer[7] = (byte) messageType.value;

        return buffer;
    }

    /**
     * Generate a Type 2 header, filled with the given data.<p>
     * This header is 4 bytes long
     *
     * @param chunkStreamId chunk stream ID
     * @param timestampDelta timestamp delta since last message
     * @return a buffer containing the header (4 bytes long)
     */
    @NonNull
    public static byte[] generateType2Header(long timestampDelta, int chunkStreamId)
    {
        byte[] buffer = new byte[4];

        addBasicHeader(buffer, 2, chunkStreamId);

        /*
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |             A. timestamp delta                |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

        /*
         * A. Delta between this frame and last one, on 3 bytes, big endian
         */
        buffer[1] = (byte) ((int) (255 & (timestampDelta >> 16)));
        buffer[2] = (byte) ((int) (255 & (timestampDelta >> 8)));
        buffer[3] = (byte) ((int) (255 & timestampDelta));

        return buffer;
    }

    /**
     * Generate a Type 3 header, filled with the given data.<p>
     * This header is 1 bytes long
     *
     * @param chunkStreamId chunk stream ID
     * @return a buffer containing the header (4 bytes long)
     */
    @NonNull
    public static byte[] generateType3Header(int chunkStreamId)
    {
        byte[] buffer = new byte[1];

        addBasicHeader(buffer, 3, chunkStreamId);

        // Type 3 chunks have no message header.

        return buffer;
    }

    /**
     * Prepend the basic header including fmt and chunk stream id to the given buffer.
     *
     * @param buffer buffer to prepend header to
     * @param fmt type of header to use
     * @param chunkStreamId chunk stream id
     */
    private static void addBasicHeader(@NonNull byte[] buffer, int fmt, int chunkStreamId)
    {
        if( chunkStreamId < 2 || chunkStreamId > 63 ) //TODO handle larger values
        {
            throw new IllegalArgumentException("chunkStreamId must be >=2 && <= 63");
        }

        /*
         * Basic Header
         *
         *  0 1 2 3 4 5 6 7
         * +-+-+-+-+-+-+-+-+
         * |fmt|   cs id   |
         * +-+-+-+-+-+-+-+-+
         *
         * Chunk type (fmt) on 2 bytes, followed by the chunk stream id (from 2 to 63)
         */
        switch (fmt)
        {
            case 0:
                buffer[0] = (byte) (chunkStreamId);
                break;
            case 1:
                buffer[0] = (byte) (0b01000000 + chunkStreamId);
                break;
            case 2:
                buffer[0] = (byte) (0b10000000 + chunkStreamId);
                break;
            case 3:
                buffer[0] = (byte) (0b11000000 + chunkStreamId);
                break;
            default:
                throw new IllegalArgumentException("fmt type ["+fmt+"] is not yet implemented");
        }
    }
}
