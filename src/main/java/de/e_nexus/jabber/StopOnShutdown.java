package de.e_nexus.jabber;

final class StopOnShutdown extends Thread {
	private JabberBuilder.JabberServer jabberServer;

	StopOnShutdown(JabberBuilder.JabberServer jabberServer, String name) {
		super(name);
		this.jabberServer = jabberServer;
	}

	@Override
	public void run() {
		jabberServer.stop();
	}
}