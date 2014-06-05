package de.e_nexus.jabber.handler.chat;

import java.util.ArrayList;
import java.util.Map;

import org.apache.vysper.xml.fragment.Attribute;
import org.apache.vysper.xml.fragment.XMLElement;
import org.apache.vysper.xml.fragment.XMLFragment;
import org.apache.vysper.xml.fragment.XMLSemanticError;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.protocol.HandlerDictionary;
import org.apache.vysper.xmpp.protocol.ProtocolException;
import org.apache.vysper.xmpp.protocol.ResponseStanzaContainer;
import org.apache.vysper.xmpp.protocol.ResponseStanzaContainerImpl;
import org.apache.vysper.xmpp.protocol.SessionStateHolder;
import org.apache.vysper.xmpp.protocol.StanzaHandler;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
import org.apache.vysper.xmpp.server.SessionContext;
import org.apache.vysper.xmpp.stanza.Stanza;
import org.apache.vysper.xmpp.stanza.StanzaBuilder;

public final class JabberChatHandler implements StanzaHandler,
		HandlerDictionary {
	private Map<String, EntityImpl> users;

	public JabberChatHandler(Map<String, EntityImpl> users) {
		this.users = users;
	}

	public boolean verify(Stanza stanza) {
		if (stanza.getName().equals("iq")
				&& stanza.getAttribute("type").getValue().equals("get"))
			return true;
		return false;
	}

	public boolean isSessionRequired() {
		return false;
	}

	public String getName() {
		return "Jabber vCard Handler";
	}

	public ResponseStanzaContainer execute(Stanza in,
			ServerRuntimeContext serverRuntimeContext,
			boolean isOutboundStanza, SessionContext sessionContext,
			SessionStateHolder sessionStateHolder) throws ProtocolException {

		if (isOutboundStanza) {
			try {
				if (in.getInnerElementsByXMLLangNamed("vcard-temp") != null) {
					ArrayList<Attribute> attr = new ArrayList<Attribute>();

					String email = in.getAttributeValue("to");
					attr.add(new Attribute("from", email));
					attr.add(new Attribute("type", "result"));
					attr.add(new Attribute("id", in.getAttributeValue("id")));
					attr.add(new Attribute("to", email));

					ArrayList<XMLFragment> frags = new ArrayList<XMLFragment>();
					StanzaBuilder out = new StanzaBuilder("iq",
							in.getNamespaceURI(), in.getNamespacePrefix(),
							attr, frags);
					StanzaBuilder vcard = out.startInnerElement("vCard",
							"vcard-temp");
					vcard.addAttribute(new Attribute("version", "2.0"));
					StanzaBuilder fn = vcard.startInnerElement("FN");
					EntityImpl entityImpl = findUserByEmail(email);
					if (entityImpl != null) {

						fn.addText(entityImpl.getNode());
					} else {
						fn.addText(email + " unknown");
					}
					out.endInnerElement();
					out.endInnerElement();

					ResponseStanzaContainerImpl resp = new ResponseStanzaContainerImpl(
							out.build());

					return resp;
				}
			} catch (XMLSemanticError e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private EntityImpl findUserByEmail(String email) {
		for (EntityImpl e : users.values()) {
			if (e.getFullQualifiedName().equals(email)) {
				return e;
			}
		}
		return null;
	}

	public void register(StanzaHandler stanzaHandler) {
	}

	public void seal() {
	}

	public StanzaHandler get(Stanza in) {
		XMLElement inFirst = in.getFirstInnerElement();
		if (inFirst != null && "vCard".equals(inFirst.getName())) {
			return this;
		}
		return null;
	}
}