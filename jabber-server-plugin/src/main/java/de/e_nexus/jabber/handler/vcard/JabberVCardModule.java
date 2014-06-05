package de.e_nexus.jabber.handler.vcard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.protocol.HandlerDictionary;

import de.e_nexus.jabber.handler.JabberModul;

public final class JabberVCardModule extends JabberModul {
	private Map<String, EntityImpl> users;

	public JabberVCardModule(Map<String, EntityImpl> users) {
		this.users = users;
	}

	public String getName() {
		return "XEP-0045 Multi-User Chat";
	}

	public List<HandlerDictionary> getHandlerDictionaries() {
		List<HandlerDictionary> dd = new ArrayList<HandlerDictionary>();
		dd.add(new JabberVCardHandler(getUsers()));
		return dd;
	}

	public Map<String, EntityImpl> getUsers() {
		return users;
	}
}