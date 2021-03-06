package test.direct.simple;

import ibis.smartsockets.direct.DirectServerSocket;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class ConnectTest {

    private static final int SERVERPORT = 42611;

    private static final int REPEAT = 10;

    private static final int COUNT = 1000;

    private static final int TIMEOUT = 1000;

    public static void main(String[] args) {

        try {
            DirectSocketFactory sf = DirectSocketFactory.getSocketFactory();

            Random rand = new Random();

            int repeat = REPEAT;
            int count = COUNT;
            int timeout = TIMEOUT;

            boolean ssh = false;
            boolean sleep = false;
            boolean pingpong = false;

            int targetCount = args.length;

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-repeat")) {
                    repeat = Integer.parseInt(args[i + 1]);
                    args[i + 1] = null;
                    args[i] = null;
                    targetCount -= 2;
                    i++;

                } else if (args[i].equals("-count")) {
                    count = Integer.parseInt(args[i + 1]);
                    args[i + 1] = null;
                    args[i] = null;
                    targetCount -= 2;
                    i++;

                } else if (args[i].equals("-timeout")) {
                    timeout = Integer.parseInt(args[i + 1]);
                    args[i + 1] = null;
                    args[i] = null;
                    targetCount -= 2;
                    i++;

                } else if (args[i].equals("-sleep")) {
                    sleep = true;
                    args[i] = null;
                    targetCount--;

                } else if (args[i].equals("-ssh")) {
                    ssh = true;
                    args[i] = null;
                    targetCount--;

                } else if (args[i].equals("-pingpong")) {
                    pingpong = true;
                    args[i] = null;
                    targetCount--;
                }
            }

            if (targetCount > 0) {
                DirectSocketAddress[] targets
                    = new DirectSocketAddress[targetCount];

                int index = 0;

                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null) {
                        targets[index++]
                                = DirectSocketAddress.getByAddress(args[i]);
                    }
                }

                Map<String, Object> prop = null;

                if (ssh) {
                    prop = new HashMap<String, Object>();
                    prop.put("allowSSH", "true");
                }

                int total = 0;

                for (DirectSocketAddress t : targets) {

                    if (sleep) {
                        try {
                            Thread.sleep(1000 + rand.nextInt(15000));
                        } catch (Exception e) {
                            // TODO: handle exception
                        }
                    }

                    System.out.println("Creating connection to " + t);

                    for (int r = 0; r < repeat; r++) {
                        long time = System.currentTimeMillis();

                        for (int c = 0; c < count; c++) {

                            DirectSocket s
                                = sf.createSocket(t, timeout, 0, prop);

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
                        }

                        time = System.currentTimeMillis() - time;

                        total += count;

                        System.out.println(count + " connections in " + time
                                + " ms. -> " + (((double) time) / count)
                                + "ms/conn (total: " + total + ")");
                    }
                }
            } else {
                System.out.println("Creating server socket");

                DirectServerSocket ss = sf.createServerSocket(SERVERPORT, 0,
                        null);

                System.out.println("Created server on " + ss.getLocalAddressSet());

                while (true) {
                    DirectSocket s = ss.accept();

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

        } catch (IOException e) {
            System.out.println("EEK!");
            e.printStackTrace(System.err);
        }
    }
}
