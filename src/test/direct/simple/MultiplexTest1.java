package test.direct.simple;


import ibis.smartsockets.direct.DirectServerSocket;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.util.MultiplexStreamFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class MultiplexTest1 {

    public static void main(String [] args) throws IOException {

        DirectSocketFactory sf = DirectSocketFactory.getSocketFactory();

        if (args.length > 0) {
            for  (int i=0;i<args.length;i++) {
                DirectSocketAddress target = DirectSocketAddress.getByAddress(args[i]);
                DirectSocket s = sf.createSocket(target, 0, null);

                System.out.println("Created connection to " + target);

                MultiplexStreamFactory f =
                    new MultiplexStreamFactory(s.getInputStream(),
                            s.getOutputStream());

                DataInputStream in = new DataInputStream(f.getBaseIn());
                DataOutputStream out = new DataOutputStream(f.getBaseOut());

                out.writeUTF("Hello server!");
                out.flush();

                out.writeUTF("Hello server!");
                out.flush();

                System.out.println("Server says: " + in.readUTF());

                out.close();
                in.close();

                f.close();
                s.close();
            }
        } else {
            System.out.println("Creating server socket");

            DirectServerSocket ss = sf.createServerSocket(0, 0, null);

            System.out.println("Created server on " + ss.getAddressSet());

            while (true) {
                DirectSocket s = ss.accept();

                System.out.println("Incoming connection from "
                        + s.getRemoteAddress());

                MultiplexStreamFactory f =
                    new MultiplexStreamFactory(s.getInputStream(),
                            s.getOutputStream());

                DataInputStream in = new DataInputStream(f.getBaseIn());
                DataOutputStream out = new DataOutputStream(f.getBaseOut());

                System.out.println("Client says: " + in.readUTF());
                System.out.println("Client says: " + in.readUTF());

                out.writeUTF("Hello client!");

                out.close();
                in.close();

                f.close();
                s.close();
            }
        }
    }
}
