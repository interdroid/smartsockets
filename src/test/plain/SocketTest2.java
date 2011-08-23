package test.plain;

import ibis.smartsockets.plugin.SmartSocketAddress;
import ibis.smartsockets.plugin.SmartSocketImplFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;


public class SocketTest2 {

    private final static int PORT = 8899;
    private final static int REPEAT = 10;
    private final static int COUNT = 10;

    private static void setSocketImplFactory(String type) {

        if (type == null) {
            type = "plain";
        } else {
            type = type.trim();
        }

        if (type.equalsIgnoreCase("plain")) {
            return;
        }

        if (type.equalsIgnoreCase("SmartSockets")) {
            try {
                SmartSocketImplFactory f = new SmartSocketImplFactory();
                Socket.setSocketImplFactory(f);
                ServerSocket.setSocketFactory(f);
            } catch (Exception e) {
                throw new Error("Failed to install SocketFactory type: " + type, e);
            }
            return;
        }

        System.err.println("Unknown ServerSocketFactory type: " + type);
        System.exit(1);
    }

    public static void main(String[] args) {

        int targets = args.length;
        int repeat = REPEAT;
        int count = COUNT;

        String type = null;
        boolean smart = false;
        boolean pingpong = false;


        for (int i=0;i<args.length;i++) {
            if (args[i].equals("-repeat")) {
                repeat = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;

            } else if (args[i].equals("-count")) {
                count = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;

            } else if (args[i].equals("-pingpong")) {
                pingpong = true;
                args[i] = null;
                targets--;

            } else if (args[i].equals("-type")) {
                type = args[i+1].trim();
                args[i] = args[i+1] = null;
                i++;
                targets--;
            }
        }

        if (type != null && type.equalsIgnoreCase("SmartSockets")) {
            smart = true;
        }

        setSocketImplFactory(type);

        try {
            SocketAddress [] targetAds = new SocketAddress[targets];
            int index = 0;

            for (int i=0;i<args.length-1;i++) {
                if (args[i] != null && args[i+1] != null) {
                    targetAds[index++] = SmartSocketAddress.create(
                            args[i], Integer.parseInt(args[i+1]), smart);
                }
            }

            if (index > 0) {

                for (SocketAddress a : targetAds) {

                    if (a == null) {
                        continue;
                    }

                    System.out.println("Creating connection to " + a);

                    Socket s = null;

                    for (int r = 0; r < repeat; r++) {

                        long time = System.currentTimeMillis();

                        // int failed = 0;

                        for (int c = 0; c < count; c++) {

                            if (s == null) {
                                s = new Socket();
                                s.setReuseAddress(true);
                            }

                            try {
                                s.connect(a, 1000);

                                if (pingpong) {
                                    s.setTcpNoDelay(true);

                                    OutputStream out = s.getOutputStream();

                                    out.write(42);
                                    out.flush();

                                    InputStream in = s.getInputStream();
                                    in.read();

                                    in.close();
                                    out.close();
                                }

                                s.close();
                                s = null;
                            } catch (IOException e) {
                                System.err.println("" + e);
                                // failed++;
                            }
                        }

                        time = System.currentTimeMillis() - time;

                        System.out.println(count + " connections in " + time
                                + " ms. -> " + (((double) time) / count)
                                + "ms/conn");

                    }
                }
            } else {

                System.out.println("Creating server socket");

                ServerSocket ss = new ServerSocket(PORT, 100);

                System.out.println("Created server on " + ss.toString());

                while (true) {
                    Socket s = ss.accept();

                    if (pingpong) {
                        s.setTcpNoDelay(true);

                        InputStream in = s.getInputStream();
                        in.read();

                        OutputStream out = s.getOutputStream();

                        out.write(42);
                        out.flush();

                        in.close();
                        out.close();
                    }

                    s.close();
                }
            }

        } catch (Throwable e) {
            System.out.println("EEK!");
            e.printStackTrace(System.err);
        }
    }
}
