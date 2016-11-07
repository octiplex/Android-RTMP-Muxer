package com.octiplex.android.rtmp.protocol;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests around Amf0 functions & serialization/deserialization
 *
 * @author Benoit LETONDOR
 */
public class Amf0FunctionsTest
{
    /**
     * Tests that AMF0 strings are written and read correctly
     *
     * @throws Exception
     */
    @Test
    public void stringParsingTest() throws Exception
    {
        String originalString = "гостепримныйAabB123654789&é'(§è!çà)^¨*$€%ù£`+=:/;.,?<>";
        int expectedLength = originalString.getBytes().length + 3;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf0Functions.addStringParam(os, originalString);

        assertEquals(expectedLength, os.size());

        Amf0Value<String> value = Amf0Functions.readString(0, os.toByteArray());
        assertEquals(expectedLength, value.length);
        assertEquals(originalString, value.value);
    }

    /**
     * Tests that AMF0 booleans are written and read correctly
     *
     * @throws Exception
     */
    @Test
    public void booleanParsingTest() throws Exception
    {
        int expectedLength = 2;

        // Positive
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf0Functions.addBooleanParam(os, true);

        assertEquals(expectedLength, os.size());

        Amf0Value<Boolean> value = Amf0Functions.readBoolean(0, os.toByteArray());
        assertEquals(expectedLength, value.length);
        assertEquals(true, value.value);

        // Negative
        os.reset();
        Amf0Functions.addBooleanParam(os, false);

        assertEquals(expectedLength, os.size());

        value = Amf0Functions.readBoolean(0, os.toByteArray());
        assertEquals(expectedLength, value.length);
        assertEquals(false, value.value);
    }

    /**
     * Tests that AMF0 numbers are written and read correctly
     *
     * @throws Exception
     */
    @Test
    public void numberParsingTest() throws Exception
    {
        double doubleValue = 30382947.15;
        int expectedLength = 9;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf0Functions.addNumberParam(os, doubleValue);

        assertEquals(expectedLength, os.size());

        Amf0Value<Double> value = Amf0Functions.readNumber(0, os.toByteArray());
        assertEquals(expectedLength, value.length);
        assertEquals(doubleValue, value.value, 1e-15);
    }

    /**
     * Tests that AMF0 objects are written and read correctly
     *
     * @throws Exception
     */
    @Test
    public void objectParsingTest() throws Exception
    {
        Map<String, Object> value = new HashMap<>();
        value.put("qoidhsdoihsdf", "oisdfhsfio");
        value.put("oihsoi", true);
        value.put("oisdhfsdoifh", false);
        value.put("dsoihsdiofs", 39490.02);

        Map<String, Object> subValue = new HashMap<>();
        subValue.putAll(value);

        value.put("sdofisdfoi", subValue);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf0Functions.addObjectParam(os, value);

        Amf0Value<Map<String, Object>> objectValue = Amf0Functions.readObject(0, os.toByteArray());
        assertEquals(value, objectValue.value);
    }

    /**
     * Tests that AMF0 map are written and read correctly
     *
     * @throws Exception
     */
    @Test
    public void mapParsingTest() throws Exception
    {
        Map<String, Object> value = new HashMap<>();
        value.put("qoidhsdoihsdf", "oisdfhsfio");
        value.put("oihsoi", true);
        value.put("oisdhfsdoifh", false);
        value.put("dsoihsdiofs", 39490.02);

        Map<String, Object> subValue = new HashMap<>();
        subValue.putAll(value);

        value.put("sdofisdfoi", subValue);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf0Functions.addMapParam(os, value);

        Amf0Value<Map<String, Object>> objectValue = Amf0Functions.readMap(0, os.toByteArray());
        assertEquals(value, objectValue.value);
    }

    /**
     * Tests that AMF0 nulls are written and read correctly
     *
     * @throws Exception
     */
    @Test
    public void nullParsingTest() throws Exception
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Amf0Functions.addNullParam(os);

        assertEquals(1, os.size());

        Amf0Value<AmfNull> value = Amf0Functions.readNull(0, os.toByteArray());
        assertEquals(1, value.length);
        assertEquals(new AmfNull(), value.value);
    }
}
