package de.e_nexus.jabber;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.authorization.AccountCreationException;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.authorization.UserAuthorization;

/**
 * Authorization of jenkins-jabber-users.
 */
public class JenkinsJabberUserAuthorization
		implements UserAuthorization, AccountManagement {

	/**
	 * The users registred.
	 */
	private final Set<JenkinsJabberEntityImpl> users = new TreeSet<JenkinsJabberEntityImpl>(
			new Comparator<JenkinsJabberEntityImpl>() {

				public int compare(final JenkinsJabberEntityImpl left,
						final JenkinsJabberEntityImpl right) {
					int result = left.getDomain().compareTo(right.getDomain());
					if (result != 0) {
						return result;
					}
					return left.getUsername().compareTo(right.getUsername());
				}
			});

	/**
	 * Adds a user.
	 * 
	 * @param user
	 *            The User.
	 * @param password
	 *            The password (not used, authorization is responsibility of
	 *            jenkins).
	 * @throws AccountCreationException
	 *             If the account could not be created.
	 */
	public final void addUser(final Entity user, final String password)
			throws AccountCreationException {
		users.add(castJJEI(user));
	}

	/**
	 * Cast the user to be a {@link JenkinsJabberEntityImpl}.
	 * <p>
	 * Throws a exception if the user is not a instance of a
	 * {@link JenkinsJabberEntityImpl}.
	 * 
	 * @param user
	 *            The user
	 * @return The {@link JenkinsJabberEntityImpl} instance.
	 * @throws AccountCreationException
	 *             If the user is not a instance of
	 *             {@link JenkinsJabberEntityImpl}.
	 */
	private JenkinsJabberEntityImpl castJJEI(final Entity user)
			throws AccountCreationException {
		try {
			return (JenkinsJabberEntityImpl) user;
		} catch (ClassCastException e) {
			throw new AccountCreationException(user.getClass() + " must be a "
					+ JenkinsJabberEntityImpl.class + "!", e);

		}
	}

	/**
	 * Changes the password.
	 * 
	 * @param user
	 *            The user.
	 * @param password
	 *            The new Password.
	 * @throws AccountCreationException
	 *             If the password could not be changed.
	 */
	public final void changePassword(final Entity user, final String password)
			throws AccountCreationException {
		throw new AccountCreationException(
				"This function is not yet supported!");
	}

	/**
	 * Verifies that the account exists.
	 * 
	 * @param jid
	 *            The entity.
	 * @return <code>true</code> if the account exists, <code>false</code> if
	 *         not.
	 */
	public final boolean verifyAccountExists(final Entity jid) {

		return searchByExample(jid) != null;
	}

	/**
	 * Finds the {@link JenkinsJabberEntityImpl} by description.
	 * 
	 * @param jid
	 *            The description.
	 * @return The {@link JenkinsJabberEntityImpl} of the user.
	 */
	private JenkinsJabberEntityImpl searchByExample(final Entity jid) {
		String username = jid.getNode();
		String domain = jid.getDomain();
		for (JenkinsJabberEntityImpl jenkinsJabberAccount : users) {
			if (jenkinsJabberAccount.getUsername().equals(username)) {
				if (jenkinsJabberAccount.getDomain().equals(domain)) {
					return jenkinsJabberAccount;
				}
			}
		}
		return null;
	}

	/**
	 * Checks for authorization.
	 * 
	 * @param jid
	 *            The entity.
	 * @param passwordCleartext
	 *            The plain password.
	 * @param credentials
	 *            The credentials.
	 * @return <code>true</code> if the user is authentic, <code>false</code> if
	 *         not.
	 */
	public final boolean verifyCredentials(final Entity jid,
			final String passwordCleartext, final Object credentials) {
		JenkinsJabberEntityImpl user = searchByExample(jid);
		if (user == null) {
			return false;
		}
		return user.checkPassword(passwordCleartext);
	}

	/**
	 * Checks for authorization.
	 * 
	 * @param username
	 *            The username.
	 * @param passwordCleartext
	 *            The plain password.
	 * @param credentials
	 *            The credentials.
	 * @return <code>true</code> if the user is authentic, <code>false</code> if
	 *         not.
	 */
	public final boolean verifyCredentials(final String username,
			final String passwordCleartext, final Object credentials) {
		for (JenkinsJabberEntityImpl user : users) {
			if (user.getFullQualifiedName().equals(username)) {
				return user.checkPassword(passwordCleartext);
			}
		}
		return false;
	}

	/**
	 * Gives the count of users.
	 * 
	 * @return The usercount.
	 */
	public String[] getUsernames() {
		JenkinsJabberEntityImpl[] u = users
				.toArray(new JenkinsJabberEntityImpl[0]);
		String[] names = new String[u.length];
		for (int i = 0; i < u.length; i++) {
			names[i] = u[i].getUsername();
		}
		return names;
	}

}
