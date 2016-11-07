package com.octiplex.android.rtmp;

import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.octiplex.android.rtmp.io.TimeoutSocket;
import com.octiplex.android.rtmp.protocol.Amf0Functions;
import com.octiplex.android.rtmp.protocol.RtmpMessageType;
import com.octiplex.android.rtmp.protocol.RtmpPeerBandwidthLimitType;
import com.octiplex.android.rtmp.protocol.RtmpProtocol;
import com.octiplex.android.rtmp.io.RtmpReader;
import com.octiplex.android.rtmp.io.RtmpWriter;
import com.octiplex.android.rtmp.protocol.RtmpUserControlEventType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.IllegalSelectorException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RTMP muxer for H264Video and AAC audio. To use it:
 * <ul>
 *     <li>1. Instanciate it: {@link #RtmpMuxer(String, int, Time)}</li>
 *     <li>2. Connect to the server using {@link #start(RtmpConnectionListener, String, String, String)}</li>
 *     <li>3. Start streaming using {@link #createStream(String)}</li>
 *     <li>4. Send Audio headers using {@link #setAudioHeader(AACAudioHeader)}</li>
 *     <li>5. Send Audio data using {@link #postAudio(AACAudioFrame)}</li>
 *     <li>6. Send video data using {@link #postVideo(H264VideoFrame)} method</li>
 *     <li>7. Stop streaming using {@link #deleteStream()}</li>
 *     <li>7. Close connection using {@link #stop()}</li>
 * </ul>
 * <b>Warning: </b>Most methods of this class need to be called from a worker thread. It will throw
 * a {@link NetworkOnMainThreadException} if called from the UI thread.
 *
 * @author Benoit LETONDOR
 */
public final class RtmpMuxer implements RtmpReader.RtmpReaderListener
{
    /**
     * Default timeout used for connection. (in ms)
     */
    private final static int DEFAULT_CONNECT_TIMEOUT = 5000;
    /**
     * Default timeout used for handshake read. (in ms)
     */
    private final static int DEFAULT_HANDSHAKE_READ_TIMEOUT = 2500;
    /**
     * Default timeout used for write. (in ms)
     */
    private final static int DEFAULT_WRITE_TIMEOUT = 10000;
    /**
     * Default timeout used for ack wait. (in ms)
     */
    private final static int DEFAULT_ACK_WAIT_TIMEOUT = 5000;

    /**
     * Chunk stream id used for video
     */
    private static final int VIDEO_CHUNK_STREAM_ID = 9;
    /**
     * Chunk stream id used for audio
     */
    private static final int AUDIO_CHUNK_STREAM_ID = 8;
    /**
     * Chunk stream id used for meta
     */
    private static final int META_DATA_STREAM_ID = 18;

    private static final String TAG = "RtmpMuxer";

    //region Audio data members
    /**
     * Has audio headers been send already
     */
    private boolean audioHeaderSent = false;
    /**
     * AAC audio header format
     */
    private int aacFormat;
    /**
     * AAC header
     */
    @Nullable
    private AACAudioHeader audioHeader;
    /**
     * Last audio frame sent timestamp. -1 == nothing has been sent yet
     */
    private long lastAudioTs = -1;
    //endregion

    //region Video data member
    /**
     * Last video frame sent timestamp. -1 == nothing has been sent yet
     */
    private long lastVideoTs = -1;
    //endregion

    //region Connection data members
    /**
     * Host of the server
     */
    @NonNull
    private final String host;
    /**
     * Port used to connect to the server
     */
    private final int port;
    /**
     * Path to publish data to.
     */
    private String playpath;
    //endregion

    //region Connection members
    /**
     * Socket used to connect to the server
     * (Used to determine if the muxer is started or not (!= null))
     */
    private TimeoutSocket socket;
    /**
     * Reader of data from the server
     */
    private RtmpReader reader;
    /**
     * Writer of data to the server
     */
    private RtmpWriter writer;
    /**
     * Listener for connection events with the server
     */
    private RtmpConnectionListener listener;
    /**
     * Chunk size used to send data to the server. Initialized to 4096, can be changed by the server
     * calling {@link RtmpReader.RtmpReaderListener#onSetChunkSize(long)}.
     */
    private int chunkSize = 4096;
    /**
     * Stream ID
     */
    private int streamId = 0;
    /**
     * Type of bandwidth limit set by the server (will be null if nothing has been sent before)
     */
    @Nullable
    private RtmpPeerBandwidthLimitType peerBandwidthLimitType;
    /**
     * Boolean that store if an ACK should be sent before the next data
     */
    @NonNull
    private final AtomicBoolean shouldSendACK = new AtomicBoolean(false);
    /**
     * Bytes we read, to send to the server (should be read if {@link #shouldSendACK} is true)
     */
    private long bytesReadForAck;
    /**
     * Boolean that store if a ping response should be sent before the next data
     */
    private final AtomicBoolean shouldSendPingResponse = new AtomicBoolean(false);
    /**
     * Timestamp sent by the server with the last ping request (should be read if {@link #shouldSendPingResponse}
     * is true
     */
    private long timestampForPing;
    /**
     * Is the muxer currently connected and ready to publish data
     */
    private boolean streaming = false;
    /**
     * Is the muxer currently connected
     */
    private boolean connected = false;
    /**
     * Buffer used to send chunks of video data without reallocating a buffer each time
     */
    @Nullable
    private ByteBuffer videoChunkBuffer;
    /**
     * Buffer used to send chunks of audio data without reallocating a buffer each time
     */
    @Nullable
    private ByteBuffer audioChunkBuffer;
    //endregion

    //region Timeouts
    /**
     * Timeout used for connection
     */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    /**
     * Timeout used for handshake read
     */
    private int handshakeTimeout = DEFAULT_HANDSHAKE_READ_TIMEOUT;
    /**
     * Timeout used for write
     */
    private int writeTimeout = DEFAULT_WRITE_TIMEOUT;
    /**
     * Timeout use for ack waiting
     */
    private int ackWaitTimeout = DEFAULT_ACK_WAIT_TIMEOUT;
    //endregion
    /**
     * Time used by the muxer
     */
    @NonNull
    private final Time time;

// ---------------------------------------->

    /**
     * Creates a new Muxer with the following parameters.
     *
     * @param host host of the RTMP server
     * @param port port to connect the the RTMP server
     * @param time an implementation of time that will be used for timestamping
     */
    public RtmpMuxer(@NonNull String host, int port, @NonNull Time time)
    {
        this.host = host;
        this.port = port;
        this.time = time;
    }

    //region Rtmp calls

    /**
     * Handshake with the RTMP server
     *
     * @throws IOException
     */
    @WorkerThread
    private void handshake() throws IOException
    {
        long timestamp = System.currentTimeMillis();

        byte[] buffer = new byte[1537];

        /*
         * C0 bit: RTMP version
         *
         * We want RTMP version 3
         */
        buffer[0] = (byte) 3;

        /*
         * C1: 1536 bytes long
         *
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                        A. time (4 bytes)                      |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                        zero (4 bytes)                         |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                         random bytes                          |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                          random bytes                         |
         * |                            (cont)                             |
         * |                             ....                              |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

        /*
         * C1 A. time
         */
        long currentTime = time.getCurrentTimestamp();
        buffer[1] = (byte) ((currentTime >> 8) & 255);
        buffer[2] = (byte) ((currentTime >> 16) & 255);
        buffer[3] = (byte) ((currentTime >> 8) & 255);
        buffer[4] = (byte) (currentTime & 255);

        writer.send(buffer); // Send C0 & C1

        /*
         * Read S0
         */
        byte s0 = reader.readHandshakeS0();
        if( s0 != 3 )
        {
            throw new IOException("Server is not RTMP 3, found version: "+s0);
        }

        /*
         * Read S1
         */
        byte[] s1 = reader.readHandshakeS1();

        /*
         * Build C2 out of S1
         *
         * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                       time echo (4 bytes)                     |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                       time2 (4 bytes)                         |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                        random echo                            |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                        random echo                            |
         * |                           (cont)                              |
         * |                            ....                               |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        long delta = System.currentTimeMillis() - timestamp;
        s1[4] = (byte) ((delta >> 24) & 255);
        s1[5] = (byte) ((delta >> 16) & 255);
        s1[6] = (byte) ((delta >> 8) & 255);
        s1[7] = (byte) (delta & 255);

        writer.send(s1);

        /*
         * Read S2
         */
        byte[] s2 = reader.readHandshakeS2();

        // TODO validate S2

        reader.start();
    }

    /**
     * Set the chunk size to the server
     *
     * @throws IOException
     */
    @WorkerThread
    private void setChunkSize() throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(16); // 16 = 12 for type 0 header and 4 for data

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), 4, RtmpMessageType.SET_CHUNK_SIZE, RtmpProtocol.CONTROL_MESSAGE_STREAM_ID));

        buffer.put((byte) 0); // 0 because Set chunk size must start with 0 FIXME actually only the first bit must, not the byte!
        buffer.put((byte) ((chunkSize >> 16) & 255));
        buffer.put((byte) ((chunkSize >> 8) & 255));
        buffer.put((byte) (chunkSize & 255));

        writer.send(buffer.array());
    }

    /**
     * Send an ACK to the server
     *
     * @param bytesReceived the number of bytes we received
     * @throws IOException
     */
    @WorkerThread
    private void sendAck(long bytesReceived) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(16); // 16 = 12 for type 0 header and 4 for data

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), 4, RtmpMessageType.ACK, RtmpProtocol.CONTROL_MESSAGE_STREAM_ID));

        buffer.put((byte) ((int) ((bytesReceived >> 24) & 255)));
        buffer.put((byte) ((int) ((bytesReceived >> 16) & 255)));
        buffer.put((byte) ((int) ((bytesReceived >> 8) & 255)));
        buffer.put((byte) ((int) (bytesReceived & 255)));

        writer.send(buffer.array());
    }

    /**
     * Send ping response to the server
     *
     * @param timestamp timestamp sent by the server on the ping request
     * @throws IOException
     */
    @WorkerThread
    private void sendPingResponse(long timestamp) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(18); // 18 = 12 for type 0 header and 6 for data

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), 6, RtmpMessageType.USER_CONTROL_MESSAGE, RtmpProtocol.CONTROL_MESSAGE_STREAM_ID));

        buffer.put((byte) ((RtmpUserControlEventType.PING_RESPONSE.getValue() >> 8) & 255));
        buffer.put((byte) (RtmpUserControlEventType.PING_RESPONSE.getValue() & 255));

        buffer.put((byte) ((int) ((timestamp >> 24) & 255)));
        buffer.put((byte) ((int) ((timestamp >> 16) & 255)));
        buffer.put((byte) ((int) ((timestamp >> 8) & 255)));
        buffer.put((byte) ((int) (timestamp & 255)));

        writer.send(buffer.array());
    }

    /**
     * Send the expected ack window to the server
     *
     * @param windowAckSize number of bytes we can send before expecting an ACK
     * @throws IOException
     */
    @WorkerThread
    private void sendWindowAckSize(long windowAckSize) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(16); // 16 = 12 for type 0 header + 4 for data

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), 4, RtmpMessageType.WINDOW_ACK_SIZE, RtmpProtocol.CONTROL_MESSAGE_STREAM_ID));

        buffer.put((byte) ((int) ((windowAckSize >> 24) & 255)));
        buffer.put((byte) ((int) ((windowAckSize >> 16) & 255)));
        buffer.put((byte) ((int) ((windowAckSize >> 8) & 255)));
        buffer.put((byte) ((int) (windowAckSize & 255)));

        writer.send(buffer.array());
    }

    @WorkerThread
    private void connect(@NonNull String app, @Nullable String serverUrl, @Nullable String pageUrl) throws IOException
    {
        byte[] connectFunction = Amf0Functions.connect(app, serverUrl, pageUrl);

        ByteBuffer buffer = ByteBuffer.allocate(connectFunction.length+12);

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), connectFunction.length, RtmpMessageType.COMMAND, RtmpProtocol.CONTROL_MESSAGE_STREAM_ID));

        // Add connect AMF function
        buffer.put(connectFunction);

        writer.send(buffer.array());
    }

    /**
     * Send the publish command to server, starting the stream
     *
     * @throws IOException
     */
    private void publish() throws IOException
    {
        byte[] publishCommand = Amf0Functions.publish(playpath);

        ByteBuffer buffer = ByteBuffer.allocate(publishCommand.length+12);

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), publishCommand.length, RtmpMessageType.COMMAND, streamId));

        // Add publish AMF function
        buffer.put(publishCommand);

        writer.send(buffer.array());
    }


    /**
     * Send video headers, with a type 0 RTMP header
     *
     * @param headerData containing header data.
     * @throws IOException
     */
    @WorkerThread
    private void sendHeader(@NonNull byte[] headerData) throws IOException
    {
                /*
         * Extract pps & sps values
         */
        ByteBuffer spsPpsBuffer = ByteBuffer.wrap(headerData);
        if (spsPpsBuffer.getInt() == 0x00000001)
        {
            Log.d(TAG, "parsing sps/pps");
        }
        else
        {
            Log.e(TAG, "something is amiss?");
        }

        // Search for sps & pps
        try
        {
            while(!(spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x01)) {

            }
        }
        catch (Exception e)
        {
            throw new IOException("Unable to find SPS data");
        }

        // Assign values
        int ppsIndex  = spsPpsBuffer.position();
        final byte[] sps = new byte[ppsIndex - 8];
        System.arraycopy(headerData, 4, sps, 0, sps.length);
        final byte[] pps = new byte[headerData.length - ppsIndex];
        System.arraycopy(headerData, ppsIndex, pps, 0, pps.length);

        ByteBuffer buffer = ByteBuffer.allocate(sps.length+pps.length+28); // 28 = 12 for type 0 header + 16 bytes of FLV container

        buffer.put(RtmpProtocol.generateType0Header(VIDEO_CHUNK_STREAM_ID, time.getCurrentTimestamp(), sps.length + 16 + pps.length, RtmpMessageType.VIDEO, streamId));

        /*
         * Type of frame
         *
         * 23 = Video prefix frame
         */
        buffer.put((byte) 23);
        buffer.put((byte) 0);

        // Followed by 3 "0"
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);

        /* 5bytes sps/pps header:
         *      configurationVersion, AVCProfileIndication, profile_compatibility,
         *      AVCLevelIndication, lengthSizeMinusOne
         * 3bytes size of sps:
         *      numOfSequenceParameterSets, sequenceParameterSetLength(2B)
         * Nbytes of sps.
         *      sequenceParameterSetNALUnit
         * 3bytes size of pps:
         *      numOfPictureParameterSets, pictureParameterSetLength
         * Nbytes of pps:
         *      pictureParameterSetNALUnit
         */

        byte profile_idc = sps[1];
        byte level_idc = sps[3];

        buffer.put((byte) 1); // configurationVersion
        buffer.put(profile_idc); // AVCProfileIndication
        buffer.put((byte) 0); // profile_compatibility
        buffer.put(level_idc); // AVCLevelIndication
        buffer.put((byte) 3); // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size, so we always set it to 3.
        buffer.put((byte) 1); // numOfSequenceParameterSets, always 1

        /*
         * Sps length, on 2 bytes
         */
        buffer.put((byte) ((sps.length >> 8) & 255));
        buffer.put((byte) (sps.length & 255));

        buffer.put(sps);  // SPS data

        buffer.put((byte) 1) ; // numOfPictureParameterSets, always 1

        /*
         * Pps length, on 2 bytes
         */
        buffer.put((byte) ((pps.length >> 8) & 255));
        buffer.put((byte) (pps.length & 255));

        buffer.put(pps); // PPS data

        Log.d(TAG, "Starting video");

        // Send data
        writer.send(buffer.array());
    }

    /**
     * Send a video frame, with a type 1 RTMP header
     *
     * @param data H264 video frame data
     * @throws IOException
     */
    @WorkerThread
    private synchronized void sendVideoData(@NonNull H264VideoFrame data) throws IOException
    {
        if ( data.isHeader() )
        {
            sendHeader(data.getData());
            return;
        }

        sendAckIfNeeded();
        sendPingResponseIfNeeded();

        if( lastVideoTs == -1 )
        {
            lastVideoTs = data.getTimestamp();
        }

        long delta = data.getTimestamp() - lastVideoTs;
        lastVideoTs = data.getTimestamp();

        int dataLength = data.getData().length;
        int length = chunkSize - 9;

        if (length > dataLength)
        {
            length = dataLength;
        }

        int bufferLength = length+17; // 17 = 9 bytes for FLV data + 8 bytes for type 1 header

        /*
         * Send first chunk
         */
        final ByteBuffer buffer;
        if( videoChunkBuffer != null  ) // Try to use the cached buffer
        {
            videoChunkBuffer.clear().limit(bufferLength);
            buffer = videoChunkBuffer;
        }
        else
        {
            Log.w(TAG, "Using a non cached buffer for first video chunk");
            buffer = ByteBuffer.allocate(bufferLength);
        }

        // RTMP Header
        buffer.put(RtmpProtocol.generateType1Header(VIDEO_CHUNK_STREAM_ID, delta, data.getData().length + 9, RtmpMessageType.VIDEO));

        /*
         * Type of frame
         *
         * 2 bytes : [type][1]
         */
        if ( data.isKeyframe() )
        {
            buffer.put((byte) 23);
        }
        else
        {
            buffer.put((byte) 39);
        }
        buffer.put((byte) 1);

        // Followed by 3 "0"
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) 0);

        // Full frame size (without chunking)
        buffer.put((byte) ((data.getData().length >> 24) & 255));
        buffer.put((byte) ((data.getData().length >> 16) & 255));
        buffer.put((byte) ((data.getData().length >> 8) & 255));
        buffer.put((byte) (data.getData().length & 255));

        // First chunk data
        buffer.put(data.getData(), 0, length);

        writer.send(buffer);

        // write additional chunks
        int offset = length;
        while (offset < dataLength)
        {
            length = chunkSize;
            if (offset + length > dataLength)
            {
                length = dataLength - offset;
            }

            int chunkBufferLength = length+1; // Data + type 3 header

            final ByteBuffer chunkBuffer;
            if( videoChunkBuffer != null ) // Try to use the cached buffer
            {
                videoChunkBuffer.clear().limit(chunkBufferLength);
                chunkBuffer = videoChunkBuffer;
            }
            else
            {
                Log.w(TAG, "Using a non cached buffer for video sub chunk");
                chunkBuffer = ByteBuffer.allocate(chunkBufferLength);
            }

            // Write chunk header
            chunkBuffer.put(RtmpProtocol.generateType3Header(VIDEO_CHUNK_STREAM_ID));

            // Write chunk data
            chunkBuffer.put(data.getData(), offset, length);

            writer.send(chunkBuffer, true); // We bypass ACK waiting cause we're in the middle of a frame package

            offset += length;
        }
    }

    /**
     * Send the ACK to the server if needed
     *
     * @throws IOException on error
     */
    @WorkerThread
    private void sendAckIfNeeded() throws IOException
    {
        if ( shouldSendACK.compareAndSet(true, false) )
        {
            sendAck(bytesReadForAck);
        }
    }

    /**
     * Send the ping response to the server if needed
     *
     * @throws IOException on error
     */
    @WorkerThread
    private void sendPingResponseIfNeeded() throws IOException
    {
        if( shouldSendPingResponse.compareAndSet(true, false) )
        {
            sendPingResponse(timestampForPing);
        }
    }

//endregion

    //region Audio

    /**
     * Send the audio headers to the server.
     *
     * @throws IOException if an error occurs during the data sending.
     */
    @WorkerThread
    private void sendAudioHeader() throws IOException
    {
        Log.d(TAG, "Starting audio");

        if( audioHeader == null )
        {
            throw new IOException("Unable to find audio header value");
        }

        int length = audioHeader.getData().length + 2;

        /*
         * AAC header format
         */
        byte sound_format = 10; // AAC
        byte sound_type = 0; // 0 = Mono sound
        if (audioHeader.getNumberOfChannels() == 2)
        {
            sound_type = 1; // 1 = Stereo sound
        }

        byte sound_size = 1; // 1 = 16-bit samples
        byte sound_rate = (byte) audioHeader.getSampleSizeIndex();

        // 1 byte header: SoundFormat|SoundRate|SoundSize|SoundType
        aacFormat = (byte) (sound_type & 0x01);
        aacFormat |= (sound_size << 1) & 0x02;
        aacFormat |= (sound_rate << 2) & 0x0c;
        aacFormat |= (sound_format << 4) & 0xf0;

        ByteBuffer buffer = ByteBuffer.allocate(audioHeader.getData().length+14); // 14 = type 0 header + 2 bytes for AAC headers

        buffer.put(RtmpProtocol.generateType0Header(AUDIO_CHUNK_STREAM_ID, time.getCurrentTimestamp(), length, RtmpMessageType.AUDIO, streamId));

        buffer.put((byte) aacFormat);
        buffer.put((byte) 0);
        buffer.put(audioHeader.getData());

        writer.send(buffer.array());

        this.audioHeaderSent = true;
    }

    /**
     * Send the audio frame data to the server.
     *
     * @param frame the frame
     * @throws IOException if an error occurs while sending data
     */
    @WorkerThread
    private void sendAudioFrame(@NonNull AACAudioFrame frame) throws IOException
    {
        if ( audioHeader != null && !audioHeaderSent)
        {
            sendAudioHeader();
        }

        if (audioHeaderSent)
        {
            sendAckIfNeeded();
            sendPingResponseIfNeeded();

            int totalLength = frame.getData().length + 2;

            if( lastAudioTs == -1 )
            {
                lastAudioTs = frame.getTimestamp();
            }

            long delta = frame.getTimestamp() - lastAudioTs;
            lastAudioTs = frame.getTimestamp();

            int length = chunkSize - 2;
            if ( length > frame.getData().length )
            {
                length = frame.getData().length;
            }

            int bufferLength = length+10; // 10 = 2 bytes for AAC format && 8 bytes for type 1 header

            // Send first chunk
            final ByteBuffer buffer;
            if( audioChunkBuffer != null )
            {
                audioChunkBuffer.clear().limit(bufferLength);
                buffer = audioChunkBuffer;
            }
            else
            {
                Log.w(TAG, "Using a non cached buffer for audio chunk");
                buffer = ByteBuffer.allocate(bufferLength);
            }

            buffer.put(RtmpProtocol.generateType1Header(AUDIO_CHUNK_STREAM_ID, delta, totalLength, RtmpMessageType.AUDIO));
            buffer.put((byte) aacFormat);
            buffer.put((byte) 1);

            buffer.put(frame.getData(), 0, length);

            writer.send(buffer);

            // Chunking
            int offset = length;
            while ( offset < frame.getData().length )
            {
                length = chunkSize;
                if ( offset + length > frame.getData().length )
                {
                    length = frame.getData().length - offset;
                }

                int chunkBufferLength = length+1; // Data + type 3 header

                // Send first chunk
                final ByteBuffer chunkBuffer;
                if( audioChunkBuffer != null )
                {
                    audioChunkBuffer.clear().limit(chunkBufferLength);
                    chunkBuffer = audioChunkBuffer;
                }
                else
                {
                    Log.w(TAG, "Using a non cached buffer for sub audio chunk");
                    chunkBuffer = ByteBuffer.allocate(chunkBufferLength);
                }

                // Write chunk header
                chunkBuffer.put(RtmpProtocol.generateType3Header(AUDIO_CHUNK_STREAM_ID));

                // Write chunk data
                chunkBuffer.put(frame.getData(), offset, length);

                writer.send(chunkBuffer, true); // We bypass ACK waiting cause we're in the middle of a frame package

                offset += length;
            }
        }
        else
        {
            Log.w(TAG, "Skip audio frame");
        }
    }

    //endregion

    //region public methods

    /**
     * Starts the muxer by connecting to the server. Will call {@link RtmpConnectionListener#onReadyToPublish()}
     * if connection successes, {@link RtmpConnectionListener#onConnectionError(IOException)} on error.
     *
     * @param listener listener for connection events. Will be released when {@link #stop()} is called
     *                 or if an {@link IOException} is thrown during runtime.
     * @param app the RTMP application name to send data to
     * @param pageUrl optional RTMP page url
     * @param serverUrl optional RTMP server url
     * @throws NetworkOnMainThreadException if called from the main thread
     * @throws IllegalStateException if you call this method while the muxer is already started. Use
     *                               {@link #isStarted()} if you're not sure.
     */
    @WorkerThread
    public void start(@NonNull RtmpConnectionListener listener, @NonNull String app, @Nullable String serverUrl, @Nullable String pageUrl) throws NetworkOnMainThreadException, IllegalStateException
    {
        ensureWorkerThread();

        if( socket != null )
        {
            throw new IllegalStateException("RtmpMuxer is already started");
        }

        this.listener = listener;

        Log.d(TAG, "Start");

        try
        {
            socket = new TimeoutSocket();
            socket.connect(new InetSocketAddress(host, port), connectTimeout);
            socket.setSoLinger(false, 0);

            writer = new RtmpWriter(socket, writeTimeout, ackWaitTimeout);
            reader = new RtmpReader(socket.getInputStream(), handshakeTimeout, this);

            handshake();
            setChunkSize();
            sendWindowAckSize(writer.getAckWindowSize());
            connect(app, serverUrl, pageUrl);
        }
        catch (IOException e)
        {
            listener.onConnectionError(e);
            doStop();
        }
    }

    /**
     * Set the connection timeout. It should be called before calling
     * {@link #start(RtmpConnectionListener, String, String, String)} to be effective.
     * <p>
     * It will be used by the {@link Socket} when connecting to the server. This timeout is used
     * only for the socket connection, not for handshake. See {@link Socket#connect(SocketAddress, int)}
     * for details.
     *
     * @param connectTimeout the timeout in ms. (0 = infinite)
     * @throws IllegalArgumentException if {@code connectTimeout} is &lt; 0
     */
    public void setConnectTimeout(int connectTimeout) throws IllegalArgumentException
    {
        if( connectTimeout < 0 )
        {
            throw new IllegalArgumentException("Timeout must be >= 0");
        }

        this.connectTimeout = connectTimeout;
    }

    /**
     * Set the handshake read timeout. It should be called before calling
     * {@link #start(RtmpConnectionListener, String, String, String)} to be effective.
     * <p>
     * This timeout will be used when waiting for handshake 1 &amp; 2 from server. Since we wait for S1
     * and then S2, this timeout will be applied twice, so the global handshake be up to 2x{@code handshakeTimeout}
     * long.
     *
     * @param handshakeTimeout the timeout in ms. (0 = infinite)
     * @throws IllegalArgumentException if {@code handshakeTimeout} is &lt; 0
     */
    public void setHandshakeReadTimeout(int handshakeTimeout) throws IllegalArgumentException
    {
        if( handshakeTimeout < 0 )
        {
            throw new IllegalArgumentException("Timeout must be >= 0");
        }

        this.handshakeTimeout = handshakeTimeout;
        if( reader != null )
        {
            reader.setHandshakeTimeout(handshakeTimeout);
        }
    }

    /**
     * Set the write timeout. It should be called before calling
     * {@link #start(RtmpConnectionListener, String, String, String)} to be effective.
     * <p>
     * This timeout is used every time this muxer writes something to the server.
     *
     * @param writeTimeout the timeout in ms. (0 = infinite)
     * @throws IllegalArgumentException if {@code writeTimeout} is &lt; 0
     */
    public void setWriteTimeout(int writeTimeout) throws IllegalArgumentException
    {
        if( writeTimeout < 0 )
        {
            throw new IllegalArgumentException("Timeout must be >= 0");
        }

        this.writeTimeout = writeTimeout;
        if( writer != null )
        {
            writer.setWriteTimeout(writeTimeout);
        }
    }

    /**
     * Set the ack wait timeout. It should be called before calling
     * {@link #start(RtmpConnectionListener, String, String, String)} to be effective.
     * <p>
     * This timeout is used when we are waiting for an ACK from the server after a certain quantity
     * of bytes has been send. The server may take a few seconds to finish the process of the data
     * before answering so you may want to avoid using too little values.
     *
     * @param ackWaitTimeout the timeout in ms. (0 = infinite)
     * @throws IllegalArgumentException if {@code ackWaitTimeout} is &lt; 0
     */
    public void setAckWaitTimeout(int ackWaitTimeout) throws IllegalArgumentException
    {
        if( ackWaitTimeout < 0 )
        {
            throw new IllegalArgumentException("Timeout must be >= 0");
        }

        this.ackWaitTimeout = ackWaitTimeout;
        if( writer != null )
        {
            writer.setAckWaitTimeout(ackWaitTimeout);
        }
    }

    /**
     * Send the given video frame to the server.
     *
     * @param data an {@link H264VideoFrame} data
     * @throws IOException on error sending data to the server. Note that the muxer will be automatically
     *                     be stopped if this exception is thrown.
     * @throws NetworkOnMainThreadException if called from the main thread
     * @throws IllegalStateException if called when {@link RtmpConnectionListener#onReadyToPublish()} hasn't been called yet.
     */
    @WorkerThread
    public void postVideo(@NonNull H264VideoFrame data) throws IOException, NetworkOnMainThreadException, IllegalStateException
    {
        ensureWorkerThread();

        if( !streaming)
        {
            throw new IllegalStateException("You must wait for listener onReadyToPublish() to be called before posting data");
        }

        try
        {
            sendVideoData(data);
        }
        catch (IOException e)
        {
            doStop();
            throw e;
        }
    }

    /**
     * Set the audio header data. You can call this method at any time since data will be used once
     * audio frames are available.
     *
     * @param audioHeader the audio header
     */
    public void setAudioHeader(@NonNull AACAudioHeader audioHeader)
    {
        this.audioHeader = audioHeader;
    }

    /**
     * Send the given audio frame to the server. Note that the frame will be dropped if
     * {@link #setAudioHeader(AACAudioHeader)} hasn't been called yet.
     *
     * @param frame the audio frame
     * @throws IOException on error sending data to the server. Note that the muxer will be automatically
     *                     be stopped if this exception is thrown.
     * @throws NetworkOnMainThreadException if called from the main thread
     * @throws IllegalStateException if called when {@link RtmpConnectionListener#onReadyToPublish()} hasn't been called yet.
     */
    @WorkerThread
    public void postAudio(@NonNull AACAudioFrame frame) throws IOException, IllegalStateException, NetworkOnMainThreadException
    {
        ensureWorkerThread();

        if( !streaming)
        {
            throw new IllegalStateException("You must wait for listener onReadyToPublish() to be called before posting data");
        }

        try
        {
            sendAudioFrame(frame);
        }
        catch (IOException e)
        {
            doStop();
            throw e;
        }
    }

    /**
     * Send the given meta-data to the server.
     *
     * @param value the meta to send to the server
     * @throws IOException on error sending data to the server.
     * @throws NetworkOnMainThreadException if called from the main thread
     * @throws IllegalStateException if called when {@link RtmpConnectionListener#onReadyToPublish()} hasn't been called yet.
     */
    public void sendMetaData(@NonNull String value) throws IOException, NetworkOnMainThreadException, IllegalStateException
    {
        ensureWorkerThread();

        if( !streaming)
        {
            throw new IllegalStateException("You must wait for listener onReadyToPublish() to be called before posting meta");
        }

        byte[] metaData = Amf0Functions.textMeta(value);

        ByteBuffer buffer = ByteBuffer.allocate(metaData.length+12);

        buffer.put(RtmpProtocol.generateType0Header(META_DATA_STREAM_ID, time.getCurrentTimestamp(), metaData.length, RtmpMessageType.AMF_0_META_DATA, streamId));
        buffer.put(metaData);

        writer.send(buffer.array());
    }

    /**
     * Send the given data frame data to the server.
     *
     * @param frame the data to send to the server
     * @throws IOException on error sending data to the server.
     * @throws NetworkOnMainThreadException if called from the main thread
     * @throws IllegalStateException if called when {@link RtmpConnectionListener#onReadyToPublish()} hasn't been called yet.
     */
    public void sendDataFrame(@NonNull RtmpDataFrame frame) throws IOException, NetworkOnMainThreadException, IllegalStateException
    {
        ensureWorkerThread();

        if( !streaming)
        {
            throw new IllegalStateException("You must wait for listener onReadyToPublish() to be called before posting meta");
        }

        byte[] metaData = Amf0Functions.dataFrameMeta(frame.serialize());

        ByteBuffer buffer = ByteBuffer.allocate(metaData.length+12);

        // Timestamp has to be 0 for data frame
        buffer.put(RtmpProtocol.generateType0Header(META_DATA_STREAM_ID, time.getCurrentTimestamp(), metaData.length, RtmpMessageType.AMF_0_META_DATA, streamId));
        buffer.put(metaData);

        writer.send(buffer.array());
    }

    /**
     * Send the create stream command to the server.
     *
     * @param playpath name of the stream
     * @throws IOException on error sending data to the server.
     * @throws NetworkOnMainThreadException if called from the main thread
     * @throws IllegalStateException if called when {@link RtmpConnectionListener#onConnected()} hasn't been called yet.
     */
    public void createStream(@NonNull String playpath) throws IOException, NetworkOnMainThreadException, IllegalSelectorException
    {
        ensureWorkerThread();

        if( !connected )
        {
            throw new IllegalStateException("You must wait for listener onConnected() to be called before calling createStream");
        }

        this.playpath = playpath;

        byte[] createStreamCommand = Amf0Functions.createStream();

        ByteBuffer buffer = ByteBuffer.allocate(createStreamCommand.length+12);

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), createStreamCommand.length, RtmpMessageType.COMMAND, RtmpProtocol.CONTROL_MESSAGE_STREAM_ID));

        // Add createStream AMF function
        buffer.put(createStreamCommand);

        writer.send(buffer.array());
    }

    /**
     * Send the delete stream command to the server.
     *
     * @throws IOException on error sending data to the server.
     * @throws NetworkOnMainThreadException if called from the main thread
     * @throws IllegalStateException if called when {@link RtmpConnectionListener#onReadyToPublish()} hasn't been called yet.
     */
    public void deleteStream() throws IOException, NetworkOnMainThreadException, IllegalStateException
    {
        ensureWorkerThread();

        if( !streaming)
        {
            throw new IllegalStateException("You must wait for listener onReadyToPublish() to be called before calling deleteSteam");
        }

        byte[] deleteStreamCommand = Amf0Functions.deleteStream(streamId);

        ByteBuffer buffer = ByteBuffer.allocate(deleteStreamCommand.length+12);

        // Add header
        buffer.put(RtmpProtocol.generateType0Header(RtmpProtocol.CONTROL_MESSAGE_CHUNK_STREAM_ID, time.getCurrentTimestamp(), deleteStreamCommand.length, RtmpMessageType.COMMAND, RtmpProtocol.CONTROL_MESSAGE_STREAM_ID));

        // Add deleteStream AMF function
        buffer.put(deleteStreamCommand);

        writer.send(buffer.array());

        // Reset status
        streaming = false;
        audioHeader = null;
        audioHeaderSent = false;
        lastAudioTs = -1;
        lastVideoTs = -1;
        streamId = 0;
        playpath = null;
    }

    /**
     * Is this muxer started.
     *
     * @return true if the muxer is started, false otherwise
     */
    public boolean isStarted()
    {
        return socket != null;
    }

    /**
     * Stop this muxer, closing the connection to the server. You must call
     * {@link #start(RtmpConnectionListener, String, String, String)}
     * before doing anything else after calling this method.
     */
    public void stop()
    {
        Log.d(TAG, "Stop");

        if( socket == null )
        {
            Log.w(TAG, "Stop called while already stopped, do nothing");
        }

        doStop();
    }

    //endregion

    /**
     * Internal method that stops the muxer. Can be called after an error that's why we want to bypass
     * the {@link #stop()} public methods cause we are not sure of the state we are.
     */
    private void doStop()
    {
        try
        {
            this.reader.stop();
        }
        catch (Exception ignored) {}

        try
        {
            this.writer.stop();
        }
        catch (Exception ignored) {}

        try
        {
            this.socket.close();
        }
        catch (Exception ignored) {}

        this.socket = null;
        this.listener = null;
        this.reader = null;
        this.writer = null;
        streaming = false;
        connected = false;
        audioHeader = null;
        audioHeaderSent = false;
        lastAudioTs = -1;
        lastVideoTs = -1;
        bytesReadForAck = 0;
        shouldSendACK.set(false);
        shouldSendPingResponse.set(false);
        peerBandwidthLimitType = null;
        chunkSize = 4096;
        streamId = 0;
        videoChunkBuffer = null;
        audioChunkBuffer = null;
        playpath = null;
    }

    /**
     * Ensure that the method is called from a worker thread.
     *
     * @throws NetworkOnMainThreadException if called from the main thread
     */
    private static void ensureWorkerThread()
    {
        if( Looper.myLooper() == Looper.getMainLooper() )
        {
            throw new NetworkOnMainThreadException();
        }
    }

    //region RtmpReaderListener impl

    @Override
    public void onSetPeerBandwidth(long size, @NonNull RtmpPeerBandwidthLimitType type)
    {
        Log.d(TAG, "onSetPeerBandwidth: "+size+". Type: "+type);

        // Dynamic: If the previous Limit Type was Hard, treat this message as though it was marked Hard, otherwise ignore this message.
        if( type == RtmpPeerBandwidthLimitType.DYNAMIC )
        {
            if( peerBandwidthLimitType != RtmpPeerBandwidthLimitType.HARD )
            {
                return; // Ignore
            }
            else
            {
                type = RtmpPeerBandwidthLimitType.HARD;
            }
        }

        peerBandwidthLimitType = type;

        boolean hasAckWindowChanged = false;

        //  Hard: The peer SHOULD limit its output bandwidth to the indicated window size.
        if( type == RtmpPeerBandwidthLimitType.HARD && size != writer.getAckWindowSize() )
        {
            writer.setAckWindow(size);
            hasAckWindowChanged = true;
        }
        // Soft: The peer SHOULD limit its output bandwidth to the the window indicated in this
        // message or the limit already in effect, whichever is smaller.
        else if( type == RtmpPeerBandwidthLimitType.SOFT )
        {
            if( size < writer.getAckWindowSize() )
            {
                writer.setAckWindow(size);
                hasAckWindowChanged = true;
            }
        }

        /*
         * The peer receiving this message SHOULD respond with a Window Acknowledgement
         * Size message if the window size is different from the last one sent
         * to the writer of this message.
         */
        if( hasAckWindowChanged )
        {
            try
            {
                sendWindowAckSize(size);
            }
            catch (IOException e)
            {
                Log.e(TAG, "Error while sending ACK window size after setPeerBandwidth received", e);
            }
        }
    }

    @Override
    public void onAck(long bytesReceived)
    {
        Log.d(TAG, "onAck: "+bytesReceived);

        writer.onAck(bytesReceived);
    }

    @Override
    public void onNeedToSendAck(long bytesReceived)
    {
        bytesReadForAck = bytesReceived;
        shouldSendACK.set(true);
    }

    @Override
    public void onNeedToSendPingResponse(long timestamp)
    {
        Log.d(TAG, "onNeedToSendPingResponse: "+timestamp);

        if( connected && !streaming ) // Should happen every time since we are not supposed to get ping while streaming
        {
            try
            {
                Log.d(TAG, "Sending auto ping response while idle");
                sendPingResponse(timestamp); // FIXME This happens on reader thread, could be better
            }
            catch (Exception e)
            {
                Log.e(TAG, "Error while sending auto ping response", e);
            }
        }
        else // In case we are getting ping while streaming, response will be sent before next frame
        {
            timestampForPing = timestamp;
            shouldSendPingResponse.set(true);
        }
    }

    @Override
    public void onSetChunkSize(long size)
    {
        Log.d(TAG, "onSetChunkSize: " + size);

        chunkSize = (int) size;

        if( videoChunkBuffer == null )
        {
            videoChunkBuffer = ByteBuffer.allocate(chunkSize + 8); // 8 for type 1 header
        }
        else
        {
            // FIXME how to manage concurrency ?
            Log.w(TAG, "Received onSetChunkSize but videoChunkBuffer is already initialized, so keep the size as-is");
        }

        if( audioChunkBuffer == null )
        {
            audioChunkBuffer = ByteBuffer.allocate(chunkSize + 8); // 8 for type 1 header
        }
        else
        {
            // FIXME how to manage concurrency ?
            Log.w(TAG, "Received onSetChunkSize but audioChunkBuffer is already initialized, so keep the size as-is");
        }
    }

    @Override
    public void onConnect()
    {
        Log.d(TAG, "onConnect");

        connected = true;
        listener.onConnected();
    }

    @Override
    public void onStreamCreated(int streamId)
    {
        Log.d(TAG, "onStreamCreated: "+streamId);

        this.streamId = streamId;

        try
        {
            publish();
        }
        catch (IOException e)
        {
            listener.onConnectionError(e);
            doStop();
        }
    }

    @Override
    public void onPublish()
    {
        Log.d(TAG, "onPublish");

        streaming = true;
        listener.onReadyToPublish();
    }

    @Override
    public void onReaderError(@NonNull IOException e)
    {
        Log.d(TAG, "onReaderError", e);

        RtmpConnectionListener listener = this.listener;
        doStop();
        listener.onConnectionError(e);
    }

    //endregion
}
