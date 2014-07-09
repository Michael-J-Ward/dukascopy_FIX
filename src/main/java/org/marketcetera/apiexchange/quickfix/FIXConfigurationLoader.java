package org.marketcetera.apiexchange.quickfix;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import quickfix.SessionSettings;

/**
 * Methods related to load configuration files and create SessionSettings.
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 */
public class FIXConfigurationLoader {
	
	/**
	 * Loads configuration from given file path.
	 * @param configFilePath file path of config file.
	 * @return SessionSettings instance generated from config file
	 * @throws FileNotFoundException
	 */
	public static SessionSettings loadFixConfiguration(String configFilePath)
	throws FileNotFoundException
	{
		SessionSettings settings = null;
		try{
			InputStream inputStream = new FileInputStream(configFilePath);
			settings = new SessionSettings(inputStream);
			inputStream.close();
		}
		catch(Exception e){
			FIXFactory.ErrorLogging("loadFixConfiguration ERROR: " +e);
		}
		return settings;
	}
	
	/**
	 * Get the password that manage this acceptor.
	 * @param configFilePath File path for the FIX server configuration
	 */
	public static String getProperty(String configFilePath, String tag) throws FileNotFoundException {
		String inPassword = "";
		Properties prop = new Properties();
	    try {
	    	InputStream inputStream = new FileInputStream(configFilePath);
			prop.load(inputStream);
			inPassword = prop.getProperty(tag);
			inputStream.close();
		} catch (IOException e) {
			FIXFactory.ErrorLogging("getProperty ERROR: " +e);
		}
	    return inPassword;
	}
}