package com.octiplex.android.rtmp.io;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests that {@link IOTimeoutAction} is working as intended
 *
 * @author Benoit LETONDOR
 */
public class IOTimeoutActionTest
{
    /**
     * Test that the timeout is not reached when condition is ok
     *
     * @throws Exception
     */
    @Test
    public void testSuccess() throws Exception
    {
        final long initialTS = System.currentTimeMillis();

        IOTimeoutAction action = new IOTimeoutAction(500)
        {
            @Override
            public boolean condition() throws IOException
            {
                return System.currentTimeMillis() - initialTS > 300;
            }
        };

        try
        {
            action.execute();
        }
        catch (IOException e)
        {
            fail();
        }
    }

    /**
     * Test that the timeout is reached when condition is not ok
     *
     * @throws Exception
     */
    @Test
    public void testFail() throws Exception
    {
        final long initialTS = System.currentTimeMillis();

        IOTimeoutAction action = new IOTimeoutAction(300)
        {
            @Override
            public boolean condition() throws IOException
            {
                return System.currentTimeMillis() - initialTS > 500;
            }
        };

        try
        {
            action.execute();
            fail();
        }
        catch (IOException e)
        {
            // Ok
        }
    }
}