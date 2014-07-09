package org.marketcetera.apiexchange.brokers.dk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.marketcetera.apiexchange.Messages;
import org.marketcetera.apiexchange.quickfix.FIXFactory;
//import org.marketcetera.client.brokers.BrokerStatus.SessionType;
import org.marketcetera.quickfix.FIXMessageUtil;

import quickfix.Application;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.UnsupportedMessageType;
import quickfix.field.Password;
import quickfix.fix44.Logon;

import com.dukascopy.api.IStrategy;
import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;

/**
 * Quickfixj Application implementation for the market data server.
 * All messages to the server goes through here and methods are implemented to
 * handle all supported messages.
 * FIX 4.4
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 * 
 */
public class AcceptorApp extends quickfix.MessageCracker
implements Application
{
	private String acceptorPassword;
	
	private String dkJnlpUrl;
	private String dkUserName;
	private String dkPassword;
	private IClient client = null;
	
	Session dataSession;
    private DataStrategy dkDataStrategy;
    Session tradeSession;
    private TradeStrategy dkTradeStrategy;	

	/**
	 * Creates an instance of the server with the given session settings and
	 * password.
	 * @param settings a <code>SessionSettings</code> Session Settings
	 * @param inAcceptorPassword a <code>String</code> password for this session
	 * @param inDKJnlpUrl a <code>String</code> jnlpUrl for Dukascopy server
	 * @param inDKUserName a <code>String</code> username for Dukascopy account
	 * @param inDKPassword a <code>String</code> password for Dukascopy account
	 */
	public AcceptorApp(SessionSettings settings, String inAcceptorPassword, String inDKJnlpUrl, String inDKUserName, String inDKPassword)
	{		
		acceptorPassword = inAcceptorPassword;
		
		dkJnlpUrl = inDKJnlpUrl;
		dkUserName = inDKUserName;
		dkPassword = inDKPassword;
		
		FIXFactory.InfoLogging("DK Application is starting...");
		try {			
			// Get the instance of the IClient interface
			client = ClientFactory.getDefaultInstance();
			
			// Create the Dukascopy Data strategy
			dkDataStrategy = new DataStrategy(client);
			
			// Create the Dukascopy Data strategy
			dkTradeStrategy = new TradeStrategy(client);
		} catch (Exception e) {
			FIXFactory.ErrorLogging("DK Application. Error connecting to Dukascopy.");
		} 
	}
	
	private void connectDK() throws Exception {	

		// Stop running strategies
		for (Entry<Long, IStrategy> e : client.getStartedStrategies()
				.entrySet()) {
			client.stopStrategy(e.getKey());
		}

        //set the listener that will receive system events
        client.setSystemListener(new ISystemListener() {
	        	@Override
	        	public void onStart(long processId) {
	                FIXFactory.DebugLogging("DK strategy started: " + processId);
	        	}

				@Override
				public void onStop(long processId) {
	                FIXFactory.DebugLogging("DK strategy stopped: " + processId);
				}

				@Override
				public void onConnect() {
	                FIXFactory.DebugLogging("DK client connected");
				}

				@Override
				public void onDisconnect() {
	                FIXFactory.DebugLogging("DK client disconnected");
            		try {
            			if(tradeSession.isLoggedOn())
            			{
            				tradeSession.disconnect(Messages.ERROR_LOGON_DKNOTCONNECTED.getText(), true);
            			}
            			if(dataSession.isLoggedOn())
            			{
            				dataSession.disconnect(Messages.ERROR_LOGON_DKNOTCONNECTED.getText(), true);
            			}
					} catch (IOException e) {
						FIXFactory.ErrorLogging("DK client disconnection error: " + e.getMessage()); 
					}
				}
			});

	    FIXFactory.InfoLogging("Connecting to Dukascopy..."); 
	    
        //connect to the server using jnlp, user name and password
        client.connect(dkJnlpUrl, dkUserName, dkPassword);
        
        //wait for it to connect
        int i = 100; //wait max ten seconds
        while (i > 0 && !client.isConnected()) {
            Thread.sleep(100);
            i--;
        }
        if (!client.isConnected()) {
            FIXFactory.ErrorLogging("Failed to connect Dukascopy servers"); 
        }else{
        	FIXFactory.InfoLogging("Starting strategies...");
	        client.startStrategy(dkDataStrategy);
	        client.startStrategy(dkTradeStrategy);
        }
	}

	/**
	 * Notifies when a session is created.
	 */
	public void onCreate(SessionID sessionID)
	{
	}

	/**
	 * Notifies on valid logon
	 */
	public void onLogon(SessionID sessionID)
	{
		FIXFactory.InfoLogging("FIX Login: " + sessionID);
	}

	/**
	 * Notifies when a connections is closed
	 */
	public void onLogout(SessionID sessionID) {
		FIXFactory.InfoLogging("FIX Logout: " + sessionID);
	}

	/**
	 * Notifies on admin messages to the connected client
	 */
	public void toAdmin(Message msg, SessionID sessionID)
	{
		FIXFactory.DebugLogging("FIX toAdmin: " + msg);
	}

	/**
	 * Notifies on admin messages from the connected client
	 */
	public void fromAdmin(Message msg, SessionID sessionID)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			RejectLogon {
		
		FIXFactory.DebugLogging("FIX fromAdmin: " + msg);		
		
		if (FIXMessageUtil.isLogon(msg)) {
			Logon logon = (Logon) msg;
			if (!acceptorPassword.equals(logon.getString(Password.FIELD))) {
				System.out.println( acceptorPassword+ " " +logon.getString(Password.FIELD));
				FIXFactory.ErrorLogging("ERROR: " + sessionID + " "
						+ Messages.ERROR_LOGON_WRONGPASSWORD.getText());
				throw new RejectLogon(Messages.ERROR_LOGON_WRONGPASSWORD.getText());
			} else {
				if (!client.isConnected()) {
					try {
						connectDK();
					} catch (Exception e) {
						FIXFactory.ErrorLogging("ERROR: " + sessionID + " "
								+ Messages.ERROR_LOGON_DKNOTCONNECTED.getText());
						throw new RejectLogon(
								Messages.ERROR_LOGON_DKNOTCONNECTED.getText());
					}
					if (!client.isConnected()) {
						FIXFactory.ErrorLogging("ERROR: " + sessionID + " "
								+ Messages.ERROR_LOGON_DKNOTCONNECTED.getText());
						throw new RejectLogon(
								Messages.ERROR_LOGON_DKNOTCONNECTED.getText());
					}
				}
			}
		}
	}

	/**
	 * Messages to client goes through here.
	 */
	public void toApp(Message msg, SessionID sessionID)
	throws DoNotSend
	{
		FIXFactory.DebugLogging("FIX toApp: " + msg);
	}

	/**
	 * Messages from client goes through here.
	 */
	public void fromApp(Message msg, SessionID sessionID) throws FieldNotFound,
			IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
		
		FIXFactory.DebugLogging("fromApp " + msg);
		
		if (FIXMessageUtil.isMarketDataRequest(msg)) {
			dkDataStrategy.newDKMarketDataRequest(msg);
		}
		
		if (FIXMessageUtil.isOrderSingle(msg) ||
				FIXMessageUtil.isCancelReplaceRequest(msg) ||
				FIXMessageUtil.isCancelRequest(msg)){
			
			// hold keys for running trade strategies
			List<Long> keys = new ArrayList<Long>();
			for (Entry<Long, IStrategy> e : client.getStartedStrategies()
					.entrySet()) {
				if(e.getValue() instanceof FIXStrategy){
					if(((FIXStrategy)e.getValue()).getFIXSessionType().equals("TRADE")){
						keys.add(e.getKey());
					}
				}
			}
			dkTradeStrategy.newTradeOrderRequest(msg);
			client.startStrategy(dkTradeStrategy);
			
			// Stop running trade strategies
			for(long key:keys){
				client.stopStrategy(key);
			}
		}
	}

	public void setFIXSessions(Session inTradeSession, Session inDataSession) {
		tradeSession = inTradeSession;
		dkTradeStrategy.setFIXSession(inTradeSession);
		dataSession = inDataSession;
		dkDataStrategy.setFIXSession(inDataSession);
	}
}