package de.e_nexus.jabber;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.apache.vysper.xml.fragment.Attribute;
import org.apache.vysper.xml.fragment.XMLElement;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.modules.Module;
import org.apache.vysper.xmpp.modules.ServerRuntimeContextService;
import org.apache.vysper.xmpp.protocol.HandlerDictionary;
import org.apache.vysper.xmpp.protocol.SessionStateHolder;
import org.apache.vysper.xmpp.protocol.StanzaHandler;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
import org.apache.vysper.xmpp.server.SessionContext;
import org.apache.vysper.xmpp.server.SessionState;
import org.apache.vysper.xmpp.stanza.MessageStanza;
import org.apache.vysper.xmpp.stanza.MessageStanzaType;
import org.apache.vysper.xmpp.stanza.Stanza;
import org.apache.vysper.xmpp.stanza.StanzaBuilder;

import de.e_nexus.jabber.JabberBuilder.JabberServer;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Item;
import jenkins.model.Jenkins;

/**
 * The conversation-module.
 * 
 * @author peter
 */
public final class JenkinsConversationModule
		implements Module, HandlerDictionary {

	private static final int MINIMUM_COUNT_USERS = 20;
	private List<HandlerDictionary> list = new ArrayList<HandlerDictionary>();
	private StanzaHandler handler;
	private JenkinsJabberUserAuthorization auth;
	private ServerRuntimeContext context;

	/**
	 * Constructor for the Jenkins conversation module.
	 * 
	 * @param jenkinsAuthroizationForJabber
	 *            The user registry.
	 */
	public JenkinsConversationModule(
			final JenkinsJabberUserAuthorization jenkinsAuthroizationForJabber) {
		this.auth = jenkinsAuthroizationForJabber;
		list.add(this);
	}

	/**
	 * Returns the name.
	 * 
	 * @return the name.
	 */
	public String getName() {
		return "Butler Jenkins";
	}

	/**
	 * Returns the version.
	 * 
	 * @return The version.
	 */
	public String getVersion() {
		return "1.0";
	}

	/**
	 * Returns the handlers for the stanzas.
	 * 
	 * @return The handlers.
	 */
	public List<HandlerDictionary> getHandlerDictionaries() {
		return list;
	}

	/**
	 * Get the services.
	 * 
	 * @return The services.
	 */
	public List<ServerRuntimeContextService> getServerServices() {
		return null;
	}

	/**
	 * Stores the {@link ServerRuntimeContext}.
	 * 
	 * @param serverRuntimeContext
	 *            The Context.
	 */
	public void initialize(ServerRuntimeContext serverRuntimeContext) {
		context = serverRuntimeContext;
	}

	/**
	 * Stores the {@link StanzaHandler}.
	 * 
	 * @param stanzaHandler
	 *            The handler.
	 */
	public void register(final StanzaHandler stanzaHandler) {
		this.handler = stanzaHandler;
	}

	/**
	 * Not used.
	 */
	public void seal() {

	}

	/**
	 * Handles the incomming stanza.
	 * 
	 * @param stanza
	 *            The Stanza.
	 * @return The handler.
	 */
	public StanzaHandler get(final Stanza stanza) {
		Attribute attribute = stanza.getAttribute("type");
		Attribute from = stanza.getAttribute("from");
		if (attribute != null && from != null) {
			String type = attribute.getValue();
			if ("chat".equals(type)) {
				XMLElement firstInnerElement = stanza.getFirstInnerElement();
				String message = firstInnerElement.getFirstInnerText()
						.getText();
				char lastChar = message.toCharArray()[message.length() - 1];
				if (lastChar == '!' | lastChar == '?') {
					System.out.println(message);
					String lower = message.toLowerCase().replaceAll(" ", "");
					if (lower.startsWith("jenkins")) {
						if (lower.contains("user") | lower.contains("visitor")
								| lower.contains("people")
								| lower.contains("buddy")
								| lower.contains("colleg")) {
							final Stanza stanza1 = stanza;
							Entity from1 = stanza1.getFrom();
							listUsers(from1);
						} else if (lower.contains("status")
								| lower.contains("action")
								| lower.contains("work")
								| lower.contains("job")) {
							Entity from1 = stanza.getFrom();
							answerJobs(from1);
						} else if (lower.contains("call")
								| lower.contains("inform")
								| lower.contains("shout")
								| lower.contains("broadcast")
								| lower.contains("proclaim")) {
							TreeSet<Integer> ts = new TreeSet<Integer>();
							ts.add(message.indexOf("\""));
							ts.add(message.indexOf("'"));
							ts.add(message.indexOf(":"));
							ts.add(message.indexOf("="));
							ts.remove(-1);

							if (ts.isEmpty()) {
								String action = lastChar == '?' ? "ask"
										: "shout";
								answer("Excuse me, what exactly shall i "
										+ action + "? ", stanza.getFrom());
							} else {
								answer("They are notified!", stanza.getFrom());

								JabberServer.getServer().broadcast(":" + message
										.substring(ts.iterator().next() + 1));
							}
						} else if (lower.contains("coffe")) {
							answer("Coffe, yes. (C)", stanza.getFrom());
						} else {
							answer("At your service!", stanza.getFrom());

						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * The request for the list of jobs.
	 * 
	 * @param from
	 *            The Recivient.
	 */
	private void answerJobs(final Entity from) {
		Collection<String> jobNames = Jenkins.getInstance().getJobNames();
		if (jobNames.size() == 0) {
			answer("Sure, i have not only one job!", from);
		} else if (jobNames.size() == 1) {
			answer("Sure, i have only one job! Its called \""
					+ jobNames.iterator().next() + "\".", from);
		} else {
			StringWriter out = new StringWriter();
			PrintWriter pw = new PrintWriter(out);
			pw.println("Sure, i have " + jobNames.size() + " jobs:");
			for (String jobName : jobNames) {
				pw.println(" - " + jobName);

			}
			answer(out.toString(), from);
		}
	}

	/**
	 * The answer to the question what users are invited.
	 * 
	 * @param from
	 *            The Recivient.
	 */
	private void listUsers(final Entity from) {
		String[] usernames = auth.getUsernames();
		int users = usernames.length - 1;
		if (users < 1) {
			answer("Excuse me, noone is invited to come! ", from);
		} else if (users > MINIMUM_COUNT_USERS) {
			answer("Sure, more than " + MINIMUM_COUNT_USERS
					+ " people are invited to come! ", from);
		} else if (users == 1) {
			answer("Only " + usernames[0] + " is invited to come! ", from);
		} else if (users == 2) {
			answer(usernames[0] + " and " + usernames[1]
					+ " are invited to come! ", from);
		} else {
			StringWriter out = new StringWriter();
			PrintWriter pw = new PrintWriter(out);
			pw.println("We have send " + users + " invitations:");
			for (String string : usernames) {
				pw.println(" - " + string);
			}
			answer(out.toString(), from);
		}
	}

	/**
	 * The answer to the request.
	 * 
	 * @param message
	 *            The message.
	 * @param from
	 *            The recivient.
	 */
	private void answer(final String message, final Entity from) {
		List<SessionContext> sessions = context.getResourceRegistry()
				.getSessions(from);
		StanzaBuilder sb = StanzaBuilder.createMessageStanza(from, from,
				MessageStanzaType.CHAT, "html", message.replaceAll("^", "  "));
		Stanza s = new MessageStanza(sb.build());
		for (SessionContext sessionContext : sessions) {

			SessionState state = sessionContext.getState();
			SessionStateHolder ssh = new SessionStateHolder();
			ssh.setState(state);
			context.getStanzaProcessor().processStanza(context, sessionContext,
					s, ssh);
		}
	}

}
