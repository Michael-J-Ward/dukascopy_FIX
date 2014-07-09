package org.marketcetera.apiexchange.brokers.dk;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.marketcetera.apiexchange.quickfix.FIXFactory;

import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.Session;

import com.dukascopy.api.*;
import com.dukascopy.api.IMessage.Type;
import com.dukascopy.api.system.IClient;

/**
 * Dukascopy trade orders strategy. Send orders, receive execution reports
 * 
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 *
 */
public class TradeStrategy implements FIXStrategy {

	private IClient client;
	private IAccount account;
	private IEngine engine;

	Session session;
	private final Set<Message> mRequests;
	private final Set<Message> messagesToProcess;

	TradeStrategy(IClient inClient) {
		client = inClient;
		mRequests = new CopyOnWriteArraySet<Message>();
		messagesToProcess = new CopyOnWriteArraySet<Message>();
	}

	public void onStart(IContext context) throws JFException {
		engine = context.getEngine();

		try {
			Set<Message> messages = new HashSet<Message>();
			messages.addAll(messagesToProcess);
			for(Message msg:messages){
				FIXFactory.newTradeOrderRequest(msg, this);
				messagesToProcess.remove(msg);
			}
		} catch (FieldNotFound e) {
			FIXFactory.ErrorLogging("DK Strategy. Error onStart: " + e);
		}
	}

	public void onAccount(IAccount inAccount) throws JFException {
		account = inAccount;
	}

	public void onMessage(IMessage message) throws JFException {
		try {
			if (message.getType().equals(Type.ORDER_SUBMIT_OK)
					|| message.getType().equals(Type.ORDER_FILL_OK)
					|| message.getType().equals(Type.ORDER_SUBMIT_REJECTED)
					|| message.getType().equals(Type.ORDER_FILL_REJECTED)
					|| message.getType().equals(Type.ORDER_CHANGED_OK)
					|| message.getType().equals(Type.ORDER_CLOSE_OK)
					|| message.getType().equals(Type.ORDER_CLOSE_REJECTED)
					|| message.getType().equals(Type.ORDER_CHANGED_REJECTED)) {
				FIXFactory.receiveExecutionReport(message, this);
			}
		} catch (FieldNotFound e) {
			FIXFactory.ErrorLogging("DK Strategy. Error onMessage: " + e);
		}
	}

	public void onStop() throws JFException {
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
	}

	public void newTradeOrderRequest(Message msg) {
		messagesToProcess.add(msg);
	}

	@Override
	public String getFIXSessionType() {
		return "TRADE";
	}

	@Override
	public Session getFIXSession() {
		return session;
	}

	@Override
	public void setFIXSession(Session inSession) {
		session = inSession;
	}

	@Override
	public Set<Message> getFIXRequests() {
		return mRequests;
	}

	@Override
	public IClient getClient() {
		return client;
	}

	@Override
	public String getAccountId() {
		return (account == null) ? "dkAccount" : account.getAccountId();
	}

	@Override
	public IEngine getEngine() {
		return engine;
	}
}
