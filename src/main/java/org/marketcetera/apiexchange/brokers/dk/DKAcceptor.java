package org.marketcetera.apiexchange.brokers.dk;

import java.io.FileNotFoundException;

import org.marketcetera.apiexchange.APIAcceptor;
import org.marketcetera.apiexchange.quickfix.FIXConfigurationLoader;
import org.marketcetera.apiexchange.quickfix.FIXFactory;

import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.FieldConvertError;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.LogFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.RuntimeError;
import quickfix.Session;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;

/**
 * FIX market data acceptor. Initialises the FIX server accepting market data
 * requests.
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 *
 */
public class DKAcceptor implements APIAcceptor
{
	private SocketAcceptor acceptor;
	private String acceptorPassword;
	private SessionSettings acceptorSessionSettings;
	private AcceptorApp appAcceptor;
	
	private String dkJnlpUrl;
	private String dkUserName;
	private String dkPassword;

	/**
	 * Create an instance
	 * @param configFilePath File path for the DIX server configuration
	 * @param string 
	 * @throws ConfigError
	 * @throws FileNotFoundException
	 */
	public DKAcceptor(String configAcceptor, String dkconfig)
	throws ConfigError, FileNotFoundException
	{
		acceptorSessionSettings =
			FIXConfigurationLoader.loadFixConfiguration(configAcceptor);
		acceptorPassword = FIXConfigurationLoader.getProperty(configAcceptor, "Password");
		
		dkJnlpUrl = FIXConfigurationLoader.getProperty(dkconfig, "jnlpUrl");
		dkUserName = FIXConfigurationLoader.getProperty(dkconfig, "userName");
		dkPassword = FIXConfigurationLoader.getProperty(dkconfig, "password");
	}

	/**
	 * Initialises the socket acceptor, from Application class, message and log factories.
	 * @param settings SessionSettings for the current session
	 * @throws ConfigError
	 * @throws FieldConvertError
	 */
	public void initializeSocketAcceptor() throws ConfigError, FieldConvertError
	{
		appAcceptor= new AcceptorApp(acceptorSessionSettings, acceptorPassword, dkJnlpUrl, dkUserName, dkPassword);
		MessageStoreFactory msgStoreFactory = new FileStoreFactory(acceptorSessionSettings);
		LogFactory logFactory = new FileLogFactory(acceptorSessionSettings);//new ScreenLogFactory(false, false, false);
		MessageFactory msgFactory = new DefaultMessageFactory();

		acceptor = new SocketAcceptor(appAcceptor, msgStoreFactory, acceptorSessionSettings,
				logFactory, msgFactory);
	}
	
	/**
	 * Initialises the socket and starts the server.
	 * @throws RuntimeError
	 * @throws ConfigError
	 * @throws FieldConvertError
	 */
	public void start() throws RuntimeError, ConfigError, FieldConvertError
	{
		initializeSocketAcceptor();
		acceptor.start();
		
		Session trade = null;
		Session data = null;
		//Trade Session
		for(Session session:acceptor.getManagedSessions()){
			if(session.getSessionID().getTargetCompID().contains("ORS")){
				trade = session;
				break;
			}
		}
		//Data Session
		for(Session session:acceptor.getManagedSessions()){
			if(session.getSessionID().getTargetCompID().contains("MDS")){
				data = session;
				break;
			}
		}
		appAcceptor.setFIXSessions(trade, data);
		
		FIXFactory.InfoLogging("DK Acceptor Server is running...");
	}

	/**
	 * Stops the server completely. Tries to log out any connected initiator
	 * first.
	 */
	public void stop()
	{
		acceptor.stop();
	}
}