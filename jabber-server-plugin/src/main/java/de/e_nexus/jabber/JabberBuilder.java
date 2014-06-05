package de.e_nexus.jabber;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.View.UserInfo;
import hudson.model.View.People;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.vysper.mina.TCPEndpoint;
import org.apache.vysper.storage.OpenStorageProviderRegistry;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.authorization.SimpleUserAuthorization;
import org.apache.vysper.xmpp.modules.extension.xep0092_software_version.SoftwareVersionModule;
import org.apache.vysper.xmpp.modules.roster.RosterItem;
import org.apache.vysper.xmpp.modules.roster.SubscriptionType;
import org.apache.vysper.xmpp.modules.roster.persistence.MemoryRosterManager;
import org.apache.vysper.xmpp.protocol.SessionStateHolder;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
import org.apache.vysper.xmpp.server.SessionContext;
import org.apache.vysper.xmpp.server.SessionState;
import org.apache.vysper.xmpp.server.XMPPServer;
import org.apache.vysper.xmpp.stanza.MessageStanza;
import org.apache.vysper.xmpp.stanza.MessageStanzaType;
import org.apache.vysper.xmpp.stanza.Stanza;
import org.apache.vysper.xmpp.stanza.StanzaBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import de.e_nexus.jabber.handler.vcard.JabberVCardModule;

/**
 * The Builder itself, used to start/stop the Server, create the user's and
 * connect them together. This class itself is thread-save, the inner static
 * Class {@link #JabberBuilder.JabberServer} is not thread-save. Therefore you shall only
 * have one instance of the {@link Builder}.
 * 
 * <p>
 * Even if the class and package named jabber, internally we use the more
 * scientific name XMPP.
 * 
 * <p>
 * The Socket may include leading zeros just for forward-compatibility usage.
 * 
 * @author Peter Rader
 * @since 1.5
 */
public final class JabberBuilder extends Builder {
	/**
	 * The name of the {@link Builder}.
	 */
	private final String name;

	/**
	 * The Constructor. Shall be used once.
	 * 
	 * @param builderName
	 *            The Name of the Builder.
	 */
	@DataBoundConstructor
	public JabberBuilder(final String builderName) {
		this.name = builderName;
	}

	/**
	 * Gets the Name of the {@link Builder}.
	 * 
	 * @return The Name of the {@link Builder}.
	 */
	public String getName() {
		return name;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build,
			final Launcher launcher, final BuildListener listener)
			throws InterruptedException, IOException {

		try {
			String m = "Building: " + build.getFullDisplayName();
			String desc = build.getProject().getDescription();
			if (desc != null && !desc.trim().equals("")) {
				m += " (" + build.getProject().getDescription() + ")";
			}
			m += "\n\r";
			for (Object o : build.getCauses()) {
				if (o instanceof Cause) {
					m += ((Cause) o).getShortDescription() + "\n\r";
				}
			}
			if (build.getChangeSet() != null
					&& build.getChangeSet().getItems() != null) {
				for (Object o : build.getChangeSet().getItems()) {
					m += o + "\n\r";
				}
			}
			JabberServer.getServer().broadcast(m);
		} catch (Exception e) {
			e.printStackTrace(listener.getLogger());
		}
		return true;
	}

	@Override
	public Descriptor<Builder> getDescriptor() {
		return super.getDescriptor();
	}

	/**
	 * The XMPP-Server instance.
	 */
	public static final class JabberServer {

		/**
		 * Lazy Singleton.
		 * 
		 * Shall not be mocked. Its a server, dont unit-test it,
		 * integration-test it instead.
		 * 
		 * @return The singleton-Instance of {@link JabberServer}.
		 */
		public static synchronized JabberServer getServer() {
			if (instance == null) {
				instance = new JabberServer();
			}
			return instance;
		}

		/**
		 * Sends a broadcast message to all users.
		 * <p>
		 * Internally synchronize about the users. Because someone may restart
		 * the server meanwhile a broadcast is running.
		 * 
		 * <p>
		 * Some lazy programmers missed to specify unchecked-exceptions in
		 * method signatures.
		 * 
		 * @param message
		 *            The message to broadcast, not null and <a
		 *            href="http://www.rfc-editor.org/rfc/rfc6120.txt">\n\r as
		 *            line-break</a>.
		 * @throws NullPointerException
		 *             If the Server stops meanwhile or some other
		 *             undocumentated cases.
		 */
		public void broadcast(final String message) throws NullPointerException {
			assert users != Collections.EMPTY_LIST;
			if (server == null) {
				throw new IllegalStateException(
						"Server has not yet been started.",
						new NullPointerException("The field 'server' is null."));
			}
			ServerRuntimeContext serverRuntimeContext = server
					.getServerRuntimeContext();
			synchronized (users) {

				for (EntityImpl recivient : users.values()) {
					java.util.List<SessionContext> sessions = serverRuntimeContext
							.getResourceRegistry().getSessions(recivient);
					StanzaBuilder sb = StanzaBuilder.createMessageStanza(
							recivient, recivient, MessageStanzaType.HEADLINE,
							"html", message);

					for (SessionContext sessionContext : sessions) {

						Stanza s = new MessageStanza(sb.build());

						SessionState state = sessionContext.getState();
						SessionStateHolder ssh = new SessionStateHolder();
						ssh.setState(state);
						serverRuntimeContext.getStanzaProcessor()
								.processStanza(serverRuntimeContext,
										sessionContext, s, ssh);

					}
				}
			}
		}

		/**
		 * The Singleton.
		 */
		private static JabberServer instance;
		/**
		 * The instance of the Provider Registry. Warning: may be null.
		 */
		private OpenStorageProviderRegistry providerRegistry;
		/**
		 * The authorization.
		 */
		private SimpleUserAuthorization simpleUserAuthorization;
		/**
		 * The server.
		 */
		private XMPPServer server;
		/**
		 * The one and only endpoint.
		 */
		private TCPEndpoint endpoint;
		/**
		 * The Users.
		 */
		private Map<String, EntityImpl> users = Collections.emptyMap();

		/**
		 * The offical server start with a cascading-fault-barrier around.
		 * 
		 * @param certAbsoluteFilename
		 *            The Filename of the .jks-file.
		 * @param certKey
		 *            The key of the .jks-file.
		 * @param hostname
		 *            The Hostname, usually not including subdomains, except
		 *            explicits.
		 * @param serverPort
		 *            The Port. In combination with the Hostname, a Socket.
		 * @throws Exception
		 *             If some unexpected has been happend.
		 */
		public synchronized void start(final String certAbsoluteFilename,
				final String certKey, final String hostname,
				final String serverPort) throws Exception {
			try {
				startJabber(certAbsoluteFilename, certKey, hostname, serverPort);
			} catch (Exception e) {
				server = null;
				endpoint = null;
				providerRegistry = null;
				simpleUserAuthorization = null;
				throw e;
			}
		}

		/**
		 * @see #start(String, String, String, String)
		 */
		private void startJabber(final String certAbsoluteFilename,
				final String certKey, final String hostname,
				final String serverPort) throws Exception {
			providerRegistry = new OpenStorageProviderRegistry();
			simpleUserAuthorization = new SimpleUserAuthorization();
			if (users == Collections.EMPTY_MAP) {
				users = new HashMap<String, EntityImpl>();
			} else {
				users.clear();
			}

			// add users
			People people = Jenkins.getInstance().getPeople();
			for (UserInfo userInfo : people.users) {
				String name = generateName(userInfo);
				EntityImpl entity = new EntityImpl(name, hostname, null);
				users.put(name, entity);
				simpleUserAuthorization.addUser(entity, name);
			}

			providerRegistry.add(simpleUserAuthorization);
			MemoryRosterManager memoryRosterManager = new MemoryRosterManager();
			providerRegistry.add(memoryRosterManager);
			server = new XMPPServer(hostname);

			endpoint = new TCPEndpoint();
			endpoint.setPort(Integer.parseInt(serverPort));

			server.addEndpoint(endpoint);
			server.setTLSCertificateInfo(new FileInputStream(
					certAbsoluteFilename), certKey);
			Runtime.getRuntime().addShutdownHook(
					new StopOnShutdown(this, "Stop Jabber/XMPP on Shutdown")
							.setBuilder(this));
			for (EntityImpl left : users.values()) {
				for (EntityImpl right : users.values()) {
					if (left != right) {

						RosterItem d = new RosterItem(right,
								SubscriptionType.BOTH);
						memoryRosterManager.addContact(left, d);
					}
				}
			}
			server.setStorageProviderRegistry(providerRegistry);
			server.start();
			server.addModule(new JabberVCardModule(users));
			server.addModule(new SoftwareVersionModule());
		}

		/**
		 * Generate a jabber JID-User-Part from {@link UserInfo}.
		 * 
		 * @param userInfo
		 *            The UserInfo used to create the Name.
		 * @return The Name.
		 */
		private String generateName(final UserInfo userInfo) {
			String id = userInfo.getUser().getId();
			if (id.indexOf("@") > 2) {
				id = id.substring(0, id.indexOf("@"));
			}

			String name = id;
			int i = 1;
			while (users.keySet().contains(name)) {
				name = id + i++;
			}
			return name;
		}

		/**
		 * Stops the XMPP-Server.
		 * 
		 * @throws IllegalStateException
		 */
		public synchronized void stop() throws IllegalStateException {
			if (endpoint == null) {
				throw new IllegalStateException("Endpoint not been started.",
						new NullPointerException());
			}
			if (server == null) {
				throw new IllegalStateException(
						"Server not been started, but Endpoint!?",
						new NullPointerException());
			}
			endpoint.stop();
			endpoint = null;
			server.stop();
			server = null;
			providerRegistry = null;
			simpleUserAuthorization = null;
			providerRegistry = null;

		}

		/**
		 * Checks if the Server is running.
		 * 
		 * @return <code>true</code> if the Server is running.
		 *         <code>false</code> if not.
		 */
		public boolean isRunning() {
			return server != null;
		}
	}

	/**
	 * The Descriptor of the BuildStep.
	 */
	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {
		/**
		 * The ServerName to use as JID-Server-Part without Resource-Part.
		 * 
		 * @see JabberBuilder.JabberServer#start(String, String, String, String)
		 */
		private String serverName;

		/**
		 * The port of the Server as String, you may specify <a
		 * href="http://tools.ietf.org/html/rfc147">leading zeros</a>.
		 */
		private String serverPort;

		/**
		 * The .jks-Filename. Not-Null. For use in
		 * {@link FileInputStream#FileInputStream(String)}.
		 */
		private String absoluteCertificateFilename;

		/**
		 * The .jks-Filename keyphrase you entered in <a href=
		 * "http://docs.oracle.com/javase/6/docs/technotes/tools/windows/keytool.html"
		 * >keytool</a>.
		 */
		private String certificateKeyphrase;

		/**
		 * Constructor called on server-startup.
		 */
		public DescriptorImpl() {
			load();

			if (absoluteCertificateFilename != null
					&& certificateKeyphrase != null && serverName != null
					&& serverPort != null) {
				try {
					JabberServer server = JabberServer.getServer();

					if (server.isRunning()) {
						server.stop();
					}

					server.start(absoluteCertificateFilename,
							certificateKeyphrase, serverName, serverPort);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(
				final Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Jabber build results.";
		}

		@Override
		public boolean configure(final StaplerRequest req,
				final JSONObject formData) throws FormException {

			save();
			JabberServer server;
			try {
				server = JabberServer.getServer();

				if (server.isRunning()) {
					server.stop();
				}
				configureFromForm(formData);
				server.start(absoluteCertificateFilename, certificateKeyphrase,
						serverName, serverPort);
			} catch (Exception e) {
				throw new FormException(e.getMessage(), "certificateKeyphrase");
			}
			return super.configure(req, formData);
		}

		/**
		 * {@inheritDoc #configure(StaplerRequest)}.
		 * 
		 * @param formData
		 *            The Form-Data.
		 */
		private void configureFromForm(final JSONObject formData) {
			serverName = formData.getString("serverName");
			absoluteCertificateFilename = formData
					.getString("absoluteCertificateFilename");
			certificateKeyphrase = formData.getString("certificateKeyphrase");
			serverPort = formData.getString("serverPort");
		}

		/**
		 * The absolute Filename of the .jks-Certificate the XMPP use.
		 * 
		 * @return The absolute Filename.
		 */
		public String getAbsoluteCertificateFilename() {
			return absoluteCertificateFilename;
		}

		/**
		 * The Certificeate Keyphrase.
		 * 
		 * @return The Keyphrase for use in {@literal <input type="password" />}
		 */
		public String getCertificateKeyphrase() {
			return certificateKeyphrase;
		}

		/**
		 * The servername i.e.: <tt>jenkins.yourdomain.org</tt> .
		 * 
		 * @return The Name of the server.
		 */
		public String getServerName() {
			return serverName;
		}

		/**
		 * The Port of the Socket. May include leading zeros.
		 * 
		 * @return The Port of the Socket.
		 */
		public String getServerPort() {
			return serverPort;
		}

	}
}
