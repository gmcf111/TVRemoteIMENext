package com.cgutman.adblib;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class AdbRegressionTest {
    @Test public void unicodeDestinationUsesUtf8ByteLength() throws Exception {
        String destination = "shell:input text '中文'";
        AdbProtocol.AdbMessage message = AdbProtocol.AdbMessage.parseAdbMessage(
            new ByteArrayInputStream(AdbProtocol.generateOpen(1, destination)));
        assertEquals(destination + "\0", new String(message.payload, StandardCharsets.UTF_8));
        assertTrue(AdbProtocol.validateMessage(message));
    }

    @Test public void bufferedPayloadSurvivesRemoteClose() throws Exception {
        AdbStream stream = new AdbStream(null, 1);
        stream.addPayload("last output".getBytes(StandardCharsets.UTF_8));
        stream.notifyClose();
        assertEquals("last output", new String(stream.read(), StandardCharsets.UTF_8));
    }

    @Test(timeout = 5000) public void closeListenerCanCloseItsOwnConnection() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            AdbConnection connection = AdbConnection.create(client, null);
            CountDownLatch closed = new CountDownLatch(1);
            connection.setOnClosedListener(() -> {
                try { connection.close(); } catch (Exception ignored) { }
                closed.countDown();
            });
            peer.getOutputStream().write(AdbProtocol.generateConnect());
            peer.getOutputStream().flush();
            connection.connect();
            peer.shutdownOutput();
            assertTrue("Connection thread deadlocked in close listener", closed.await(2, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 5000) public void fastCommandCanCloseBeforeOpenReturns() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            AdbConnection connection = AdbConnection.create(client, null);
            peer.getOutputStream().write(AdbProtocol.generateConnect());
            peer.getOutputStream().flush();
            connection.connect();
            Thread responder = new Thread(() -> {
                try {
                    AdbProtocol.AdbMessage message;
                    do { message = AdbProtocol.AdbMessage.parseAdbMessage(peer.getInputStream()); }
                    while (message.command != AdbProtocol.CMD_OPEN);
                    java.io.ByteArrayOutputStream packets = new java.io.ByteArrayOutputStream();
                    packets.write(AdbProtocol.generateReady(99, message.arg0));
                    packets.write(AdbProtocol.generateWrite(99, message.arg0, "done".getBytes(StandardCharsets.UTF_8)));
                    packets.write(AdbProtocol.generateClose(99, message.arg0));
                    peer.getOutputStream().write(packets.toByteArray());
                    peer.getOutputStream().flush();
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            responder.start();
            try (AdbStream stream = connection.open("shell:echo done")) {
                responder.join();
                assertEquals("done", new String(stream.read(), StandardCharsets.UTF_8));
            } finally { connection.close(); }
        }
    }
}
