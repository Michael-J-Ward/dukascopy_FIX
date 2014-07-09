package org.marketcetera.apiexchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.marketcetera.apiexchange.quickfix.FIXFactory;

import quickfix.ConfigError;
import quickfix.FieldConvertError;
import quickfix.RuntimeError;

/**
 * Exchange object to start the exchange. Starts a FIX 4.4 server for the market data,
 * and FIX 4.4 server that accept orders.
 * 
 * Manage market data and trades acceptors.
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 * 
 */
public class APIExchange{
	
	private final List<APIAcceptor> m_Acceptor;

	/**
	 * Initialises the object with the given parameters.
	 * @param acceptors Instances of the FIX market data or order acceptor.
	 */
	public APIExchange(
			List<APIAcceptor> acceptors) {
		this.m_Acceptor = new ArrayList<APIAcceptor>();
		this.m_Acceptor.addAll(acceptors);
	}
	
	/**
	 * Start the exchange, including the data load and FIX server
	 */
	public void runExchange() {
			try {
				for(APIAcceptor acceptor:m_Acceptor){
					acceptor.start();
				}
				System.in.read();
			} catch (ConfigError e) {
				FIXFactory.ErrorLogging("Quickfix configuration error: " + e.toString());
			} catch (RuntimeError e) {
				FIXFactory.ErrorLogging("Quickfix runtime error: " + e.toString());
			} catch (FieldConvertError e) {
				FIXFactory.ErrorLogging("Quickfix field conversion error: " + e.toString());
			} catch (IOException e) {
				FIXFactory.ErrorLogging("Uknown Error: " + e.toString());
			}
	}
	
	/**
	 * Stops the FIX servers.
	 */
	protected void stopServers() {
		for(APIAcceptor acceptor:m_Acceptor){
			acceptor.stop();
		}
		System.exit(0);
	}
}