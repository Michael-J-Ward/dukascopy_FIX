package org.marketcetera.apiexchange;

import org.marketcetera.util.log.I18NLoggerProxy;
import org.marketcetera.util.log.I18NMessage0P;
import org.marketcetera.util.log.I18NMessageProvider;
import org.marketcetera.util.misc.ClassVersion;

/**
 * The internationalisation constants used by this package.
 *
 * @author Otmane El Rhazi
 * @since 1.0.0
 * @version $Id: Messages.java 16339 2014-07-09 15:59:24Z elrhazi $
 */

/* $License$ */

@ClassVersion("$Id: Messages.java 16339 2014-07-09 15:59:24Z elrhazi $")
public interface Messages
{
    /**
     * The message provider.
     */

    static final I18NMessageProvider PROVIDER= 
        new I18NMessageProvider("apiexchange_dk");  //$NON-NLS-1$

    /**
     * The logger.
     */

    static final I18NLoggerProxy LOGGER= 
        new I18NLoggerProxy(PROVIDER);

    /*
     * The messages.
     */
    
    static final I18NMessage0P ERROR_LOGON_WRONGPASSWORD=
            new I18NMessage0P(LOGGER,"error_logon_wrongpassword"); //$NON-NLS-1$
    
    static final I18NMessage0P ERROR_LOGON_DKNOTCONNECTED=
            new I18NMessage0P(LOGGER,"error_logon_dknotconnected"); //$NON-NLS-1$
    
}
