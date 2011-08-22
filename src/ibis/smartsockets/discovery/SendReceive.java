package ibis.smartsockets.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendReceive {

    private static final Logger logger =
        LoggerFactory.getLogger("ibis.smartsockets.discovery");

    private final DatagramSocket socket;

    private final DatagramPacket receivepacket;

    private DatagramPacket sendpacket;

    protected SendReceive(int port) throws Exception {

        if (port == 0) {
            socket = new DatagramSocket();
        } else {
            socket = new DatagramSocket(port);
        }

        receivepacket = new DatagramPacket(new byte[64*1024], 64*1024);
    }

    protected void setMessage(String message, int destport) throws SocketException {

        byte [] data = message.getBytes();

        if (data.length > 1024) {
            throw new IllegalArgumentException("Message exceeds 1024 bytes!");
        }

        byte [] tmp = new byte[data.length+8];

        Discovery.write(tmp, 0, Discovery.MAGIC);
        Discovery.write(tmp, 4, data.length);

        System.arraycopy(data, 0, tmp, 8, data.length);

        InetSocketAddress target = new InetSocketAddress("255.255.255.255", destport);
        sendpacket = new DatagramPacket(tmp, tmp.length, target);
    }

    public void send(int timeout) {

        if (sendpacket == null) {
            return;
        }

        try {
            if (logger.isInfoEnabled()) {
                logger.info("MulticastSender sending data to "
                        + sendpacket.getSocketAddress());
            }

            socket.setSoTimeout(timeout);
            socket.send(sendpacket);
        } catch (Exception e) {
            if (logger.isInfoEnabled()) {
                logger.info("MulticastSender got exception ", e);
            }
        }
    }

    public String receive(int timeout) throws IOException {

        long end = System.currentTimeMillis() + timeout;
        long left = timeout;

        if (timeout == 0) {
            socket.setSoTimeout(timeout);
        }

        while (timeout == 0 || left > 0) {

            if (logger.isInfoEnabled()) {
                logger.info("Receiver waiting for data");
            }

            if (timeout > 0 && left > 0) {
                socket.setSoTimeout((int)left);
            }

            socket.receive(receivepacket);

            byte [] tmp = receivepacket.getData();

            if (tmp.length > 8) {
                if (Discovery.read(tmp, 0) != Discovery.MAGIC) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Discarding packet, wrong MAGIC");
                    }
                } else {
                    int len = Discovery.read(tmp, 4);

                    if (logger.isInfoEnabled()) {
                        logger.info("MAGIC OK, data length = " + len);
                    }

                    if (len > 1024) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Discarding packet, wrong size");
                        }
                    } else {
                        byte [] data = new byte[len];
                        System.arraycopy(tmp, 8, data, 0, len);
                        return new String(data);
                    }
                }
            }

            if (timeout > 0) {
                left = end - System.currentTimeMillis();
            }
        }

        throw new SocketTimeoutException();
    }
}
