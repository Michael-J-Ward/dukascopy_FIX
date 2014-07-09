package org.marketcetera.apiexchange;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.marketcetera.apiexchange.brokers.dk.DKAcceptor;
import org.marketcetera.apiexchange.quickfix.FIXFactory;
import org.marketcetera.core.ApplicationBase;

import quickfix.ConfigError;


/**
 * Main program entry
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 *
 */
public class Main extends ApplicationBase{
	
	private static final String DK_APP=
	        CONF_DIR+"brokers/dk/"; //$NON-NLS-1$

	
	
	/**
	 * Application entry point.
	 * Loads the {@link APIExchange} from the Spring configuration.
	 * @param args
	 */
	public static void main(String[] args) {
		List<APIAcceptor> acceptors = new ArrayList<APIAcceptor>();
		
		try {
			
			// Dukascopy
			acceptors.add(new DKAcceptor(DK_APP + "Acceptor.properties", DK_APP + "Account.properties"));
			
		} catch (FileNotFoundException e) {
			FIXFactory.ErrorLogging("File not found: " + e.toString());
		} catch (ConfigError e) {
			FIXFactory.ErrorLogging("The configuration file is bogus: " + e.toString());
		}
		
		final APIExchange apiExchange = new APIExchange(acceptors);
		apiExchange.runExchange();		
		
		// Hook to log shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                apiExchange.stopServers();
            }
        });
	}
}