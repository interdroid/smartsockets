package ibis.smartsockets.naming;

import ibis.smartsockets.util.MalformedAddressException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A method used by the {@link NameResolver}
 * to resolve names to addresses.
 * @author nick <palmer@cs.vu.nl>
 *
 */
public abstract class NameResolverMethod {
	/** Logger for this class. */
	private static final Logger LOGGER = LoggerFactory
			.getLogger("ibis.smartsockets.naming");

	/**
	 * Subclasses should add themselves to this using a static block.<br>
	 * <pre>static {
	 *      NameResolverMethod.registerMethod(<ClassName>.class);
	 * }</pre>
	 */
	private static
	Map<String, Class<? extends NameResolverMethod>> sResolverMethods =
	new HashMap<String, Class<? extends NameResolverMethod>>();

	/**
	 * The resolver this method is working for.
	 */
	private NameResolver mResolver;

	/**
	 * Sets the resolver for this method. Called immediately after construction.
	 * @param nameResolver the resolver for this method.
	 */
	private void setResolver(final NameResolver nameResolver) {
		mResolver = nameResolver;
	}

	protected void fireLocalRemove(final String name) {
		mResolver.handleRemove(true, name);
	}

	protected void fireLocalAdd(String name,
			String address, Map<String, String> props)
					throws UnknownHostException, MalformedAddressException {
		mResolver.handleAdd(true, name, address, props);
	}

	/**
	 * @return the resolver this instance is working for.
	 */
	protected final NameResolver getResolver() {
		return mResolver;
	}

	/**
	 * Registers a method. Should be called by subclasses from a static block.
	 * @param method the method being registered.
	 */
	protected static void registerMethod(
			final Class<? extends NameResolverMethod> method) {
		sResolverMethods.put(method.getName(), method);
	}

	/**
	 * Called by the resolver in order to initialize the methods.
	 * @param nameResolver The resolver the methods are working for
	 * @return a list of the resolver methods.
	 */
	static List<NameResolverMethod>
	initMethodsForResolver(final NameResolver nameResolver) {
		List<NameResolverMethod> methods = new ArrayList<NameResolverMethod>();
		LOGGER.debug("I know of: {} resolver methods", sResolverMethods.size());
//        try {
//            Class.forName("ibis.smartsockets.naming.NameResolverMDNS");
//        } catch (ClassNotFoundException e1) {
//            // TODO Auto-generated catch block
//            e1.printStackTrace();
//        }
		try {
			Class.forName("ibis.smartsockets.naming.NameResolverServiceLink");
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		LOGGER.debug("I know of: {} resolver methods", sResolverMethods.size());

		for (Class<? extends NameResolverMethod> method
			: sResolverMethods.values()) {
			try {
				NameResolverMethod methodInstance = method.newInstance();
				methodInstance.setResolver(nameResolver);
				methodInstance.start();
				methods.add(methodInstance);
			} catch (Exception e) {
				LOGGER.error("Error instantiating resolver method", e);
			}

		}

		return methods;
	}

	// The functions subclasses must implement

	/**
	 * Registers a name with this resolver method.
	 *
	 * @param name The name of the socket
	 * @param address The address of the socket
	 * @param info Any additional application specific information
	 * @throws IOException If there was a a problem with the registration
	 */
	public abstract void register(final String name, final String address,
			final Map<String, String>info) throws IOException;

	/**
	 * Removes a name from this method.
	 * @param name The name to be removed
	 * @throws IOException If there was a problem with unregistering
	 */
	public abstract void unregister(final String name) throws IOException;

	/**
	 * Called once the resolver has been set and this method should start.
	 * @throws IOException If there is a problem starting the service
	 */
	public abstract void start() throws IOException;

	/**
	 * Called to indicate that the method should shutdown.
	 */
	public abstract void stop();

	/**
	 * Tells this method to query for this name if possible.
	 * Note that this should return as quickly as possible
	 * so that other methods can also get the query. If a method
	 * needs to perform long running tasks to handle a query
	 * that must be done in another thread so as not to
	 * block the resolver thread from querying the other methods
	 * it knows about.
	 * @param name the name to query for.
	 */
	public abstract void query(String name);
}
