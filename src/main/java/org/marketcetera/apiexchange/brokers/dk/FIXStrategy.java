package org.marketcetera.apiexchange.brokers.dk;

import java.util.Set;

import quickfix.Message;
import quickfix.Session;

import com.dukascopy.api.*;
import com.dukascopy.api.system.IClient;

/**
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 */
public interface FIXStrategy extends IStrategy {
    public String getFIXSessionType();
    
    public Set<Message> getFIXRequests();
    
    public IClient getClient();
    
    public void setFIXSession(Session inSession);
    
    public Session getFIXSession();
    
    public String getAccountId();
    
    public IEngine getEngine();
}