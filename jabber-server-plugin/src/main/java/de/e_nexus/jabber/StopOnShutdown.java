package de.e_nexus.jabber;

import de.e_nexus.jabber.JabberBuilder.JabberServer;

final class StopOnShutdown extends Thread {
	/**
	 * 
	 */
	private final JabberBuilder.JabberServer shutdownThread;
	private JabberBuilder.JabberServer jabberServer;

	StopOnShutdown(JabberBuilder.JabberServer jabberServer, String name) {
		super(name);
		shutdownThread = jabberServer;
	}

	@Override
	public void run() {
		jabberServer.stop();
	}

	public Thread setBuilder(JabberBuilder.JabberServer jabberServer) {
		this.jabberServer = jabberServer;
		return this;
	}
}