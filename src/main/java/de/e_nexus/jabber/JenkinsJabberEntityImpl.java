package de.e_nexus.jabber;

import java.util.Map;
import java.util.Set;

import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityImpl;

import hudson.model.View.UserInfo;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.HudsonPrivateSecurityRealm.Details;

/**
 * A representation of a jabber user in jenkins.
 * 
 * <p>
 * The conventions of usernames may differ between Jenkins and Jabber/XMPP.
 * Often the usernames are equal, but sometimes they are not. If the usernames
 * are not equal we must find a unique representation of the username in
 * jenkins. If we must find a simmilar username they are ideally simmilar. But
 * then they may not be unique anymore, in this cornercase we have to add a
 * postfixed counting.
 * 
 * <p>
 * The password may also have a format that is not provided by the
 * jabber-client. In example, the jenkins-user have a password like
 * <code>abc</code> but the jabber-client requires a password that must have at
 * least 6 characters in length. Actually this code have a leak right here and i
 * must suggest to either change the password in jenkins or use a different
 * jabber-client. Anyway, the responsibility of communication (of the password
 * mismatch) is in the jabber-client. This plugin is not allowed to write such a
 * message into the log!
 * 
 * @author peter.rader
 */
// Final, dont inherit or proxy, only compose or decorate or something else
// because i do not trust scriptkiddies (if you read this, i guess, you are not
// a scriptkiddie).
public final class JenkinsJabberEntityImpl extends EntityImpl {

	/**
	 * Used to compare the password only. The Username of jenkins may not match
	 * the username of jabber.
	 */
	private final Details jenkinsDetails;

	/**
	 * Creates a instance of {@link JenkinsJabberEntityImpl} once per
	 * authentication.
	 * 
	 * <p>
	 * <b>Note: </b> The bareId may contains a password that is not used!
	 * 
	 * @param hostname
	 *            The hostname.
	 * @param userInfo
	 *            The real Password.
	 */
	public JenkinsJabberEntityImpl(Set<String> users, String hostname,
			final UserInfo userInfo) {
		super(generateUsername(users, userInfo), hostname, null);
		this.jenkinsDetails = userInfo.getUser().getProperty(Details.class);
	}

	/**
	 * Generates a unique username.
	 * 
	 * @param users
	 *            The users.
	 * @param userInfo
	 * @return
	 */
	private static String generateUsername(Set<String> users,
			final UserInfo userInfo) {
		String usernameUncounted = userInfo.getUser().getId().toLowerCase();
		if (usernameUncounted.indexOf("@") > 2) {
			usernameUncounted = usernameUncounted.substring(0,
					usernameUncounted.indexOf("@"));
		}

		if (!users.contains(usernameUncounted)) {
			return usernameUncounted;
		}

		long i = 0;
		while (i != Long.MAX_VALUE - 1) {
			i++;
			String usernameCounted = usernameUncounted + i;
			if (!users.contains(usernameCounted)) {
				return usernameCounted;
			}
		}
		throw new ArrayIndexOutOfBoundsException("No more usernames to try.");
	}

	/**
	 * Checks if the Password is correct.
	 * 
	 * @param plaintextPassword
	 *            The Password comming from the jabber-client.
	 * @return <code>true</code> if the Password is correct, <code>false</code>
	 *         if not.
	 */
	public boolean checkPassword(final String plaintextPassword) {
		return HudsonPrivateSecurityRealm.PASSWORD_ENCODER.isPasswordValid(
				jenkinsDetails.getPassword(), plaintextPassword, null);
	}

	/**
	 * Returns if you can use the jabber's change-password function to change
	 * the password of the jenkins user.
	 * 
	 * @return <code>true</code> if jabber can change the password of the
	 *         jenkins user, <code>false</code> if not.
	 */
	public boolean canJabberChangePassword() {
		return false;
	}

	/**
	 * Returns the Username for jabber, may not the jenkins username.
	 * 
	 * @return The Username.
	 */
	public String getUsername() {
		return getNode();
	}
}
