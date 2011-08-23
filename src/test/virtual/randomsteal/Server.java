package test.virtual.randomsteal;

import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;

public class Server {

    public final static byte OPCODE_START = 42;
    public final static byte OPCODE_SYNC  = 43;
    public final static byte OPCODE_DONE  = 44;

    private final int numberOfClients;
    private final int count;
    private final int repeat;

    private final VirtualSocketFactory sf;

    private final VirtualServerSocket ss;

    private final LinkedList<Client> clients = new LinkedList<Client>();

    private class Client {

        final VirtualSocket s;
        final DataInputStream in;
        final DataOutputStream out;
        final VirtualSocketAddress address;

        Client(VirtualSocket s) throws IOException {
            this.s = s;
            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            s.setSoTimeout(5000);
            s.setTcpNoDelay(true);

            address = new VirtualSocketAddress(in);
        }

        void writeParameters(int timeout) throws IOException {

            s.setSoTimeout(timeout);

            out.writeInt(count);
            out.writeInt(clients.size()-1);

            for (Client c : clients) {

                if (c != this) {
                    c.address.write(out);
                }
            }

            out.flush();
        }

        void start(int timeout) throws IOException {
            s.setSoTimeout(timeout);
            out.writeByte(OPCODE_START);
            out.flush();
        }

        void done(int timeout) throws IOException {
            s.setSoTimeout(timeout);
            out.writeByte(OPCODE_DONE);
            out.flush();

            VirtualSocketFactory.close(s, out, in);
        }

        int sync() throws IOException {
            s.setSoTimeout(0);

            if (in.readByte() != OPCODE_SYNC) {
                System.err.println("Client " + address + " returned junk!");
                return 0;
            }

            return in.readInt();
        }
    }

    public Server(int numberOfClients, int count, int repeat) throws InitializationException, IOException {
        this.numberOfClients = numberOfClients;
        this.count = count;
        this.repeat = repeat;

        sf = VirtualSocketFactory.createSocketFactory();
        ss = sf.createServerSocket(0, 0, null);

        System.out.println("Created server on " + ss.getLocalSocketAddress());
    }

    public void start() {

        try {

            while (clients.size() < numberOfClients) {

                System.out.println("Server waiting for connections");

                Client tmp = new Client(ss.accept());

                System.out.println("Got connection from " + tmp.address);

                clients.add(tmp);
            }

            for (Client c : clients) {
                c.writeParameters(5000);
            }

            for (int r=0;r<repeat;r++) {

                for (Client c : clients) {
                    c.start(5000);
                }

                int totalTime = 0;

                for (Client c : clients) {
                    totalTime += c.sync();
                }

                double avg = ((double) totalTime) / (count * numberOfClients);

                System.err.println("Avg. connect time: " + avg + " ms.");

            }

            for (Client c : clients) {
                c.done(5000);
            }

        } catch (Exception e) {
            System.err.println("Server got exception " + e);
            e.printStackTrace(System.err);
        }
    }
}

