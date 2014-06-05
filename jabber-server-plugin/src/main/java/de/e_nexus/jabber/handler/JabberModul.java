package de.e_nexus.jabber.handler;

import java.util.Collections;
import java.util.List;

import org.apache.vysper.xmpp.modules.ServerRuntimeContextService;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
/**
 * The base of 
 * @author Peter
 *
 */
public abstract class JabberModul implements
		org.apache.vysper.xmpp.modules.Module {

	public void initialize(ServerRuntimeContext serverRuntimeContext) {

	}

	public String getVersion() {
		return "1.0";
	}

	public List<ServerRuntimeContextService> getServerServices() {
		return Collections.EMPTY_LIST;
	}
}
