package test.direct.splice;


import ibis.smartsockets.direct.DirectServerSocket;
import ibis.smartsockets.direct.DirectSimpleSocket;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;


public class SimpleSpliceTest {

    private static final int LOCAL_PORT = 16889;

    private static DirectSocketFactory sf =
        DirectSocketFactory.getSocketFactory();

    public static void main(String [] args) throws IOException {

        if (args.length > 0) {

            DirectSocketAddress target = DirectSocketAddress.getByAddress(args[0]);

            DirectSocket s = null;
            DataInputStream in = null;
            DataOutputStream out = null;

            for (int i=0;i<100;i++) {
                try {
                    s = sf.createSocket(target, 0, LOCAL_PORT, null);

                    System.out.println("Created connection to " + target +
                            " on local address " + s.getLocalAddress()
                            + " remote address " + s.getRemoteAddress());

                    in = new DataInputStream(s.getInputStream());
                    out = new DataOutputStream(s.getOutputStream());

                    out.writeUTF("Hello server!");
                    out.flush();

                    System.out.println("Server says: " + in.readUTF());

                } catch (Exception e) {
                    System.out.println("Failed to created connection to "
                            + target);
                } finally {
                    DirectSocketFactory.close(s, out, in);
                }
            }
        } else {
            System.out.println("Creating server socket");

            DirectServerSocket ss = sf.createServerSocket(LOCAL_PORT, 0, null);
            ss.setReuseAddress(true);

            System.out.println("Created server on " + ss.getAddressSet());

            // Get a normal connection first...
            DirectSimpleSocket s = (DirectSimpleSocket) ss.accept();
            ss.close();

            InetSocketAddress address =
                (InetSocketAddress) s.getRemoteSocketAddress();

            System.out.println("Incoming connection from " + address);

            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            System.out.println("Client says: " + in.readUTF());
            out.writeUTF("Hello client!");
            out.flush();

            DirectSocketFactory.close(s, out, in);

            // Now try if we can connect to the client using an outgoing
            // connection

            DirectSocketAddress target = DirectSocketAddress.getByAddress(address);

            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("allowSSH", "false");

            for (int i=0;i<100;i++) {

                try {
                    s = (DirectSimpleSocket) sf.createSocket(target, 0,
                            LOCAL_PORT, properties);

                    System.out.println("Created connection to " + target +
                            " on local address " + s.getLocalSocketAddress()
                            + " remote address " + s.getRemoteSocketAddress());

                    in = new DataInputStream(s.getInputStream());
                    out = new DataOutputStream(s.getOutputStream());

                    out.writeUTF("Hello server!");
                    out.flush();

                    System.out.println("Server says: " + in.readUTF());

                } catch (Exception e) {
                    System.out.println("Failed to created connection to "
                            + target);
                } finally {
                    DirectSocketFactory.close(s, out, in);
                }
            }
        }
    }
}
