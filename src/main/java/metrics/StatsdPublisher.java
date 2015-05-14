/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package metrics;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the configuration of StatsD connection
 *
 */
public class StatsdPublisher {

    private ByteBuffer sendBuffer;

    private static final Logger log = LogManager.getLogger(StatsdPublisher.class.getName());

    private final InetSocketAddress _address;
    private final DatagramChannel _channel;

    public StatsdPublisher(String host, int port) throws UnknownHostException, IOException {
        this(InetAddress.getByName(host), port);
    }

    public StatsdPublisher(InetAddress host, int port) throws IOException {
        _address = new InetSocketAddress(host, port);
        _channel = DatagramChannel.open();
        setBufferSize((short) 1500);
    }

    public synchronized void setBufferSize(short packetBufferSize) {
        if (sendBuffer != null) {
            flush();
        }
        sendBuffer = ByteBuffer.allocate(packetBufferSize);
    }

    public synchronized boolean send(String stat) {
        try {
            final byte[] data = stat.getBytes("utf-8");

            sendBuffer.put(data); // append the data
            flush();

            return true;

        } catch (IOException e) {
            log.error(String.format("Could not send stat %s to host %s:%d", sendBuffer.toString(), _address.getHostName(),
                    _address.getPort()), e);
            return false;
        }
    }

    private synchronized boolean flush() {
        try {
            final int sizeOfBuffer = sendBuffer.position();

            if (sizeOfBuffer <= 0) {
                return false;
            } // empty buffer

            // send and reset the buffer
            sendBuffer.flip();
            final int nbSentBytes = _channel.send(sendBuffer, _address);
            sendBuffer.limit(sendBuffer.capacity());
            sendBuffer.rewind();

            if (sizeOfBuffer == nbSentBytes) {
                return true;
            } else {
                log.error(String.format("Could not send entirely stat %s to host %s:%d. Only sent %d bytes out of %d bytes",
                        sendBuffer.toString(), _address.getHostName(), _address.getPort(), nbSentBytes, sizeOfBuffer));
                return false;
            }

        } catch (IOException e) {
            log.error(String.format("Could not send stat %s to host %s:%d", sendBuffer.toString(), _address.getHostName(),
                    _address.getPort()), e);
            return false;
        }
    }
}