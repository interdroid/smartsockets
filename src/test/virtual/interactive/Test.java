package test.virtual.interactive;

import ibis.smartsockets.hub.servicelink.ClientInfo;
import ibis.smartsockets.hub.servicelink.HubInfo;
import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.StringTokenizer;

public final class Test extends Thread {

    private static int TIMEOUT = 10000;

    private static boolean interactive = false;

    private class Connection extends Thread {

        private final int number;

        private VirtualSocket socket;
        private DataInputStream in;
        private DataOutputStream out;

        private boolean done = false;

        private int ping = -1;
        private long pingTime = -1;
        private boolean pingOK = true;

        Connection(int number, VirtualSocket socket) throws IOException {
            this.number = number;
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        }

        public synchronized void done() {
            this.done = true;
        }

        private synchronized boolean getDone() {
            return done;
        }

        public synchronized long ping(int count) {

            while (ping != -1) {
                try {
                    wait();
                } catch (Exception e) {
                    // ignore
                }
            }

            ping = count;

            notifyAll();

            while (pingTime == -1) {
                try {
                    wait();
                } catch (Exception e) {
                    // ignore
                }
            }

            long time = pingTime;

            if (!pingOK) {
                System.out.println("WARNING: Ping was interrupted by other traffic!");
            }

            // reset the lot....
            ping = -1;
            pingTime =-1;
            pingOK = true;

            notifyAll();

            return time;
        }

        private int getPing() {
            return ping;
        }

        public String toString() {
            return socket.getRemoteSocketAddress().toString();
        }

        private void handleMessage(int opcode) throws IOException {

            switch (opcode) {
            case -1:
            case 0:
                // (un)expected close
                done();
                break;

            case 1:
                // message
                gotMessage("[" + number + "] " + in.readUTF());
                break;

            case 2:
                // data
                int size = in.readInt();
                int chunks = in.readInt();

                byte [] data = new byte[size];

                long start = System.currentTimeMillis();

                for (int i=0;i<chunks;i++) {
                    in.readFully(data);
                }

                long end = System.currentTimeMillis();

                gotMessage("[" + number + "] Received " + chunks + " chunks of " + size
                        + "bytes in " + (end-start) + " ms. ("
                        + (((8L*size*chunks)/(1024.0*1024.0)) / ((end-start)/1000.0))
                        + " MBit/sec)");

                break;

            case 3:
                // ping
                synchronized (this) {
                    out.write(4);
                    out.flush();
                }
                break;

            case 4:
                // ping ack
                break;

            default:
                done();
                gotMessage("Receive junk on Connection " + number);
            }
        }

        private synchronized void doPing(int count) throws IOException {

            long start = System.currentTimeMillis();

            for (int i=0;i<count;i++) {

                out.write(3);
                out.flush();

                int opcode = in.read();

                if (opcode != 4) {
                    pingOK = false;
                    handleMessage(opcode);
                }
            }

            long end = System.currentTimeMillis();

            pingTime = (end-start);
            notifyAll();
        }

        public void run() {

            try {
                socket.setSoTimeout(1000);
            } catch (Exception e) {
                gotMessage("Failed to start connection " + number);
                VirtualSocketFactory.close(socket, out, in);
                closed(this);
                return;
            }

            boolean done = getDone();

            while (!done) {

                try {
                    int ping = getPing();

                    if (ping > 0) {
                        doPing(ping);
                    } else {
                        int opcode = in.read();
                        handleMessage(opcode);
                    }
                } catch (SocketTimeoutException e) {
                    // ignored
                } catch (Exception e) {
                    done();
                    gotMessage("Got exception on Connection " + number + ": "
                            + e);
                    e.printStackTrace(System.err);
                }

                done = getDone();
            }

            VirtualSocketFactory.close(socket, out, in);
            closed(this);
        }

        public synchronized void sendMessage(String message) {

            try {
                out.write(1);
                out.writeUTF(message);
                out.flush();
            } catch (Exception e) {
                System.out.println("Got exception on connection " + number
                        + " while writing message " + e);
                VirtualSocketFactory.close(socket, out, in);
                closed(this);
            }
        }

        public synchronized void sendDataMessage(int size, int chunks) {

            byte [] data = new byte[size];

            try {

                long start = System.currentTimeMillis();

                out.write(2);
                out.writeInt(size);
                out.writeInt(chunks);

                for (int i=0;i<chunks;i++) {
                    out.write(data);
                }

                out.flush();

                long end = System.currentTimeMillis();

                System.out.println("Send " + chunks + " chunks of " + size
                        + "bytes in " + (end-start) + " ms. ("
                        + (((8L*size*chunks)/(1024.0*1024.0)) / ((end-start)/1000.0))
                        + " MBit/sec)");

            } catch (Exception e) {

                System.out.println("Got exception on connection " + number
                        + " while writing message " + e);

                VirtualSocketFactory.close(socket, out, in);
                closed(this);
            }
        }

        public void close() {

            try {
                out.write(0);
                out.flush();
            } catch (Exception e) {
                // ignore
            }

            VirtualSocketFactory.close(socket, out, in);
        }

    }

    private static class Acceptor extends Thread {

        private final VirtualServerSocket ss;

        Acceptor(VirtualServerSocket ss) {
            this.ss = ss;
        }

        public void run() {

            while (true) {
                try {
                    VirtualSocket s = ss.accept();
                    s.close();
                } catch (Exception e) {
                    System.out.println("Acceptor got exception!");
                    return;
                }
            }
        }
    }

    private VirtualSocketFactory sf;

    private HashMap<String, Object> connectProperties;

    private ArrayList<Connection> connections = new ArrayList<Connection>();

    private LinkedList<String> messages = new LinkedList<String>();

    private HubInfo [] hubInfo;

    private ClientInfo [] clientInfo;

    private VirtualServerSocket normal;
    private VirtualServerSocket connectTest;

    private Acceptor acceptor;

    private Test(int port) throws IOException {

        connectProperties = new HashMap<String, Object>();

        try {
            sf = VirtualSocketFactory.createSocketFactory();
        } catch (InitializationException e1) {
            throw new IOException("Failed to create socketfactory!");
        }

        normal = sf.createServerSocket(port, 0, connectProperties);
        connectTest = sf.createServerSocket(port+1, 0, connectProperties);

        System.out.println("Created server socket on "
                + normal.getLocalSocketAddress());

        System.out.println("Created connection test server socket on "
                + connectTest.getLocalSocketAddress());

        sf.getServiceLink().registerProperty("Test",
                normal.getLocalSocketAddress().toString());

        sf.getServiceLink().registerProperty("Repeat",
                connectTest.getLocalSocketAddress().toString());

        acceptor = new Acceptor(connectTest);
        acceptor.start();
    }

    private void closed(Connection c) {
        gotMessage("Connection " + c.number + " closed");
        connections.set(c.number, null);
    }

    private synchronized void printMessages() {

        for (String m : messages) {
            System.out.println(m);
        }

        messages.clear();
    }

    private void gotMessage(String message) {

        if (interactive) {
            messages.add(message);
        } else {
            System.out.println(message);
        }
    }

    private void usage() {

    }

    private int parseTargetHub(String s) {
        int target = -1;

        try {
            target = Integer.parseInt(s);
        } catch (Exception e) {
            System.out.println("Failed to parse target: " + s);
            return -1;
        }

        if (hubInfo == null) {
            System.out.println("No hub info available!");
            return -1;
        }

        if (target < 0 || target >= hubInfo.length) {
            System.out.println("Cannot list clients for hub [" + target + "]: "
                    + " hub does not exist!");
            return -1;
        }

        return target;
    }

    private synchronized int parseTargetConnection(String s) {
        int target = -1;

        try {
            target = Integer.parseInt(s);
        } catch (Exception e) {
            System.out.println("Failed to parse target: " + s);
            return -1;
        }

        if (target < 0 || target >= connections.size()) {
            System.out.println("Cannot connect to [" + target + "]: "
                    + " connection does not exist!");
            return -1;
        }

        return target;
    }

    private int parseTargetClient(String s) {
        int target = -1;

        try {
            target = Integer.parseInt(s);
        } catch (Exception e) {
            System.out.println("Failed to parse target: " + s);
            return -1;
        }

        if (clientInfo == null) {
            System.out.println("No client info available yet!");
            return -1;
        }

        if (target < 0 || target >= clientInfo.length) {
            System.out.println("Cannot connect to [" + target + "]: "
                    + " client does not exist!");
            return -1;
        }

        return target;
    }


    private void sendData(String s) {

        int target = -1;

        StringTokenizer st = new StringTokenizer(s, " ");

        if (st.countTokens() != 3) {
            System.out.println("sending data requires parameters: " +
                    "target size chunks");
            return;
        }

        target = parseTargetConnection(st.nextToken());

        if (target == -1) {
            // error already given!
            return;
        }

        try {
            int size = Integer.parseInt(st.nextToken());
            int chunks = Integer.parseInt(st.nextToken());

            Connection c = connections.get(target);

            if (c == null) {
                System.out.println("No connection to " + target);
                return;
            }

            c.sendDataMessage(size, chunks);

        } catch (Exception e) {
            System.out.println("sending data requires parameters: " +
            "target size chunks");
        }
    }

    private void send(String s) {

        if (s.length() == 0) {
            System.out.println("send requires parameters: target <message>");
            return;
        }

        if (s.startsWith("data ")) {
            sendData(s.substring(5).trim());
            return;
        }

        int index = s.indexOf(' ');

        int target = -1;

        if (index == -1) {
            // apparently it is an empty message
            target = parseTargetConnection(s);
            s = "";
        } else {
            target = parseTargetConnection(s.substring(0, index));
            s = s.substring(index).trim();
        }

        if (target == -1) {
            // error already given!
            return;
        }

        Connection c = connections.get(target);

        if (c == null) {
            System.out.println("No connection to " + target);
            return;
        }

        c.sendMessage(s);
    }

    private void ping(String s) {

        StringTokenizer st = new StringTokenizer(s, " ");

        if (st.countTokens() != 2) {
            System.out.println("ping requires parameters: target <repeat>");
            return;
        }

        int target = parseTargetConnection(st.nextToken());

        if (target == -1) {
            // error already given!
            return;
        }

        int repeat = -1;

        try {
            repeat = Integer.parseInt(st.nextToken());
        } catch (Exception e) {
            System.out.println("ping requires parameters: target <repeat>");
            return;
        }

        Connection c = connections.get(target);

        if (c == null) {
            System.out.println("No connection to " + target);
            return;
        }

        long time = c.ping(repeat);

        System.out.println("Ping took: " + time + " ms. (rtt. "
                + (time/repeat) + ")");

    }

    private synchronized void connections() {

        int i = 0;

        for (Connection c : connections) {
            if (c != null) {
                System.out.println("[" + i + "]: " + c);
            }

            i++;
        }
    }

    private void listhubs() throws IOException {

        hubInfo = sf.getServiceLink().hubDetails();

        int i = 0;

        for (HubInfo info : hubInfo) {
            System.out.println("[" + i++ + "] " + info.name + " ("
                    + info.clients + " clients)");
        }
    }

    private void listclients(String s) throws IOException {

        int target = parseTargetHub(s);

        if (target == -1) {
            // error is already printed!
            return;
        }

        HubInfo info = hubInfo[target];

        clientInfo = sf.getServiceLink().clients(info.hubAddress);

        int i = 0;

        for (ClientInfo c : clientInfo) {
            System.out.println("[" + i++ + "] " + c);
        }
    }

    private synchronized void exit() {

        for (Connection c : connections) {

            if (c != null) {
                c.close();
            }
        }

        System.exit(0);
    }

    private void connect(String s) {

        int target = parseTargetClient(s);

        if (target == -1) {
            // error is already printed!
            return;
        }

        ClientInfo info = clientInfo[target];

        if (!info.hasProperty("Test")) {
            System.out.println("Cannot connect to client " + target
                    + " since it doesn't export a \"Test\" port");
            return;
        }

        try {
            VirtualSocketAddress a =
                new VirtualSocketAddress(info.getProperty("Test"));

            long start = System.currentTimeMillis();

            VirtualSocket vs = sf.createClientSocket(a, TIMEOUT, null);

            vs.setSoTimeout(10000);

            long end = System.currentTimeMillis();

            System.out.println("Connection setup took: " + (end-start) + " ms.");

            synchronized (this) {
                Connection c = new Connection(connections.size(), vs);
                connections.add(c);
                c.start();
            }



        } catch (Exception e) {
            System.out.println("Failed to create connection: " + e);
        }
    }

    private synchronized void close(String s) {

        int target = parseTargetConnection(s);

        if (target == -1) {
            // error is already printed!
            return;
        }

        Connection c = connections.get(target);

        if (c == null) {
            System.out.println("Connection " + target + " already closed!");
            return;
        }

        c.close();
        connections.set(target, null);
    }
    private void connectRepeat(String s) {


        int target = -1;

        StringTokenizer st = new StringTokenizer(s, " ");

        if (st.countTokens() != 2) {
            System.out.println("connect test requires parameters: " +
                    "target #connections");
            return;
        }

        target = parseTargetClient(st.nextToken());

        if (target == -1) {
            // error already given!
            return;
        }

        int repeat = -1;

        try {
            repeat = Integer.parseInt(st.nextToken());
        } catch (Exception e) {
            System.out.println("connect test requires parameters: " +
            "target #connections");
            return;
        }

        ClientInfo info = clientInfo[target];

        if (!info.hasProperty("Repeat")) {
            System.out.println("Cannot connect to client " + target
                    + " since it doesn't export a \"Repeat\" port");
            return;
        }

        try {
            VirtualSocketAddress a =
                new VirtualSocketAddress(info.getProperty("Repeat"));

            System.out.println("Connecting " + repeat + " times to target " + target);

            long start = System.currentTimeMillis();

            for (int i=0;i<repeat;i++) {
                VirtualSocket vs = sf.createClientSocket(a, TIMEOUT, null);
                vs.close();
            }

            long end = System.currentTimeMillis();

            System.out.println(repeat + "connection setups took: "
                    + (end-start) + " ms. -> avg. time: " + ((end-start)/repeat));

        } catch (Exception e) {
            System.out.println("Failed to create connection: " + e);
        }
    }

    private void parseInput() {

        boolean done = false;

        BufferedReader clin =
            new BufferedReader(new InputStreamReader(System.in));

        try {
            while (!done) {
                System.out.print("> ");
                System.out.flush();

                String line = clin.readLine();

                if (line == null) {
                    // probably a control-c ?
                    exit();
                    done = true;
                } else {
                    line = line.trim();

                    if (line.length() == 0) {
                        // ignore empty lines....
                    } else if (line.startsWith("help")) {
                        usage();
                    } else if (line.startsWith("send ")) {
                        send(line.substring(5).trim());
                    } else if (line.startsWith("hubs")) {
                        listhubs();
                    } else if (line.startsWith("clients")) {
                        listclients(line.substring(7).trim());
                    } else if (line.startsWith("connections")) {
                        connections();
                    } else if (line.startsWith("connect")) {
                        connect(line.substring(7).trim());
                    } else if (line.startsWith("repeat connect")) {
                        connectRepeat(line.substring(14).trim());
                    } else if (line.startsWith("close")) {
                        close(line.substring(5).trim());
                    } else if (line.startsWith("ping")) {
                        ping(line.substring(4).trim());
                    } else if (line.startsWith("exit")) {
                        exit();
                        done = true;
                    } else {
                        System.out.println("Unknown command, try help");
                    }
                }

                printMessages();
            }
        } catch (Exception e) {
            System.out.println("Got exception! " + e);
        }
    }

    private void handleConnection(VirtualSocket s) {

        gotMessage("Incoming connection from "
                    + s.getRemoteSocketAddress());

        Connection c = null;

        try {
            synchronized (this) {
                c = new Connection(connections.size(), s);
                connections.add(c);
                c.start();
            }
        } catch (Exception e) {
            System.out.println("Server got exception " + e);
        }
    }

    public void run() {

        try {
            normal.setSoTimeout(1000);

        } catch (Exception e) {
            System.out.println("Server got exception " + e);
            return;
        }

        while (true) {
            VirtualSocket s = null;

            try {
                s = normal.accept();
            } catch (SocketTimeoutException e) {
                // ignored!
            } catch (Exception e) {
                System.out.println("Server got exception " + e);
                return;
            }

            if (s != null) {
                handleConnection(s);
            }
        }
    }

    public static void main(String [] args) throws IOException {

        int port = 17771;

        for (int i=0;i<args.length;i++) {

            if (args[i].equals("-port")) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-interactive")) {
                interactive = true;
            } else {
                System.err.println("Unknown option: " + args[i]);
                System.exit(1);
            }
        }

        Test t = new Test(port);

        if (interactive) {
            // run the receiver in a seperate thread, and the interactive bit
            // in parallel...
            t.start();
            t.parseInput();
        } else {
            // run the receiver directly
            t.run();
        }
    }
}
