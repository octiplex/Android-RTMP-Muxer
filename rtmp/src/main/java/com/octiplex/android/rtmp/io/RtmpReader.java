package com.octiplex.android.rtmp.io;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.octiplex.android.rtmp.protocol.Amf0Functions;
import com.octiplex.android.rtmp.protocol.Amf0Value;
import com.octiplex.android.rtmp.protocol.AmfNull;
import com.octiplex.android.rtmp.protocol.RtmpMessageType;
import com.octiplex.android.rtmp.protocol.RtmpPeerBandwidthLimitType;
import com.octiplex.android.rtmp.protocol.RtmpUserControlEventType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Class that parses RTMP data received from server
 *
 * @author Benoit LETONDOR
 */
public final class RtmpReader implements Runnable
{
    @NonNull
    private final static String TAG = "RtmpReader";

    /**
     * Number of bytes read since last ACK
     */
    private long dataRead = 0;
    /**
     * Number of bytes we can read before sending an ACK to the server
     */
    private long ackWindowSize = 5000000;
    /**
     * Stream used by the server to send data to the client
     */
    @NonNull
    private final BufferedInputStream in;
    /**
     * Listener of this parser
     */
    @NonNull
    private final RtmpReaderListener listener;
    /**
     * Executor that checks for new data from server every once in a while
     */
    @NonNull
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /**
     * Timeout for handshake read operations (in ms)
     */
    private int handshakeTimeout;

// ----------------------------------------->

    /**
     * Init the parser with the given stream (must be opened)
     *
     * @param in the stream to read into (must be opened)
     * @param handshakeTimeout timeout for handshake read operations (in ms)
     * @param listener listener
     */
    public RtmpReader(@NonNull InputStream in, int handshakeTimeout, @NonNull RtmpReaderListener listener)
    {
        this.in = new BufferedInputStream(in);
        this.listener = listener;
        this.handshakeTimeout = handshakeTimeout;
    }

// ----------------------------------------->

    /**
     * Start the thread that will read input stream data
     */
    public void start()
    {
        readAsync();
    }

    /**
     * Stop the thread that read input stream data
     */
    public void stop()
    {
        executor.shutdownNow();
    }

    /**
     * Set the handshake timeout (in ms).
     *
     * @param handshakeTimeout handshake read timeout (in ms).
     */
    public void setHandshakeTimeout(int handshakeTimeout)
    {
        this.handshakeTimeout = handshakeTimeout;
    }

// ----------------------------------------->

    /**
     * Schedule the next read. Will do nothing if {@link #stop()} has been called
     */
    private void readAsync()
    {
        try
        {
            executor.submit(this);
        }
        catch (RejectedExecutionException ignored)
        {
            // Nothing to be done here, stop() method has been called
        }
    }

    /**
     * Read the next command sent by the server. Will call the appropriate listener if a command
     * is found.<p>
     * <b>Warning:</b> This function will block the current thread until the server sends a command.
     */
    @Override
    public void run()
    {
        try
        {
            in.mark(12); // put a mark of the header's length to be able to rollback

            int basicHeader = in.read(); // blocking

            if( basicHeader == -1 ) // Error
            {
                listener.onReaderError(new ServerException("Header -1"));
                return;
            }

            if( basicHeader == 2 || basicHeader == 3 || basicHeader == 5 )
            {
                if( in.skip(3) != 3 ) // Timestamp
                {
                    throw new IOException("Unable to read timestamp");
                }

                byte[] bodySizeBuffer = new byte[3];
                if( in.read(bodySizeBuffer) != 3 )
                {
                    throw new IOException("Unable to read body size");
                }

                long bodySize = readNumber(3, 0, bodySizeBuffer);
                RtmpMessageType type = RtmpMessageType.fromValue(in.read());

                if( in.skip(4) != 4 ) // Stream ID
                {
                    throw new IOException("Unable to read stream ID");
                }

                // Check that body message is complete before reading it
                if( in.available() < bodySize )
                {
                    in.reset();

                    // Call next read
                    readAsync();
                    return;
                }

                // If type is unknown, just skip it
                if( type == null )
                {
                    Log.e("RtmpReader", "Unknown type, skipping");

                    if( in.skip(bodySize) != bodySize )
                    {
                        throw new IOException("Unable to skip unknown type");
                    }

                    // Call next read
                    readAsync();
                    return;
                }

                byte[] buffer = new byte[(int) bodySize];

                if( in.read(buffer) != bodySize )
                {
                    throw new IOException("Unable to read body");
                }

                parseMessage(type, buffer);

                dataRead += bodySize + 12;
                if( dataRead >= ackWindowSize ) // Need to send an ACK
                {
                    listener.onNeedToSendAck(dataRead);
                    dataRead = 0;
                }
            }
            else
            {
                throw new IOException("Unknown basic header: "+basicHeader);
            }

            // Call next read
            readAsync();
        }
        catch (IOException e)
        {
            // Prevent send of Socket closed exception
            if( !executor.isShutdown() )
            {
                listener.onReaderError(e);
            }
        }
    }

    /**
     * Read handshake 0 composed of 1 byte defining the RTMP version
     *
     * @return the RTMP version used by the server
     * @throws IOException
     */
    @WorkerThread
    public byte readHandshakeS0() throws IOException
    {
        dataRead += 1;

        return (byte) in.read();
    }

    /**
     * Read and returns the S1 handshake<p>
     * <b>Warning:</b> This function will block the current thread until the server sends S1 (with limit).
     *
     * @return bytes of the S1 handshake
     * @throws IOException if the server is too slow sending S1 or on network error
     */
    @WorkerThread
    @NonNull
    public byte[] readHandshakeS1() throws IOException
    {
        /*
         * S1: 1536 bytes long
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

        new IOTimeoutAction(handshakeTimeout)
        {
            @Override
            public boolean condition() throws IOException
            {
                return in.available() >= 1536;
            }

        }.execute();

        byte[] s1 = new byte[1536];
        if( in.read(s1) != 1536 )
        {
            throw new IOException("Unable to read S1");
        }

        dataRead += 1536;

        return s1;
    }

    /**
     * Read and returns the S2 handshake.<p>
     * <b>Warning:</b> This function will block the current thread until the server sends S2 (with limit).
     *
     * @return bytes of the S2 handshake
     * @throws IOException if the server is too slow sending S2 or on network error
     */
    @WorkerThread
    @NonNull
    public byte[] readHandshakeS2() throws IOException
    {
        /*
         * S2: 1536 bytes long
         *
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                        time (4 bytes)                         |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                        time2 (4 bytes)                        |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                          random echo                          |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                          random echo                          |
         * |                            (cont)                             |
         * |                             ....                              |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

        new IOTimeoutAction(handshakeTimeout)
        {
            @Override
            public boolean condition() throws IOException
            {
                return in.available() >= 1536;
            }

        }.execute();

        byte[] s2 = new byte[1536];
        if( in.read(s2) != 1536 )
        {
            throw new IOException("Unable to read S2");
        }

        dataRead += 1536;

        return s2;
    }

// ----------------------------------------->

    /**
     * Parse a message sent by the server
     *
     * @param type type of message
     * @param buffer body of the message
     * @throws IOException
     */
    private void parseMessage(@NonNull RtmpMessageType type, @NonNull byte[] buffer) throws IOException
    {
        switch ( type )
        {
            case SET_CHUNK_SIZE:
            {
                parseSetChunkSizeMessage(buffer);
                break;
            }
            case WINDOW_ACK_SIZE:
            {
                parseWindowAckSize(buffer);
                break;
            }
            case PEER_BANDWIDTH:
            {
                parsePeerBandwidth(buffer);
                break;
            }
            case ACK:
            {
                parseAck(buffer);
                break;
            }
            case COMMAND:
            {
                parseAmf0Function(buffer);
                break;
            }
            case USER_CONTROL_MESSAGE:
            {
                parseUserControlMessage(buffer);
                break;
            }
            default:
            {
                throw new IOException("Unable to parse message of type: "+type);
            }
        }
    }

    /**
     * Parse the AMF0 function contained in the buffer
     *
     * @param buffer bytes of data sent by the server containing an AMF0 function
     * @throws IOException
     */
    private void parseAmf0Function(@NonNull byte[] buffer) throws IOException
    {
        Amf0Value<String> functionName = Amf0Functions.readString(0, buffer);

        switch (functionName.value)
        {
            case Amf0Functions.RESULT :
                parseResultFunction(functionName.length, buffer);
                break;
            case Amf0Functions.ON_STATUS :
                parseOnStatusFunction(functionName.length, buffer);
                break;
            case Amf0Functions.ERROR :
                listener.onReaderError(new ServerException("Error received from the server: "+Amf0Functions.ERROR));
                break;
            default :
                throw new IOException("Unknown command: "+functionName.value);
        }
    }

    /**
     * Parse the AMF0 onStatus function contained in the buffer. Will call {@link RtmpReaderListener#onPublish()}
     * on the {@link #listener} if the function contains the publish status ok.
     *
     * @param offset offset to start reading from to get the onStatus function data into the buffer.
     * @param buffer bytes of data sent by the server containing the onStatus function
     * @throws IOException
     */
    private void parseOnStatusFunction(int offset, @NonNull byte[] buffer) throws IOException
    {
        /*
         * +--------------+----------+----------------------------------------+
         * |  Field Name  |   Type   |              Description               |
         * +--------------+----------+----------------------------------------+
         * +--------------+----------+----------------------------------------+
         * | Command Name |  String  |      The command name "onStatus".      |
         * +--------------+----------+----------------------------------------+
         * | Transaction  |  Number  |        Transaction ID set to 0.        |
         * |     ID       |          |                                        |
         * +--------------+----------+----------------------------------------+
         * |   Command    |   Null   |     There is no command object for     |
         * |    Object    |          |          onStatus messages.            |
         * +--------------+----------+----------------------------------------+
         * | Info Object  |  Object  |    An AMF object having at least the   |
         * |              |          |   following three properties: "level"  |
         * |              |          |  (String): the level for this message, |
         * |              |          | one of "warning", "status", or "error";|
         * |              |          | "code" (String): the message code, for |
         * |              |          |   example "NetStream.Play.Start"; and  |
         * |              |          |     "description" (String): a human-   |
         * |              |          |  readable description of the message.  |
         * |              |          |    The Info object MAY contain other   |
         * |              |          | properties as appropriate to the code. |
         * +--------------+----------+----------------------------------------+
         */

        // Transaction ID
        Amf0Value<Double> transactionId = Amf0Functions.readNumber(offset, buffer);
        if( transactionId.value != 0.0 )
        {
            throw new IOException("OnStatus received with transaction id != 0 ("+transactionId.value+")");
        }

        offset += transactionId.length;

        // Command object = null
        Amf0Value<AmfNull> command = Amf0Functions.readNull(offset, buffer);
        offset += command.length;

        // Info object
        Amf0Value<Map<String, Object>> info = Amf0Functions.readObject(offset, buffer);
        if( info.value.containsKey("code") )
        {
            String code = (String) info.value.get("code");

            if( "NetStream.Publish.Start".equals(code) )
            {
                listener.onPublish();
            }
            else if( code.startsWith("NetStream.Publish") ) // if it's an error on publish
            {
                listener.onReaderError(new ServerException("Bad publish response: "+code));
            }
        }
    }

    /**
     * Parse the AMF0 result function contained in the buffer.
     *
     * @param offset offset to start reading from to get the result function data into the buffer.
     * @param buffer bytes of data sent by the server containing the result function
     * @throws IOException if result is != ok
     */
    private void parseResultFunction(int offset, @NonNull byte[] buffer) throws IOException
    {
        // Transaction Id
        Amf0Value<Double> transactionId = Amf0Functions.readNumber(offset, buffer);
        offset += transactionId.length;
        if( transactionId.value == 1.0 )
        {
            parseOnConnectResult(offset, buffer);
        }
        else if( transactionId.value == Amf0Functions.CREATE_STREAM_TRANSACTION_ID )
        {
            parseOnCreateStreamResult(offset, buffer);
        }
        else
        {
            throw new IOException("Result received with transaction id unknown ("+transactionId.value+")");
        }
    }

    /**
     * Parse the AMF0 result function for connect command contained in the buffer. Will call
     * {@link RtmpReaderListener#onConnect()} on the {@link #listener} if the result is ok, will throw otherwise.
     *
     * @param offset offset to start reading after the transaction into the buffer.
     * @param buffer bytes of data sent by the server containing the result function
     * @throws IOException if result is != ok
     */
    private void parseOnConnectResult(int offset, @NonNull byte[] buffer) throws IOException
    {
        /*
         * +--------------+----------+----------------------------------------+
         * |  Field Name  |   Type   |              Description               |
         * +--------------+----------+----------------------------------------+
         * +--------------+----------+----------------------------------------+
         * | Command Name |  String  |  _result or _error; indicates whether  |
         * |              |          |    the response is result or error.    |
         * +--------------+----------+----------------------------------------+
         * | Transaction  |  Number  |     Transaction ID is 1 for connect    |
         * |     ID       |          |                responses               |
         * +--------------+----------+----------------------------------------+
         * |  Properties  |  Object  |   Name-value pairs that describe the   |
         * |              |          |     properties(fmsver etc.) of the     |
         * |              |          |               connection.              |
         * +--------------+----------+----------------------------------------+
         * | Information  |  Object  |    Name-value pairs that describe the  |
         * |              |          |    response from|the server. ’code’,   |
         * |              |          | ’level’, ’description’ are names of few|
         * |              |          |         among such information.        |
         * +--------------+----------+----------------------------------------+
         */

        // Properties TODO parse ?
        Amf0Value<Map<String, Object>> properties = Amf0Functions.readObject(offset, buffer);
        offset += properties.length;

        // Information
        Amf0Value<Map<String, Object>> information = Amf0Functions.readObject(offset, buffer);
        if( information.value.containsKey("code") )
        {
            String code = (String) information.value.get("code");

            if( "NetConnection.Connect.Success".equals(code) )
            {
                listener.onConnect();
                return;
            }
            else if( code.startsWith("NetConnection.Connect") )
            {
                listener.onReaderError(new ServerException("Bad connect response: "+code));
                return;
            }
        }

        throw new IOException("Result received without connection success");
    }

    /**
     * Parse the AMF0 result function for create stream command contained in the buffer. Will call
     * {@link RtmpReaderListener#onStreamCreated(int)} on the {@link #listener} if the result is ok,
     * will throw otherwise.
     *
     * @param offset offset to start reading after the transaction into the buffer.
     * @param buffer bytes of data sent by the server containing the result function
     * @throws IOException if result is != ok
     */
    private void parseOnCreateStreamResult(int offset, @NonNull byte[] buffer) throws IOException
    {
        /*
         * +--------------+----------+----------------------------------------+
         * |  Field Name  |   Type   |             Description                |
         * +--------------+----------+----------------------------------------+
         * | Command Name |  String  |  _result or _error; indicates whether  |
         * |              |          |    the response is result or error.    |
         * +--------------+----------+----------------------------------------+
         * |  Transaction |  Number  | ID of the command that response belongs|
         * |      ID      |          |                  to.                   |
         * +--------------+----------+----------------------------------------+
         * |    Command   |  Object  |  If there exists any command info this |
         * |     Object   |          | is set, else this is set to null type. |
         * +--------------+----------+----------------------------------------+
         * |    Stream    |  Number  | The return value is either a stream ID |
         * |     ID       |          |      or an error information object.   |
         * +--------------+----------+----------------------------------------+
         */

        try
        {
            Amf0Value<Map<String, Object>> commandObject = Amf0Functions.readObject(offset, buffer);
            offset += commandObject.length;
        }
        catch (IOException e) // Then command Object must be null
        {
            Amf0Value<AmfNull> nullValue = Amf0Functions.readNull(offset, buffer);
            offset += nullValue.length;
        }

        listener.onStreamCreated(Amf0Functions.readNumber(offset, buffer).value.intValue());
    }

    /**
     * Parse the set peer bandwidth command sent by the server. Will call
     * {@link RtmpReaderListener#onSetPeerBandwidth(long, RtmpPeerBandwidthLimitType)}
     * after parsing
     *
     * @param buffer buffer containing the parse peer bandwidth data sent by the server
     * @throws IOException
     */
    private void parsePeerBandwidth(@NonNull byte[] buffer) throws IOException
    {
        /*
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |                Acknowledgement Window size                    |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |   Limit Type  |
         * +-+-+-+-+-+-+-+-+
         */

        long size = readNumber(4, 0, buffer);
        RtmpPeerBandwidthLimitType type = RtmpPeerBandwidthLimitType.fromValue(buffer[4]);

        if( type == null )
        {
            throw new IOException("Unable to parse peer bandwidth limit type ("+buffer[4]+")");
        }

        listener.onSetPeerBandwidth(size, type);
    }

    /**
     * Parse the window ack size command sent by the server and store the value.
     *
     * @param buffer buffer containing the window ack size data sent by the server
     * @throws IOException
     */
    private void parseWindowAckSize(@NonNull byte[] buffer) throws IOException
    {
        /*
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |            Acknowledgement Window size (4 bytes)              |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

        ackWindowSize = readNumber(4, 0, buffer);
    }

    /**
     * Parse the set chunk size command sent by the server and call {@link RtmpReaderListener#onSetChunkSize(long)}
     * on the {@link #listener} after parsing.
     *
     * @param buffer buffer containing the set chunk size command data sent by the server
     * @throws IOException
     */
    private void parseSetChunkSizeMessage(@NonNull byte[] buffer) throws IOException
    {
        /*
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |0|                   chunk size (31 bits)                      |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        listener.onSetChunkSize(readNumber(4, 0, buffer));
    }

    /**
     * Parse the ACK command sent by the server and call {@link RtmpReaderListener#onAck(long)}
     * on the {@link #listener} after parsing.
     *
     * @param buffer buffer containing the ACK command data sent by the server
     * @throws IOException
     */
    private void parseAck(@NonNull byte[] buffer) throws IOException
    {
        /*
         *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |          number of bytes received so far (4 bytes)            |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        listener.onAck(readNumber(4, 0, buffer));
    }

    /**
     * Parse user control message sent by the server.
     *
     * @param buffer buffer containing the user control message and its data
     * @throws IOException
     */
    private void parseUserControlMessage(@NonNull byte[] buffer) throws IOException
    {
        /*
         * +------------------------------+-------------------------
         * |    Event Type (16 bits)      |      Event Data
         * +------------------------------+-------------------------
         */
        long eventTypeValue = readNumber(2, 0, buffer);

        Log.d(TAG, "User control message received of type: "+eventTypeValue);

        RtmpUserControlEventType eventType = RtmpUserControlEventType.fromValue((int) eventTypeValue);
        if( eventType == null )
        {
            throw new IOException("Unable to read type user control message (type value: "+eventTypeValue+")");
        }

        switch (eventType)
        {
            case PING_REQUEST:
            {
                long timestamp = readNumber(4, 2, buffer);
                listener.onNeedToSendPingResponse(timestamp);
                break;
            }
            // TODO other message types
        }
    }

// ----------------------------------------->

    /**
     * Read a number value into the given stream contained in the specified number of bytes
     *
     * @param size number of bytes used for this int (currently only 2, 3 and 4 are supported)
     * @param offset offset to start reading data from into the buffer
     * @param data data to read into
     * @return the found int value
     * @throws IOException on error or if size is not 2, 3 or 4
     */
    // FIXME this really needs to be less lame!!
    public static long readNumber(int size, int offset, @NonNull byte[] data) throws IOException
    {
        if( size == 2 )
        {
            return (data[offset] & 255) << 8 | (data[offset+1] & 255);
        }
        else if( size == 3 )
        {
            return (data[offset] & 255) << 16 | (data[offset+1] & 255) << 8 | (data[offset+2] & 255);
        }
        else if( size == 4 )
        {
            return (data[offset] & 255) << 24 | (data[offset+1] & 255) << 16 | (data[offset+2] & 255) << 8 | (data[offset+3] & 255);
        }

        throw new IOException("Unable to read long for size: "+size);
    }

// ----------------------------------------->

    /**
     * Listener interface of the reader
     */
    public interface RtmpReaderListener
    {
        /**
         * Called by the server to specify the peer bandwidth used
         *
         * @param size ack size
         * @param type type of limit
         */
        void onSetPeerBandwidth(long size, @NonNull RtmpPeerBandwidthLimitType type);

        /**
         * Called when the server send an ACK
         *
         * @param bytesReceived number of bytes received by the server
         */
        void onAck(long bytesReceived);

        /**
         * Called when the client needs to send an ACK to the server
         *
         * @param bytesReceived number of byte we received
         */
        void onNeedToSendAck(long bytesReceived);

        /**
         * Called when the client needs to send a ping response to the server
         *
         * @param timestamp timestamp sent by the server
         */
        void onNeedToSendPingResponse(long timestamp);

        /**
         * Called by the server to specify the chunk size used
         *
         * @param size chunk size
         */
        void onSetChunkSize(long size);

        /**
         * Called when the connection is successful
         */
        void onConnect();

        /**
         * Called when the stream id is created
         *
         * @param streamId the created stream id
         */
        void onStreamCreated(int streamId);

        /**
         * Called when publish is starting
         */
        void onPublish();

        /**
         * Called on error
         *
         * @param e the error
         */
        void onReaderError(@NonNull IOException e);
    }
}
