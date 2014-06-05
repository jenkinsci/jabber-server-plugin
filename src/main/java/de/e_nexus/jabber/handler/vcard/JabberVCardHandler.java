package de.e_nexus.jabber.handler.vcard;

import java.util.ArrayList;
import java.util.Collections;
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

/**
 * A empty VCard modul used to prevent bugging of requests for VCard from PSI.
 * 
 * @author Peter Rader
 * @since 1.3
 */
public final class JabberVCardHandler implements StanzaHandler,
		HandlerDictionary {
	/**
	 * The users, initialized as empty {@link Map}.
	 */
	private Map<String, EntityImpl> users = Collections.emptyMap();

	/**
	 * The Constructor.
	 * 
	 * @param users
	 *            The Users as a Map, neither the Key, nor the Value shall be
	 *            <code>null</code>.
	 */
	public JabberVCardHandler(final Map<String, EntityImpl> users) {
		this.users = users;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean verify(final Stanza stanza) {
		if (stanza.getName().equals("iq")
				&& stanza.getAttribute("type").getValue().equals("get")) {
			return true;
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isSessionRequired() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getName() {
		return "Jabber vCard Handler";
	}

	/**
	 * {@inheritDoc}
	 */
	public ResponseStanzaContainer execute(final Stanza in,
			final ServerRuntimeContext serverRuntimeContext,
			final boolean isOutboundStanza,
			final SessionContext sessionContext,
			final SessionStateHolder sessionStateHolder)
			throws ProtocolException {

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
					EntityImpl entityImpl = findUserByJID(email);
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

	/**
	 * Find the {@link EntityImpl} from JID.
	 * 
	 * @param JID
	 *            The JID.
	 * @return The {@link EntityImpl}.
	 */
	private EntityImpl findUserByJID(final String JID) {
		synchronized (users) {

			for (EntityImpl entity : users.values()) {
				if (entity.getFullQualifiedName().equals(JID)) {
					return entity;
				}
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void register(final StanzaHandler stanzaHandler) {
	}

	/**
	 * {@inheritDoc}
	 */
	public void seal() {
	}

	/**
	 * {@inheritDoc}
	 * @throws NullPointerException If the request does not have a innerElement.
	 */
	public StanzaHandler get(final Stanza in) throws NullPointerException{
		XMLElement inFirst = in.getFirstInnerElement();
		if (inFirst != null && "vCard".equals(inFirst.getName())) {
			return this;
		}
		return null;
	}
}