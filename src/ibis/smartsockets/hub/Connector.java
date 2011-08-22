package ibis.smartsockets.hub;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSSHSocket;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.hub.connections.HubConnection;
import ibis.smartsockets.hub.connections.VirtualConnections;
import ibis.smartsockets.hub.state.HubDescription;
import ibis.smartsockets.hub.state.HubList;
import ibis.smartsockets.hub.state.StateCounter;
import ibis.smartsockets.util.TypedProperties;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Connector extends CommunicationThread {

    private static final Logger hconlogger =
        LoggerFactory.getLogger("ibis.smartsockets.hub.connections.hub");

    private int sendBuffer = -1;
    private int receiveBuffer = -1;

    private final int usercode;

    private final StatisticsCallback callback;
    private final long statisticsInterval;

    Connector(TypedProperties p, StateCounter state, Connections connections,
            HubList knownHubs, VirtualConnections vcs,
            DirectSocketFactory factory, StatisticsCallback callback,
            long statisticsInterval) {

        super("HubConnector", state, connections, knownHubs, vcs, factory);

        this.callback = callback;
        this.statisticsInterval = statisticsInterval;

        sendBuffer = p.getIntProperty(SmartSocketsProperties.HUB_SEND_BUFFER, -1);
        receiveBuffer = p.getIntProperty(SmartSocketsProperties.HUB_RECEIVE_BUFFER, -1);
        usercode = p.getIntProperty(SmartSocketsProperties.HUB_VIRTUAL_PORT, 42);
    }

    private boolean sendConnect(DataOutputStream out, DataInputStream in)
        throws IOException {

        if (hconlogger.isDebugEnabled()) {
            hconlogger.debug("Sending connection request");
        }

        out.write(ConnectionProtocol.HUB_CONNECT);
        out.writeUTF(localAsString);
        out.flush();

        int opcode = in.read();

        switch (opcode) {
        case ConnectionProtocol.CONNECTION_ACCEPTED:
            if (hconlogger.isDebugEnabled()) {
                hconlogger.debug("Connection request accepted");
            }
            return true;
        case ConnectionProtocol.CONNECTION_REFUSED:
            if (hconlogger.isDebugEnabled()) {
                hconlogger.debug("Connection request refused (duplicate)");
            }
            return false;
        default:
            if (hconlogger.isDebugEnabled()) {
                hconlogger.warn("Got unknown reply from proxy! ("
                        + opcode + ")");
            }
            return false;
        }
    }

    private void testConnection(HubDescription d) {

        DirectSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;

        // Creates a connection to a hub to check if it is reachable. If so,
        // it will send a ping.
        if (hconlogger.isDebugEnabled()) {
            hconlogger.info("Creating test connection to " + d.hubAddress);
        }

        try {
	        s = factory.createSocket(d.hubAddress, DEFAULT_TIMEOUT, 0,
                    sendBuffer, receiveBuffer, null, false, usercode);
            s.setTcpNoDelay(true);
            s.setSoTimeout(DEFAULT_TIMEOUT);

            out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));

            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            out.write(ConnectionProtocol.PING);
            out.writeUTF(localAsString);
            out.flush();

            if (hconlogger.isDebugEnabled()) {
                hconlogger.debug("Succesfully created connection!");
            }

            //if (!d.reachableKnown() || !d.isReachable()) {
                d.setReachable();
                knownHubs.getLocalDescription().addConnectedTo(d.hubAddressAsString);
            //}

        } catch (IOException e) {

            if (hconlogger.isDebugEnabled()) {
                hconlogger.info("Failed to set up connection!");
            }

//            if (!d.reachableKnown() || d.isReachable()) {
                d.setUnreachable();
                knownHubs.getLocalDescription().removeConnectedTo(d.hubAddressAsString);
  //          }

        } finally {
            DirectSocketFactory.close(s, out, in);
        }
    }

    private void createConnection(HubDescription d) {

        DirectSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        boolean result = false;
        HubConnection c = null;

        // Creates a connection to a newly discovered proxy. Note that there is
        // a very nice race condition here, since the target proxy may be doing
        // exactly the same connection setup to us at this very moment.
        //
        // As a result, we may get two half-backed connections between the
        // proxies, because the state of the two receiving ends conflicts with
        // the state of the sending parts....
        //
        // To solve this problem, we introduce some 'total order' on the proxies
        // by comparing the string form of their addresses. We then let the
        // smallest one decide what to do...
        boolean master = localAsString.compareTo(d.hubAddress.toString()) < 0;

        if (hconlogger.isInfoEnabled()) {
            hconlogger.info("Creating connection to " + d.hubAddress);
        }

        try {
	        s = factory.createSocket(d.hubAddress, DEFAULT_TIMEOUT, 0,
                    sendBuffer, receiveBuffer, null, false, usercode);

            s.setTcpNoDelay(true);
            s.setSoTimeout(DEFAULT_TIMEOUT);

            if (hconlogger.isInfoEnabled()) {
                hconlogger.info("Send buffer = " + s.getSendBufferSize());
                hconlogger.info("Recv buffer = " + s.getReceiveBufferSize());
            }

            out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));

            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            // If I am the master I must atomically grab the connection 'lock'
            // before sending the request. It will return true if it is still
            // free. If it isn't, we don't need to create the connection anymore
            // and just send a ping message instead. If I am the slave then we
            // grab the lock after sending the connect message.
            //
            // This approach ensures that if there are two machines trying to
            // do a connection setup at the same time, the lock is always
            // grabbed on one of the two machines. This way one of the two
            // will always win. Added bonus is that the receiving thread does
            // not need to know anything about this.
            //
            // Note that we intentionally create the connection first, since
            // we don't want to grab the lock until we're absolutely sure that
            // we're able to create the connection. If we wouldn't do this, we
            // may 'accidently' block an incoming connection from a machine that
            // we are not able to connect to ourselves.
            if (master) {
                if (hconlogger.isDebugEnabled()) {
                    hconlogger.debug("I am master during connection setup");
                }

                c = new HubConnection(s, in, out, d, connections,
                        knownHubs, state, virtualConnections, true,
                        callback, statisticsInterval);

                result = d.createConnection(c);

                if (!result) {
                    if (hconlogger.isDebugEnabled()) {
                        hconlogger.debug("Connection was already created!");
                    }

                    // never mind...
                    out.write(ConnectionProtocol.PING);
                    out.writeUTF(localAsString);
                    out.flush();
                } else {
                    result = sendConnect(out, in);
                }
            } else {
                if (hconlogger.isDebugEnabled()) {
                    hconlogger.debug("I am slave during connection setup");
                }

                result = sendConnect(out, in);

                if (result) {
                    c = new HubConnection(s, in, out, d, connections,
                            knownHubs, state, virtualConnections, false,
                            callback, statisticsInterval);
                    result = d.createConnection(c);

                    if (!result) {
                        // This should not happen if the protocol works....
                        hconlogger.warn("Race condition triggered during " +
                                "connection setup!!");
                    }
                }
            }

            s.setSoTimeout(0);

            d.setReachable();

            String name = d.hubAddressAsString;

            if (s instanceof DirectSSHSocket) {
                name += " (SSH)";
            }

            knownHubs.getLocalDescription().addConnectedTo(name);
         } catch (IOException e) {
            // This happens a lot, so it's not worth a warning...
            if (hconlogger.isDebugEnabled()) {
                hconlogger.debug("Got exception!", e);
            }

            d.setUnreachable();

            DirectSocketFactory.close(s, out, in);
       }

        if (result) {
            if (hconlogger.isDebugEnabled()) {
                hconlogger.debug("Succesfully created connection to "
                        + d.hubAddressAsString);
            }

            connections.put(d.hubAddress, c);
            c.activate();

            String name = d.hubAddressAsString;

            if (s instanceof DirectSSHSocket) {
                name += " (SSH)";
            }

            knownHubs.getLocalDescription().addConnectedTo(name);
        } else {
            if (hconlogger.isInfoEnabled()) {
                hconlogger.info("Failed to set up connection!");
            }
            DirectSocketFactory.close(s, out, in);
        }
    }

    private void handleNewHub() {

        // Handles the connection setup to newly discovered proxies.
        HubDescription d = knownHubs.nextHubToCheck();

        if (d == null) {
            // This may happen if the hub is shutting down...
            return;
        }

        if (d.haveConnection()) {
            // The connection was already created by the other side. Create a
            // test connection to see if the proxy is reachable from here.
            testConnection(d);
        } else {
            createConnection(d);
        }

        knownHubs.putBack(d);
    }

    public void run() {

        while (!getDone()) {
            handleNewHub();
        }
    }
}
