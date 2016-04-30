package de.e_nexus.jabber;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.View.People;
import hudson.model.View.UserInfo;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * The Builder itself, used to start/stop the Server, create the user's and
 * connect them together. This class itself is thread-save, the inner static
 * Class {@link JabberServer} is not thread-save. Therefore you shall only have
 * one instance of the {@link Builder}.
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
	 * The Logger.
	 */
	private static final Logger LOG = Logger.getLogger(JabberBuilder.class.getSimpleName());

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
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
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
			if (build.getChangeSet() != null && build.getChangeSet().getItems() != null) {
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
		 * The constructor registeres the shutdown-hook for clean exit.
		 */
		public JabberServer() {
			LOG.log(Level.FINER, "Register the StopOnShutdown-Hook.");
			Runtime.getRuntime().addShutdownHook(new StopOnShutdown(this, "Stop Jabber/XMPP on Shutdown"));
		}

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
		 *            The message to broadcast, not null and
		 *            <a href="http://www.rfc-editor.org/rfc/rfc6120.txt">\n\r
		 *            as line-break</a>.
		 * @throws NullPointerException
		 *             If the Server stops meanwhile or some other
		 *             undocumentated cases.
		 */
		public void broadcast(final String message) throws NullPointerException {
			assert users != Collections.EMPTY_LIST;
			if (server == null) {
				throw new IllegalStateException("Server has not yet been started.",
						new NullPointerException("The field 'server' is null."));
			}
			ServerRuntimeContext serverRuntimeContext = server.getServerRuntimeContext();
			synchronized (users) {

				for (EntityImpl recivient : users.values()) {
					java.util.List<SessionContext> sessions = serverRuntimeContext.getResourceRegistry()
							.getSessions(recivient);
					StanzaBuilder sb = StanzaBuilder.createMessageStanza(recivient, recivient,
							MessageStanzaType.HEADLINE, "html", message);

					for (SessionContext sessionContext : sessions) {

						Stanza s = new MessageStanza(sb.build());

						SessionState state = sessionContext.getState();
						SessionStateHolder ssh = new SessionStateHolder();
						ssh.setState(state);
						serverRuntimeContext.getStanzaProcessor().processStanza(serverRuntimeContext, sessionContext, s,
								ssh);

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
		private JenkinsJabberUserAuthorization jenkinsAuthroizationForJabber;
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
		public synchronized void start(final String certAbsoluteFilename, final String certKey, final String hostname,
				final String serverPort) throws Exception {
			LOG.log(Level.FINER, "Start save Jabber settings.");
			try {
				startJabber(certAbsoluteFilename, certKey, hostname, serverPort);
			} catch (Exception e) {
				server = null;
				endpoint = null;
				providerRegistry = null;
				jenkinsAuthroizationForJabber = null;
				throw e;
			}
		}

		/**
		 * @see #start(String, String, String, String)
		 */
		private void startJabber(final String certAbsoluteFilename, final String certKey, final String hostname,
				final String serverPort) throws Exception {

			LOG.log(Level.FINER, "Create a stream to the certificate-file.");
			FileInputStream fileInputStream = new FileInputStream(certAbsoluteFilename);
			LOG.log(Level.FINER, "Start new identity storage.");
			providerRegistry = new OpenStorageProviderRegistry();
			LOG.log(Level.FINER, "Start new user authorization.");
			jenkinsAuthroizationForJabber = new JenkinsJabberUserAuthorization();
			if (users == Collections.EMPTY_MAP) {
				LOG.log(Level.FINER, "Old users collection not yet set, create new.");
				users = new HashMap<String, EntityImpl>();
			} else {
				LOG.log(Level.FINE, "Old users collection has already" + " been set, clear it now.");
				users.clear();
			}
			// add users
			LOG.log(Level.FINE, "Get userlist from jenkins-ci.");
			People people = Jenkins.getInstance().getPeople();
			LOG.log(Level.FINER, "Will loop " + people.users.size() + " jenkins-ci's user.");
			for (UserInfo userInfo : people.users) {
				LOG.log(Level.FINER, "Create unique name for user " + userInfo);

				JenkinsJabberEntityImpl jabberJenkinsUser = new JenkinsJabberEntityImpl(users.keySet(), hostname,
						userInfo);
				LOG.log(Level.FINER,
						"Unique name is: '" + jabberJenkinsUser.getUsername() + "', create new jabber-user ...");
				LOG.log(Level.FINER,
						"Jabber-user has been created:'" + jabberJenkinsUser + "', add it to the users collection.");
				users.put(jabberJenkinsUser.getUsername(), jabberJenkinsUser);
				LOG.log(Level.FINER, "Add it to the user authorization.");
				jenkinsAuthroizationForJabber.addUser(jabberJenkinsUser, jabberJenkinsUser.getUsername());
			}
			LOG.log(Level.FINER, "Loop done, add the user " + "authroization to the jabber-registry.");
			providerRegistry.add(jenkinsAuthroizationForJabber);
			LOG.log(Level.FINER, "Register the memory-roster.");
			MemoryRosterManager memoryRosterManager = new MemoryRosterManager();
			providerRegistry.add(memoryRosterManager);
			LOG.log(Level.FINE, "Create the Jabber-Server using hostname: '" + hostname + "'.");
			server = new XMPPServer(hostname);
			LOG.log(Level.FINER, "Create a TCP-Endpoint.");
			endpoint = new TCPEndpoint();
			LOG.log(Level.FINER, "Set the endpoint's port to " + serverPort + ".");
			endpoint.setPort(Integer.parseInt(serverPort));
			LOG.log(Level.FINER, "Add the endpoint to the Server.");
			server.addEndpoint(endpoint);
			LOG.log(Level.FINER, "Register the certificate-stream to the Jabber-Server.");
			server.setTLSCertificateInfo(fileInputStream, certKey);
			LOG.log(Level.FINE, "Handshake all users ...");
			for (EntityImpl left : users.values()) {
				LOG.log(Level.FINER, "Take hand of " + left);
				for (EntityImpl right : users.values()) {
					if (left != right) {
						LOG.log(Level.FINER, "Take hand of " + right);

						RosterItem d = new RosterItem(right, SubscriptionType.BOTH);
						LOG.log(Level.FINER, "Handshake!");
						memoryRosterManager.addContact(left, d);
					}
				}
			}
			LOG.log(Level.FINER, "Register the storage provider.");
			server.setStorageProviderRegistry(providerRegistry);
			LOG.log(Level.FINER, "Start the Jabber-Server.");
			server.start();
			LOG.log(Level.FINER, "Add module for receive Jenkins commands.");
			server.addModule(new JenkinsConversationModule(jenkinsAuthroizationForJabber));
			LOG.log(Level.FINER, "Add module for VCart-Fake.");
			server.addModule(new JabberVCardModule(users));
			LOG.log(Level.FINER, "Add module for Software-Version.");
			server.addModule(new SoftwareVersionModule());
			LOG.log(Level.FINE, "Initialization done, ready for connect!");

		}

		/**
		 * Checks the certificate for validity.
		 * 
		 * @param certKey
		 *            The key of the Certificate
		 * @param hostname
		 *            The hostname/alias this certificate is for.
		 * @param fileInputStream
		 *            The Stream of the Certificate.
		 * @throws KeyStoreException
		 *             This shall never been thrown. Bug in JKS i think.
		 * @throws IOException
		 *             If the file can not be read.
		 * @throws NoSuchAlgorithmException
		 *             If the format of the .jks is wrong.
		 * @throws CertificateException
		 *             If the Certificate can not be loaded.
		 * @throws FormException
		 *             Internal certificate mismatch, wrong password or
		 *             out-dated.
		 */
		private void checkCertificate(final String certKey, final String hostname,
				final FileInputStream fileInputStream)
				throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, FormException {
			LOG.log(Level.FINER, "Initiate the keystore-instance.");
			KeyStore s = KeyStore.getInstance(KeyStore.getDefaultType());
			LOG.log(Level.FINE, "Load the certificate using the certificate-password.");
			try {
				s.load(fileInputStream, certKey.toCharArray());
			} catch (IOException e) {
				if (e.getCause() instanceof UnrecoverableKeyException) {
					throw new FormException("Password is wrong.", "absoluteCertificateFilename");
				}
				throw e;
			}
			LOG.log(Level.FINE, "Get the certificate by the alias(" + hostname + ").");

			Certificate certificate = s.getCertificate(hostname);
			X509Certificate cert = (X509Certificate) certificate;
			if (certificate instanceof X509Certificate) {
				LOG.log(Level.FINER, "Certificate is an X509Certificate.");
				DateFormat dateFormat = SimpleDateFormat.getDateInstance(DateFormat.MEDIUM);
				Date now = new Date(System.currentTimeMillis());
				if (cert.getNotAfter().before(now)) {
					throw new FormException(
							"Certificate out-of-date! Valid from " + dateFormat.format(cert.getNotBefore()) + "-"
									+ dateFormat.format(cert.getNotAfter()) + "!",
							"absoluteCertificateFilename");
				}
				if (cert.getNotBefore().after(now)) {
					throw new FormException(
							"Certificate premature! Valid from " + dateFormat.format(cert.getNotBefore()) + "-"
									+ dateFormat.format(cert.getNotAfter()) + "!",
							"absoluteCertificateFilename");
				}
			} else {
				if (certificate == null) {
					throw new FormException("Alias is not found in the .jks or it is not a certificate!",
							"absoluteCertificateFilename");
				} else {
					PublicKey pk = certificate.getPublicKey();
					throw new FormException(
							"Key not supported: X509Certificate expected but .jks contains " + certificate + "!",
							"absoluteCertificateFilename");
				}
			}
		}

		/**
		 * Stops the XMPP-Server.
		 * 
		 * @throws IllegalStateException
		 *             If already stopped.
		 */
		public synchronized void stop() throws IllegalStateException {
			if (endpoint == null) {
				throw new IllegalStateException("Endpoint not been started.", new NullPointerException());
			}
			if (server == null) {
				throw new IllegalStateException("Server not been started, but Endpoint!?", new NullPointerException());
			}
			endpoint.stop();
			endpoint = null;
			server.stop();
			server = null;
			providerRegistry = null;
			jenkinsAuthroizationForJabber = null;
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
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * The ServerName to use as JID-Server-Part without Resource-Part.
		 * 
		 * @see JabberBuilder.JabberServer#start(String, String, String, String)
		 */
		private String serverName;

		/**
		 * The port of the Server as String, you may specify
		 * <a href="http://tools.ietf.org/html/rfc147">leading zeros</a>.
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
			new Thread() {
				public void run() {
					try {
						sleep(10000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					if (absoluteCertificateFilename != null && certificateKeyphrase != null && serverName != null
							&& serverPort != null) {
						try {
							JabberServer server = JabberServer.getServer();

							if (server.isRunning()) {
								server.stop();
							}

							server.start(absoluteCertificateFilename, certificateKeyphrase, serverName, serverPort);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				};
			}.start();
		}

		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Jabber build results.";
		}

		@Override
		public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {

			save();
			JabberServer server;
			try {
				configureFromForm(formData);
				server = JabberServer.getServer();
				FileInputStream fileInputStream = new FileInputStream(absoluteCertificateFilename);
				server.checkCertificate(certificateKeyphrase, serverName, fileInputStream);

				if (server.isRunning()) {
					server.stop();
				}

				server.start(absoluteCertificateFilename, certificateKeyphrase, serverName, serverPort);
			} catch (Exception e) {
				if (e instanceof FileNotFoundException) {
					throw new FormException(
							"Certificate not found for the jabber-server! If you have no certificate deactivate the Jabber Server plugin please.",
							"certificateKeyphrase");
				}
				throw new FormException("Jabber Server plugin reported: " + e.getMessage(), "certificateKeyphrase");
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
			absoluteCertificateFilename = formData.getString("absoluteCertificateFilename");
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
