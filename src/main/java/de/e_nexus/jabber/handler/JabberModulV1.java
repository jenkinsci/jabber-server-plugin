package de.e_nexus.jabber.handler;

import java.util.Collections;
import java.util.List;

import org.apache.vysper.xmpp.modules.Module;
import org.apache.vysper.xmpp.modules.ServerRuntimeContextService;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;

/**
 * The base of jabber modules in Version 1. For compatibility only if you like
 * to use the plugin in different Versions multiple times.
 * 
 * @author Peter Rader
 * @since 1.5
 * 
 */
public abstract class JabberModulV1 implements
		org.apache.vysper.xmpp.modules.Module {
	/**
	 * Initialize the Modul using the current {@link ServerRuntimeContext}.
	 * @param serverRuntimeContext The Context, never <code>null</code>.
	 */
	public void initialize(final ServerRuntimeContext serverRuntimeContext) {

	}

	/**
	 * The Version of the {@link Module}.
	 * 
	 * @return The version <tt>1.0</tt> and Never <code>null</code>.
	 */
	public final String getVersion() {
		return "1.0";
	}

	/**
	 * The Services you may fill.
	 * 
	 * @return A list of Services the Modul contains.
	 */
	public final List<ServerRuntimeContextService> getServerServices() {
		return Collections.emptyList();
	}
}
