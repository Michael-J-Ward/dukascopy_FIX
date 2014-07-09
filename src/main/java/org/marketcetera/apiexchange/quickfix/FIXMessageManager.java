package org.marketcetera.apiexchange.quickfix;

import quickfix.DataDictionaryProvider;
import quickfix.FixVersions;
import quickfix.LogUtil;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.field.ApplVerID;

/**
 * General manager that sends FIX messages to through the correct session.
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 * 
 */
public class FIXMessageManager {

	/**
	 * Get the application version ID from the session or message,
	 * depending on the session id.
	 * @param session The session to get the ID from.
	 * @param message The message to get the ID from.
	 * @return
	 */
	private static ApplVerID getApplVerID(Session session, Message message) {
		String beginString = session.getSessionID().getBeginString();
		if (FixVersions.BEGINSTRING_FIXT11.equals(beginString)) {
			return new ApplVerID(ApplVerID.FIX50);
		} else {
			return MessageUtils.toApplVerID(beginString);
		}
	}

	/**
	 * Sends the given FIX message to the correct receiver based on the session ID.
	 * @param sessionID Session id specifying receiver
	 * @param message FIX message to send.
	 */
	public static void sendMessage(SessionID sessionID, Message message) {
		try {
			Session session = Session.lookupSession(sessionID);
			if (session == null) {
				throw new SessionNotFound(sessionID.toString());
			}

			DataDictionaryProvider dataDictionaryProvider = session.getDataDictionaryProvider();
			if (dataDictionaryProvider != null) {
				try {
					dataDictionaryProvider.getApplicationDataDictionary(
							getApplVerID(session, message)).validate(message, true);
				} catch (Exception e) {
					LogUtil.logThrowable(sessionID, "Outgoing message failed validation: "
							+ e.getMessage(), e);
					return;
				}
			}

			session.send(message);
		} catch (SessionNotFound e) {
			FIXFactory.ErrorLogging("Could not find the session to send the message: " + message.toXML());
		}
	}
}