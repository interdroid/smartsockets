/**
 *
 */
package test.virtual.throughput.mtnio;

import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

class Sender extends Thread {

    private final DataSource d;
    private final VirtualSocket s;
    private final SocketChannel channel;
    private final ByteBuffer buffer;
    private final ByteBuffer opcode;

    private final ByteBuffer [] message;

    private final int size ;

    Sender(DataSource d, VirtualSocket s, DataOutputStream out,
            DataInputStream in, int size) {

        this.d = d;
        this.s = s;
        this.size = size;

        channel = s.getChannel();

        opcode= ByteBuffer.allocateDirect(4);
        opcode.clear();

        buffer = ByteBuffer.allocateDirect(size);
        buffer.put(new byte[size]);
        buffer.clear();

        message = new ByteBuffer[2];
        message[0] = opcode;
        message[1] = buffer;
    }

    private void sendOpcode(int value) {
 //       System.out.println("Writing opcode " + value);

        opcode.putInt(value);
        opcode.flip();

        try {
            int written = 0;

            while (written < 4) {
                written += channel.write(opcode);
            }
        } catch (Exception e) {
            throw new Error("Failed to write opcode!", e);
        }

        opcode.clear();
    }

    private void sendData() {

        //System.out.println("Sending data!");

        // long time = System.currentTimeMillis();

        // int count = 0;
        int block = d.getBlock();

        while (block != -1) {
            // count++;

            try {
                opcode.putInt(block);
                opcode.flip();

                int written = 0;

                while (written < (size + 4)) {
                    written += channel.write(message);
                }

                opcode.clear();
                buffer.position(0);
            } catch (Exception e) {
                throw new Error("Failed to write data!", e);
            }

            block = d.getBlock();
        }

        sendOpcode(-1);

        // time = System.currentTimeMillis() - time;

        // TODO: do something with stats!
    }

    public void run() {

        boolean done = d.waitForStartOrDone();

        while (!done) {
            sendData();
            done = d.waitForStartOrDone();
        }

        sendOpcode(-2);

        VirtualSocketFactory.close(s, channel);
    }
}
