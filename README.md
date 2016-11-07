# RTMP Java Muxer for Android

This project implements the RTMP protocol to broadcast video and audio TO **(and only TO!)** an RTMP server from Android using pure Java (no native extension).

It has been tested with [Android MediaCodec](http://developer.android.com/reference/android/media/MediaCodec.html) encoder to send H264 (avc) video and with [Android MediaRecorder](http://developer.android.com/reference/android/media/MediaRecorder.html) to send AAC audio via RTMP to a server.

## RFCs

This implementation uses the RTMP 3 protocol version: [RTMP 3](https://www.adobe.com/content/dam/Adobe/en/devnet/rtmp/pdf/rtmp_specification_1.0.pdf)

It also uses the AMF0 file format: [AMF0](http://wwwimages.adobe.com/content/dam/Adobe/en/devnet/amf/pdf/amf0-file-format-specification.pdf)

## Android dependency

This muxer has been developed to be used on Android but it actually contains only 1 dependency to the Android platform: the [support-v4 library](http://developer.android.com/tools/support-library/index.html).

It's used for [annotations](http://tools.android.com/tech-docs/support-annotations) and also [ArrayMap](http://developer.android.com/reference/android/support/v4/util/ArrayMap.html) but if you want to use this library outside of Android you can remove the dependency quite easily.

Another dependency are logs which are made with the [Log](http://developer.android.com/reference/android/util/Log.html) util of Android, but it's also easily replaceable.

## Contributions

This code is a small part of an internal project and is available "as is" without any guarantee. We are not providing implementation details on how to extract H264 video and AAC audio frames on purpose, this implementation is up to you.

If you want to contribute, feel free to ask question or make pull requests, we'll be happy to review them :)

## How to use

### Runtime

To use the Muxer, you must respect the following runtime:

1- Start streaming by calling the `start` and `createStream` method

```java
public class MainActivity extends Activity implements RtmpMuxer.RtmpConnectionListener
{
    private RtmpMuxer muxer;

    private void initMuxer()
    {
        muxer = new RtmpMuxer(host, port, new Time()
        {
            @Override
            public long getCurrentTimestamp()
            {
                return System.currentTimeMillis();
            }
        });

        // Always call start method from a background thread.
        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                muxer.start(this, "app", null, null);
                return null;
            }
        }.execute();
    }

    @Override
    public void onConnected()
    {
        // Muxer is connected to the RTMP server, you can create a stream to publish data
        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                muxer.createStream("playPath");
                return null;
            }
        }.execute();
    }

    @Override
    public void onReadyToPublish()
    {
        // Muxer is connected to the server and ready to receive data
    }

    @Override
    public void onConnectionError(IOException e)
    {
        // Error while connecting to the server
    }
} 
```

> Note that this sample uses an AsyncTask to demonstrate threading but you should use another most efficient threading method into your app.

2- Send data to the Muxer using `postVideo` and `postAudio` (see next part for more info)

3- Stop streaming by calling the `deleteStream` method

4- Disconnect by calling the `stop` method

### Sample of video posting with MediaCodec API

Once the RtmpMuxer is ready to publish, you can provide it with video data. Those data can be extracted from the MediaCodec encoder (other ways asn't been tested). Here's how to create an `H264VideoFrame` object from the MediaCodec buffer (extracted from the [Grafika](https://github.com/google/grafika/blob/master/src/com/android/grafika/VideoEncoderCore.java) sample):

```java
public class VideoRecordingActivity extends Activity
{
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;

    private RtmpMuxer muxer; // Already started muxer

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer. 
     */
    public void drainEncoder() 
    {
        final int TIMEOUT_USEC = 10000;

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) 
        {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

            if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) 
            {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } 
            else 
            {
                if (mBufferInfo.size != 0) 
                {
                    final boolean isHeader = (mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;

                    final boolean isKeyframe = (mBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 && !isHeader;

                    final long timestamp = mBufferInfo.presentationTimeUs;

                    final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    // Extract video data
                    final byte[] b = new byte[mBufferInfo.size];
                    encodedData.get(b);

                    // Always call postVideo method from a background thread.
                    new AsyncTask<Void, Void, Void>()
                    {
                        @Override
                        protected Void doInBackground(Void... params)
                        {
                            try
                            {
                                muxer.postVideo(new H264VideoFrame()
                                {
                                    @Override
                                    public boolean isHeader()
                                    {
                                        return isHeader;
                                    }

                                    @Override
                                    public long getTimestamp()
                                    {
                                        return timestamp;
                                    }

                                    @NonNull
                                    @Override
                                    public byte[] getData()
                                    {
                                        return b;
                                    }

                                    @Override
                                    public boolean isKeyframe()
                                    {
                                        return isKeyframe;
                                    }
                                });
                            }
                            catch(IOException e)
                            {
                                // An error occured while sending the video frame to the server
                            }
                            
                            return null;
                        }
                    }.execute();
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) 
                {
                    break;      // out of while
                }
            }
        }
    }
}
```

> Note that this sample is incomplete and you'll have to manage encoder lifecycle. Also, avoid using AsyncTask for posting data since it will happen multiple times per seconds, it's done here just as a sample.

### Sample of audio posting with MediaRecorder API

Once the RtmpMuxer is ready to publish, you can also provide it with audio data. Those data can be extracted from the MediaRecorder (other ways asn't been tested). Here's how to create configure MediaRecorder to generate the right stream:

```java
public class AudioRecordingActivity extends Activity implements Runnable
{
    /**
     * Instance of media recorder used to record audio
     */
    private MediaRecorder mediaRecorder;
    /**
     * File descriptors used to extract data from the {@link #mediaRecorder}
     */
    private ParcelFileDescriptor[] fileDescriptors;
    /**
     * Thread that will handle aac parsing using {@link #fileDescriptors} output
     */
    private Thread aacParsingThread;
    /**
     * Has AAC header been send yet
     */
    private boolean headerSent;
    /**
     * Already started muxer.
     */
    private RtmpMuxer muxer;

    public void configure() throws IOException
    {
        fileDescriptors = ParcelFileDescriptor.createPipe();
        aacParsingThread = new Thread(this);
        headerSent = false;

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER); // If you want to use the camera's microphone
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(fileDescriptors[1].getFileDescriptor());
        mediaRecorder.prepare();
    }

    public void startAudio()
    {
        mediaRecorder.start();
        aacParsingThread.start();
    }

    @Override
    public void run()
    {
        FileInputStream is = null;
        try
        {
            is = new FileInputStream(fileDescriptors[0].getFileDescriptor());
            
            while (true)
            {
                // TODO parse AAC: This sample doesn't provide AAC extracting complete method since it's not the purpose of this repository. 

                if( !headerSent )
                {
                    // TODO extract header data
                    byte[] aacHeader;
                    int numberOfChannel;
                    int sampleSizeIndex;

                    muxer.setAudioHeader(new AACAudioHeader()
                    {
                        @NonNull
                        @Override
                        public byte[] getData()
                        {
                            return aacHeader;
                        }

                        @Override
                        public int getNumberOfChannels()
                        {
                            return numberOfChannel;
                        }

                        @Override
                        public int getSampleSizeIndex()
                        {
                            return sampleSizeIndex;
                        }
                    });

                    headerSent = true;
                }
            
                // TODO extract frame data

                final byte[] aacData;
                final long timestamp;

                // Don't call postAudio from the extracting thread.
                new AsyncTask<Void, Void, Void>()
                {
                    @Override
                    protected Void doInBackground(Void... params)
                    {
                        try
                        {
                            muxer.postAudio(new AACAudioFrame()
                            {
                                @Override
                                public long getTimestamp()
                                {
                                    return timestamp;
                                }

                                @NonNull
                                @Override
                                public byte[] getData()
                                {
                                    return aacData;
                                }
                            });
                        }
                        catch(IOException e)
                        {
                            // An error occured while sending the audio frame to the server
                        }
                        
                        return null;
                    }
                }.execute();
            }
        }
        catch (Exception e)
        {
            // TODO handle error
        }
        finally
        {
            if( is != null )
            {
                try
                {
                    is.close();
                }
                catch (Exception ignored){}
            }
        }
    }
}
```

> Note that this sample is incomplete and you'll have to manage thread completion, AAC parsing and data extracting. Also, avoid using AsyncTask for posting data since it will happen multiple times per seconds, it's done here just as a sample.

## Authors

- [Benoit Letondor](https://github.com/benoitletondor): Android software engineer at Newzulu/Octiplex.

## Licence

Code is available under the Revised BSD License, see [LICENCE](LICENCE) for more info.

    Copyright (c) 2016 Octiplex.
    All rights reserved.

    Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    * Neither the names of Octiplex and Newzulu nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
