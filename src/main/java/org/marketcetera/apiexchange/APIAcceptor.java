package org.marketcetera.apiexchange;

import quickfix.ConfigError;
import quickfix.FieldConvertError;
import quickfix.RuntimeError;

/**
 * FIX market data acceptor. Initialises the FIX server accepting market data
 * requests.
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 *
 */
public interface APIAcceptor{
	/**
	 * Initialises the socket acceptor, from Application class, message and log factories.
	 * @param settings SessionSettings for the current session
	 * @throws ConfigError
	 * @throws FieldConvertError
	 */
	void initializeSocketAcceptor() throws ConfigError, FieldConvertError;

	/**
	 * Initialises the socket and starts the server.
	 * @throws RuntimeError
	 * @throws ConfigError
	 * @throws FieldConvertError
	 */
	public void start() throws RuntimeError, ConfigError, FieldConvertError;

	/**
	 * Stops the server completely. Tries to log out any connected clients
	 * first.
	 */
	public void stop();
}