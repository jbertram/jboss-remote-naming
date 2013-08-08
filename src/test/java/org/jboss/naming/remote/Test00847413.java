package org.jboss.naming.remote;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.naming.remote.server.RemoteNamingService;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.Test;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Stuart Douglas
 * @author Justin Bertram
 */
public class Test00847413 {


    public static final String SERVER = "Server-Port";

    public static Endpoint createServer(int port) throws Exception {
        Context localContext = new MockContext();
        localContext.bind("serverId", SERVER + port);
        final Xnio xnio = Xnio.getInstance();
        final Endpoint endpoint = Remoting.createEndpoint("RemoteNaming", xnio, OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.EMPTY);

        final NetworkServerProvider nsp = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SocketAddress bindAddress = new InetSocketAddress("localhost", port);
        final OptionMap serverOptions = TestUtils.createOptionMap();

        nsp.createServer(bindAddress, serverOptions, new TestUtils.DefaultAuthenticationHandler(), null);
        RemoteNamingService server = new RemoteNamingService(localContext, Executors.newFixedThreadPool(10));
        server.start(endpoint);
        return endpoint;
    }


    @Test
    public void testHaContext() throws Exception {

        // create 2 JNDI servers
        Endpoint server1 = createServer(7999);
        Endpoint server2 = createServer(8999);

        try {
            Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, org.jboss.naming.remote.client.InitialContextFactory.class.getName());
            env.put(Context.PROVIDER_URL, "remote://localhost:7999,remote://localhost:8999");
            InitialContext context = new InitialContext(env);

            // do 2 look-ups on server1
            assertEquals(SERVER + 7999, context.lookup("serverId"));
            assertEquals(SERVER + 7999, context.lookup("serverId"));

            // close server1, this will trigger the context to fail-over to server2
            server1.close();

            // do a look-up on server2
            assertEquals(SERVER + 8999, context.lookup("serverId"));
          
            // restart server1
            server1 = createServer(7999);

            // do another look-up on server2, this shows the context is still connected to server2 even though server1 is now up
            assertEquals(SERVER + 8999, context.lookup("serverId"));

            // now re-create the context
            context.close();
            context = new InitialContext(env);

            // make sure the context connects to the 1st server in the URL even though the 2nd one is still up had we had been connected to it
            assertEquals(SERVER + 7999, context.lookup("serverId"));

        } finally {
            if (server1 != null) {
                server1.close();
            }
            if (server2 != null) {
                server2.close();
            }
        }
    }


}
