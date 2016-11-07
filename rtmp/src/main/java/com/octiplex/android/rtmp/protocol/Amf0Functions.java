package com.octiplex.android.rtmp.protocol;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import com.octiplex.android.rtmp.io.RtmpReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that implements AMF0 functions to send to the server. <p>
 * See <a href="http://wwwimages.adobe.com/content/dam/Adobe/en/devnet/amf/pdf/amf0-file-format-specification.pdf">RCF</a>
 * for specifications.
 *
 * @author Benoit LETONDOR
 */
public final class Amf0Functions
{
    @NonNull
    private static final String TAG = "Amf0Functions";

    /**
     * Publish function
     */
    @NonNull
    private static final String PUBLISH = "publish";
    /**
     * Connect function
     */
    @NonNull
    private static final String CONNECT = "connect";
    /**
     * Create stream function
     */
    @NonNull
    private static final String CREATE_STREAM = "createStream";
    /**
     * Delete stream function
     */
    @NonNull
    private static final String DELETE_STREAM = "deleteStream";
    /**
     * Transaction ID used to send a create stream command
     */
    public static final int CREATE_STREAM_TRANSACTION_ID = 10;
    /**
     * On status function
     */
    @NonNull
    public static final String ON_STATUS = "onStatus";
    /**
     * Result function
     */
    @NonNull
    public static final String RESULT = "_result";
    /**
     * Error function
     */
    @NonNull
    public static final String ERROR = "_error";

// ------------------------------------------>

    private Amf0Functions()
    {
        // Not instanciable
    }

// ------------------------------------------>

    /**
     * Create the publish function bytes
     *
     * @param streamName name of the stream
     * @return bytes of the function
     * @throws IOException on error
     */
    @NonNull
    public static byte[] publish(@NonNull String streamName) throws IOException
    {
        /*
         * +--------------+----------+----------------------------------------+
         * |  Field Name  |   Type   |              Description               |
         * +--------------+----------+----------------------------------------+
         * +--------------+----------+----------------------------------------+
         * | Command Name |  String  | Name of the command, set to "publish". |
         * +--------------+----------+----------------------------------------+
         * |  Transaction |  Number  |        Transaction ID set to 0.        |
         * |      ID      |          |                                        |
         * +--------------+----------+----------------------------------------+
         * |    Command   |   Null   |  Command information object does not   |
         * |    Object    |          |        exist. Set to null type.        |
         * +--------------+----------+----------------------------------------+
         * |  Publishing  |  String  |       Name with which the stream is    |
         * |     Name     |          |                published.              |
         * +--------------+----------+----------------------------------------+
         * |  Publishing  |  String  |   Type of publishing. Set to "live",   |
         * |     Type     |          |          "record", or "append".        |
         * +--------------+----------+----------------------------------------+
         */
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try
        {
            addStringParam(os, PUBLISH);
            addNumberParam(os, 0);
            addNullParam(os);
            addStringParam(os, streamName);
            addStringParam(os, "live");

            return os.toByteArray();
        }
        finally
        {
            try { os.close(); } catch (Exception ignored){}
        }
    }

    /**
     * Create the createStream function bytes
     *
     * @return bytes of the function
     * @throws IOException on error
     */
    @NonNull
    public static byte[] createStream() throws IOException
    {
        /*
         * +--------------+----------+----------------------------------------+
         * |  Field Name  |   Type   |              Description               |
         * +--------------+----------+----------------------------------------+
         * | Command Name |  String  |        Name of the command. Set to     |
         * |              |          |             "createStream".            |
         * +--------------+----------+----------------------------------------+
         * | Transaction  |  Number  |       Transaction ID of the command.   |
         * |      ID      |          |                                        |
         * +--------------+----------+----------------------------------------+
         * |    Command   |  Object  |  If there exists any command info this |
         * |     Object   |          | is set, else this is set to null type. |
         * +--------------+----------+----------------------------------------+
         */

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try
        {
            addStringParam(os, CREATE_STREAM);
            addNumberParam(os, CREATE_STREAM_TRANSACTION_ID);
            addNullParam(os);

            return os.toByteArray();
        }
        finally
        {
            try
            {
                os.close();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    /**
     * Create the deleteStream function bytes
     *
     * @param streamId id of the stream
     * @return bytes of the function
     * @throws IOException on error
     */
    @NonNull
    public static byte[] deleteStream(int streamId) throws IOException
    {
        /*
         * +--------------+----------+----------------------------------------+
         * |  Field Name  |   Type   |              Description               |
         * +--------------+----------+----------------------------------------+
         * | Command Name |  String  |       Name of the command, set to      |
         * |              |          |             "deleteStream".            |
         * +--------------+----------+----------------------------------------+
         * | Transaction  |  Number  |        Transaction ID set to 0.        |
         * |      ID      |          |                                        |
         * +--------------+----------+----------------------------------------+
         * |   Command    |   Null   |  Command information object does not   |
         * |    Object    |          |        exist. Set to null type.        |
         * +--------------+----------+----------------------------------------+
         * |   Stream ID  |  Number  | The ID of the stream that is destroyed |
         * |              |          |             on the server.             |
         * +--------------+----------+----------------------------------------+
         */

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try
        {
            addStringParam(os, DELETE_STREAM);
            addNumberParam(os, 0);
            addNullParam(os);
            addNumberParam(os, streamId);

            return os.toByteArray();
        }
        finally
        {
            try
            {
                os.close();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    /**
     * Create the connect function bytes
     *
     * @param app name of the app to connect to
     * @return bytes of the function
     * @throws IOException on error
     */
    @NonNull
    public static byte[] connect(@NonNull String app, @Nullable String serverUrl, @Nullable String pageUrl) throws IOException
    {
        /*
         * +----------------+---------+---------------------------------------+
         * |   Field Name   |  Type   |              Description              |
         * +--------------- +---------+---------------------------------------+
         * +--------------- +---------+---------------------------------------+
         * | Command Name   | String  | Name of the command. Set to "connect".|
         * +----------------+---------+---------------------------------------+
         * | Transaction ID | Number  |           Always set to 1.            |
         * +----------------+---------+---------------------------------------+
         * | Command Object | Object  |  Command information object which has |
         * |                |         |          the name-value pairs.        |
         * +----------------+---------+---------------------------------------+
         */

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try
        {
            addStringParam(os, CONNECT);
            addNumberParam(os, 1);

            // Add the app param
            Map<String,Object> params = new ArrayMap<>();
            params.put("app", app);

            if( serverUrl != null )
            {
                params.put("tcUrl", serverUrl);
            }

            if( pageUrl != null )
            {
                params.put("pageUrl", pageUrl);
            }

            addObjectParam(os, params);

            return os.toByteArray();
        }
        finally
        {
            try { os.close(); } catch (Exception ignored){}
        }
    }

    /**
     * Generate the text meta-data bytes
     *
     * @param value the text meta to send to the server
     * @return serialized bytes
     * @throws IOException
     */
    @NonNull
    public static byte[] textMeta(@NonNull String value) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try
        {
            addStringParam(os, "onTextData");

            Map<String, Object> obj = new HashMap<>(1);
            obj.put("text", value);

            Amf0Functions.addMapParam(os, obj);

            return os.toByteArray();
        }
        finally
        {
            try { os.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Generate the data frame sending bytes
     *
     * @param values the map containing dataframe data to send to the server
     * @return serialized bytes
     * @throws IOException
     */
    @NonNull
    public static byte[] dataFrameMeta(Map<String, Object> values) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try
        {
            addStringParam(os, "@setDataFrame");
            addStringParam(os, "onMetaData");

            Amf0Functions.addMapParam(os, values);

            return os.toByteArray();
        }
        finally
        {
            try { os.close(); } catch (Exception ignored) {}
        }
    }


// ------------------------------------------>

    /**
     * Add a string parameter to the function at the end of the given output stream
     *
     * @param os output stream to write into
     * @param value string value to put
     * @throws IOException on error
     */
    @VisibleForTesting
    protected static void addStringParam(@NonNull ByteArrayOutputStream os, @NonNull String value) throws IOException
    {
        byte[] bytes = value.getBytes();

        // Param type
        os.write(ParamType.STRING.value);

        os.write((bytes.length >> 8) & 255);
        os.write(bytes.length & 255);

        // Param value
        for( byte byteValue : bytes )
        {
            os.write(byteValue);
        }
    }

    /**
     * Add a number parameter to the function at the end of the given output stream
     *
     * @param os output stream to write into
     * @param value value to put
     * @throws IOException on error
     */
    @VisibleForTesting
    protected static void addNumberParam(@NonNull ByteArrayOutputStream os, Number value) throws IOException
    {
        // Param type
        os.write(ParamType.NUMBER.value);

        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value.doubleValue());

        os.write(bytes);
    }

    /**
     * Add a boolean parameter to the function at the end of the given output stream
     *
     * @param os output stream to write into
     * @param value boolean value to put
     * @throws IOException on error
     */
    @VisibleForTesting
    protected static void addBooleanParam(@NonNull ByteArrayOutputStream os, boolean value) throws IOException
    {
        // Param type
        os.write(ParamType.BOOLEAN.value);

        // Value
        os.write(value ? 1 : 0);
    }

    /**
     * Add a null parameter to the function at the end of the given output stream
     *
     * @param os output stream to write into
     * @throws IOException on error
     */
    @VisibleForTesting
    protected static void addNullParam(@NonNull ByteArrayOutputStream os) throws IOException
    {
        os.write(ParamType.NULL.value);
    }

    /**
     * Add an object parameter to the function at the end of the given output stream
     *
     * @param os output stream to write into
     * @param object object to putc
     * @throws IOException on error
     */
    @VisibleForTesting
    protected static void addObjectParam(@NonNull ByteArrayOutputStream os, @NonNull Map<String, Object> object) throws IOException
    {
        // Param type
        os.write(ParamType.OBJECT.value);

        serializeValues(os, object);

        // End of Object marker
        os.write(0);
        os.write(0);
        os.write(ParamType.END_OF_OBJECT.value);
    }

    /**
     * Add a map parameter to the function at the end of the given output stream
     *
     * @param os output stream to write into
     * @param object object to putc
     * @throws IOException on error
     */
    @VisibleForTesting
    protected static void addMapParam(@NonNull ByteArrayOutputStream os, @NonNull Map<String, Object> object) throws IOException
    {
        // Param type
        os.write(ParamType.MAP.value);

        // Map is always 4x 0x00
        os.write(0);
        os.write(0);
        os.write(0);
        os.write(0);

        serializeValues(os, object);

        // End of Object marker
        os.write(0);
        os.write(0);
        os.write(ParamType.END_OF_OBJECT.value);
    }

    /**
     * Serialize the given map into the OutputStream
     *
     * @param os outputstream to serialize the map into
     * @param object the map to serialize
     * @throws IOException
     */
    private static void serializeValues(@NonNull ByteArrayOutputStream os, @NonNull Map<String, Object> object) throws IOException
    {
        for(String key: object.keySet())
        {
            Object value = object.get(key);

            byte[] keyBytes = key.getBytes();

            // Write key
            os.write((keyBytes.length >> 8) & 255);
            os.write(keyBytes.length & 255);

            for( byte byteValue : keyBytes )
            {
                os.write(byteValue);
            }

            // Write value
            if( value instanceof String )
            {
                addStringParam(os, (String) value);
            }
            else if( (value instanceof Integer) || (value instanceof Long) || (value instanceof Double) )
            {
                addNumberParam(os, (Number) value);
            }
            else if( (value instanceof Boolean) )
            {
                addBooleanParam(os, (boolean) value);
            }
            else if( (value instanceof Map) )
            {
                try
                {
                    //noinspection unchecked
                    addObjectParam(os, (Map<String, Object>) value);
                }
                catch (Exception e)
                {
                    throw new IOException(e);
                }
            }
            else
            {
                throw new IllegalArgumentException("Unknown object type: "+value.getClass().getName());
            }
        }
    }

// ------------------------------------------>

    /**
     * Read the next string
     *
     * @param offset offset to start reading at
     * @param buffer data
     * @return read string &amp; its length
     * @throws IOException if the next value is not a string or on error reading it
     */
    @NonNull
    public static Amf0Value<String> readString(int offset, @NonNull byte[] buffer) throws IOException
    {
        ParamType type = ParamType.fromValue(buffer[offset]);
        if( type != ParamType.STRING )
        {
            throw new IOException("Unable to read string, found "+type+" value");
        }

        int size = (int) RtmpReader.readNumber(2, offset + 1, buffer);

        byte[] stringBytes = new byte[size];
        System.arraycopy(buffer, offset + 3, stringBytes, 0, size);

        return new Amf0Value<>(new String(stringBytes, "UTF-8"), 3 + size);
    }

    /**
     * Read the next boolean value
     *
     * @param offset offset to start reading at
     * @param buffer data
     * @return boolean value and its size
     * @throws IOException if the next value is not a boolean or on error reading it
     */
    @NonNull
    public static Amf0Value<Boolean> readBoolean(int offset, @NonNull byte[] buffer) throws IOException
    {
        ParamType type = ParamType.fromValue(buffer[offset]);
        if( type != ParamType.BOOLEAN )
        {
            throw new IOException("Unable to read boolean, found "+type+" value");
        }

        return new Amf0Value<>(buffer[offset+1] != 0, 2);
    }

    /**
     * Read the next number value
     *
     * @param offset offset to start reading at
     * @param buffer data
     * @return int value and its size
     * @throws IOException if the next value is not a number or on error reading it
     */
    @NonNull
    public static Amf0Value<Double> readNumber(int offset, @NonNull byte[] buffer) throws IOException
    {
        ParamType type = ParamType.fromValue(buffer[offset]);
        if( type != ParamType.NUMBER )
        {
            throw new IOException("Unable to read number, found "+type+" value");
        }

        ByteBuffer bbuffer = ByteBuffer.wrap(buffer, offset+1, 8);

        return new Amf0Value<>(bbuffer.getDouble(), 9);
    }

    /**
     * Read the next null value
     *
     * @param offset offset to start reading at
     * @param buffer data
     * @return int int value and its size
     * @throws IOException if the next value is not a null or on error reading it
     */
    public static Amf0Value<AmfNull> readNull(int offset, @NonNull byte[] buffer) throws IOException
    {
        ParamType type = ParamType.fromValue(buffer[offset]);
        if( type != ParamType.NULL )
        {
            throw new IOException("Unable to read null, found "+type+" value");
        }

        return new Amf0Value<>(new AmfNull(), 1);
    }

    /**
     * Read the next object value
     *
     * @param offset offset to start reading at
     * @param buffer data
     * @return object value and its data
     * @throws IOException if the next value is not an object or on error reading it
     */
    @NonNull
    public static Amf0Value<Map<String, Object>> readObject(int offset, @NonNull byte[] buffer) throws IOException
    {
        ParamType type = ParamType.fromValue(buffer[offset]);
        if( type != ParamType.OBJECT )
        {
            throw new IOException("Unable to read object, found "+type+" value");
        }

        int length = 1;

        Map<String, Object> values = new ArrayMap<>();

        while( true )
        {
            // Read name of property
            int nameLength = (int) RtmpReader.readNumber(2, offset + length, buffer);

            if( nameLength > (buffer.length - (offset+length+2)) )
            {
                Log.e(TAG, "Error reading length of object param: found "+nameLength+" out of a "+buffer.length+" bytes long buffer, aborting");
                break;
            }

            byte[] nameBytes = new byte[nameLength];
            System.arraycopy(buffer, offset+length+2, nameBytes, 0, nameLength);

            String name = new String(nameBytes, "UTF-8");

            // Bump offset after property name
            length += 2+nameLength;

            ParamType paramType = ParamType.fromValue(buffer[offset+length]);

            if( paramType == ParamType.STRING )
            {
                Amf0Value<String> value = readString(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.NUMBER )
            {
                Amf0Value<Double> value = readNumber(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.BOOLEAN )
            {
                Amf0Value<Boolean> value = readBoolean(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.OBJECT )
            {
                Amf0Value<Map<String, Object>> value = readObject(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.MAP )
            {
                Amf0Value<Map<String, Object>> value = readMap(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.NULL )
            {
                Amf0Value<AmfNull> value = new Amf0Value<>(new AmfNull(), 1);
                values.put(name, value);

                length += 1;
            }
            else
            {
                throw new IOException("Unable to read data of type: "+type);
            }

            if( isEndOfObject(offset+length, buffer) )
            {
                length += 3;
                break;
            }
        }

        return new Amf0Value<>(values, length);
    }

    /**
     * Read the next map value
     *
     * @param offset offset to start reading at
     * @param buffer data
     * @return map value and its data
     * @throws IOException if the next value is not a map or on error reading it
     */
    @NonNull
    public static Amf0Value<Map<String, Object>> readMap(int offset, @NonNull byte[] buffer) throws IOException
    {
        ParamType type = ParamType.fromValue(buffer[offset]);
        if( type != ParamType.MAP )
        {
            throw new IOException("Unable to read map, found "+type+" value");
        }

        int length = 1;

        offset+= 4; // 4 cause a map is always starting with 4x 0x00

        Map<String, Object> values = new ArrayMap<>();

        while( true )
        {
            // Read name of property
            int nameLength = (int) RtmpReader.readNumber(2, offset + length, buffer);

            if( nameLength > (buffer.length - (offset+length+2)) )
            {
                Log.e(TAG, "Error reading length of map param: found "+nameLength+" out of a "+buffer.length+" bytes long buffer, aborting");
                break;
            }

            byte[] nameBytes = new byte[nameLength];
            System.arraycopy(buffer, offset+length+2, nameBytes, 0, nameLength);

            String name = new String(nameBytes, "UTF-8");

            // Bump offset after property name
            length += 2+nameLength;

            ParamType paramType = ParamType.fromValue(buffer[offset+length]);

            if( paramType == ParamType.STRING )
            {
                Amf0Value<String> value = readString(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.NUMBER )
            {
                Amf0Value<Double> value = readNumber(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.BOOLEAN )
            {
                Amf0Value<Boolean> value = readBoolean(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.OBJECT )
            {
                Amf0Value<Map<String, Object>> value = readObject(offset+length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.MAP )
            {
                Amf0Value<Map<String, Object>> value = readMap(offset + length, buffer);
                values.put(name, value.value);

                length += value.length;
            }
            else if( paramType == ParamType.NULL )
            {
                Amf0Value<AmfNull> value = new Amf0Value<>(new AmfNull(), 1);
                values.put(name, value);

                length += 1;
            }
            else
            {
                throw new IOException("Unable to read data of type: "+type);
            }

            if( isEndOfObject(offset+length, buffer) )
            {
                length += 3;
                break;
            }
        }

        return new Amf0Value<>(values, length);
    }

    /**
     * Are the next bytes a {@link ParamType#END_OF_OBJECT} marker
     *
     * @param offset offset to start reading at
     * @param buffer data
     * @return true if the next bytes are end of object, false otherwise
     */
    private static boolean isEndOfObject(int offset, @NonNull byte[] buffer)
    {
        int value = buffer[offset];

        if( value == 0 && buffer.length >= offset+1 )
        {
            int value2 = buffer[offset+1];
            int value3 = buffer[offset+2];

            if( value2 == 0 && value3 == ParamType.END_OF_OBJECT.value )
            {
                return true;
            }
        }

        return false;
    }

// ------------------------------------------>

    /**
     * Types of parameter for a function
     */
    public enum ParamType
    {
        /**
         * A number parameter
         */
        NUMBER(0),
        /**
         * A boolean parameter
         */
        BOOLEAN(1),
        /**
         * A String parameter
         */
        STRING(2),
        /**
         * An object parameter
         */
        OBJECT(3),
        /**
         * A Null parameter
         */
        NULL(5),
        /**
         * A Map (key/value) parameter (also known as ecma-array)
         */
        MAP(8),
        /**
         * End of object parameter
         */
        END_OF_OBJECT(9);

        final int value;

        ParamType(int value)
        {
            this.value = value;
        }

        @Nullable
        public static ParamType fromValue(int value)
        {
            for(ParamType type : values())
            {
                if( type.value == value )
                {
                    return type;
                }
            }

            return null;
        }
    }

}
