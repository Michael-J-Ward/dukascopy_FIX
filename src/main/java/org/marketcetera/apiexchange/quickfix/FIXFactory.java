
package org.marketcetera.apiexchange.quickfix;


import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.marketcetera.apiexchange.brokers.dk.FIXStrategy;
import org.marketcetera.quickfix.FIXMessageFactory;
import org.marketcetera.quickfix.FIXMessageUtil;
import org.marketcetera.quickfix.FIXVersion;
import org.marketcetera.trade.Currency;

import com.dukascopy.api.IEngine;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.IOrder.State;

import quickfix.FieldNotFound;
import quickfix.Group;
import quickfix.Message;
import quickfix.field.ClOrdID;
import quickfix.field.MDEntryDate;
import quickfix.field.MDEntryPx;
import quickfix.field.MDEntrySize;
import quickfix.field.MDEntryTime;
import quickfix.field.MDEntryType;
import quickfix.field.MDReqID;
import quickfix.field.NoRelatedSym;
import quickfix.field.OrdRejReason;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.SubscriptionRequestType;
import quickfix.field.Symbol;
import quickfix.field.TimeInForce;
import quickfix.field.TransactTime;
import quickfix.fix44.MarketDataRequest;
import quickfix.fix44.MarketDataSnapshotFullRefresh;


/**
 * Factory to create Quickfixj messages from market events, or a list of parameters.
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 *
 */
public class FIXFactory {
	
	private static final String MLABEL = "m_";
	private static FIXMessageFactory msgFactory = FIXVersion.FIX44.getMessageFactory();
	
	// Trace FIX Msgs
	private static boolean m_INFO = true;
	public static void InfoLogging(String info){
		if(m_INFO){
			System.out.println(info);
		}
	}
	private static boolean m_DEBUG = false;
	public static void DebugLogging(String debug){
		if(m_DEBUG){
			System.out.println(debug);
		}
	}
	private static boolean m_ERROR = true;
	public static void ErrorLogging(String error){
		if(m_ERROR){
			System.out.println(error);
		}
	}//OE

	/**
     * Request market data from Dukascopy strategy.
     * @param msg a <code>Message</code> value containing the initial request message
     * @param strategy a <code>FIXStrategy</code> the Dukascopy data strategy
	 * @throws FieldNotFound 
     */
	public static void newDKMarketDataRequest(Message msg, FIXStrategy strategy) throws FieldNotFound {		
		Set<Instrument> instruments = strategy.getClient().getSubscribedInstruments();
		// Cancel market data
		if (((MarketDataRequest) msg).getSubscriptionRequestType().getValue() == SubscriptionRequestType.DISABLE_PREVIOUS_SNAPSHOT_PLUS_UPDATE_REQUEST) {
			Set<Message> messages = new HashSet<Message>();
			messages.addAll(strategy.getFIXRequests());
			for(Message request:messages){
				if (msg.getString(MDReqID.FIELD).equals(
						request.getString(MDReqID.FIELD))) {
					List<Group> groups = request.getGroups(NoRelatedSym.FIELD);
					for (int k = 0; k < groups.size(); k++) {
						String symbol = groups.get(k).getString(
								Symbol.FIELD);
						if (symbol != null) {
							Instrument instrument = Instrument
									.fromString(symbol);
							instruments.remove(instrument);
						}
					}
					strategy.getFIXRequests().remove(request);
				}
			}
		}else{ // Request Market data
			strategy.getFIXRequests().add(msg);
			List<Group> groups = msg.getGroups(NoRelatedSym.FIELD);
			for (int i = 0; i < groups.size(); i++) {
				String symbol = groups.get(i).getString(Symbol.FIELD);
				if (symbol != null) {
					Instrument instrument = Instrument.fromString(symbol);
					instruments.add(instrument);
				}
			}
		}
		
		strategy.getClient().setSubscribedInstruments(instruments);
	}

	/**
     * Receive the market data from Dukascopy strategy.
     * @param instrument a <code>Instrument</code> value containing the instrument for market data
     * @param tick a <code>ITick</code> the tick data
     * @param strategy a <code>FIXStrategy</code> the Dukascopy data strategy
	 * @throws FieldNotFound 
     */
	public static void receiveMarketData(Instrument instrument, ITick tick,
			FIXStrategy strategy)
			throws FieldNotFound {
		Set<Message> messages = new HashSet<Message>();
		messages.addAll(strategy.getFIXRequests());
		for(Message request:messages){
			List<Group> groups = request.getGroups(NoRelatedSym.FIELD);
			for (int i = 0; i < groups.size(); i++) {
				String symbol = groups.get(i).getString(Symbol.FIELD);
				int pmfac = (symbol.equals("XAU/USD") || symbol.equals("XAG/USD"))?1000000:1;
				if (symbol != null) {
					if (instrument.equals(symbol)) {

						MarketDataSnapshotFullRefresh refresh = new MarketDataSnapshotFullRefresh();
						refresh.set(new MDReqID(request
								.getString(MDReqID.FIELD)));
						refresh.set(new Symbol(symbol));
						MarketDataSnapshotFullRefresh.NoMDEntries group = new MarketDataSnapshotFullRefresh.NoMDEntries();

						// BID
						group.set(new MDEntryType(MDEntryType.BID));
						group.set(new MDEntryPx(new BigDecimal(""
								+ tick.getBid())));	
						group.set(new MDEntrySize(new BigDecimal(""
								+ tick.getBidVolume()*pmfac)));
						group.set(new MDEntryDate(new Date(tick.getTime())));
						group.set(new MDEntryTime(new Date(tick.getTime())));
						refresh.addGroup(group);

						// OFFER
						group.set(new MDEntryType(MDEntryType.OFFER));
						group.set(new MDEntryPx(new BigDecimal(""
								+ tick.getAsk())));
						group.set(new MDEntrySize(new BigDecimal(""
								+ tick.getAskVolume()*pmfac)));
						group.set(new MDEntryDate(new Date(tick.getTime())));
						group.set(new MDEntryTime(new Date(tick.getTime())));
						refresh.addGroup(group);

						strategy.getFIXSession().send(refresh);
					}
				}
			}
		}
	}

	/**
     * Returns a Trade Order Request for the given symbols on the given exchange.
     * @param msg a <code>Message</code> value containing the initial request message
     * @param strategy a <code>FIXStrategy</code> the Dukascopy data strategy
	 * @throws FieldNotFound 
	 * @throws JFException 
     */
	public static void newTradeOrderRequest(Message msg, FIXStrategy strategy)
			throws FieldNotFound, JFException {
		Set<Instrument> instruments = strategy.getClient()
				.getSubscribedInstruments();
		strategy.getFIXRequests().add(msg);
		Instrument instrument = Instrument.fromString(msg
				.getString(Symbol.FIELD));
		instruments.add(instrument);
		strategy.getClient().setSubscribedInstruments(instruments);

		if (FIXMessageUtil.isOrderSingle(msg)) {
			double amount = msg.getDouble(OrderQty.FIELD);
			IEngine.OrderCommand orderCommand = null;
			String label = MLABEL + msg.getString(ClOrdID.FIELD);
			char side = msg.getChar(Side.FIELD);
			char orderType = msg.getChar(OrdType.FIELD);
			char timeInForce = msg.getChar(TimeInForce.FIELD);
			long goodTillTime = 0L;

			if (orderType == OrdType.LIMIT) {
				double price = msg.getDouble(Price.FIELD);
				orderCommand = (side == Side.BUY) ? IEngine.OrderCommand.BUYLIMIT
						: IEngine.OrderCommand.SELLLIMIT;

				if (timeInForce == TimeInForce.DAY) {
					goodTillTime = System.currentTimeMillis() + 24 * 60 * 60
							* 1000;
				} else if ((timeInForce == TimeInForce.IMMEDIATE_OR_CANCEL)
						|| (timeInForce == TimeInForce.FILL_OR_KILL)) {
					goodTillTime = System.currentTimeMillis() + 60 * 1000;
				}
				strategy.getEngine().submitOrder(label, instrument,
						orderCommand, amount, price, 0.0, 0.0, 0.0,
						goodTillTime);
			} else {
				orderCommand = (side == Side.BUY) ? IEngine.OrderCommand.BUY
						: IEngine.OrderCommand.SELL;
				strategy.getEngine().submitOrder(label, instrument,
						orderCommand, amount);
			}
		} else if (FIXMessageUtil.isCancelRequest(msg)) {
			String label = MLABEL + msg.getString(OrigClOrdID.FIELD);
			for (IOrder order : strategy.getEngine().getOrders()) {
				if (order.getLabel().equals(label)) {
					order.close();
				}
			}
		} else if (FIXMessageUtil.isCancelReplaceRequest(msg)) {
			double price = msg.getDouble(Price.FIELD);
			double amount = msg.getDouble(OrderQty.FIELD);
			char side = msg.getChar(Side.FIELD);
			String prevLabel = MLABEL + msg.getString(OrigClOrdID.FIELD);
			String newLabel = MLABEL + msg.getString(ClOrdID.FIELD);
			for (IOrder iOrder : strategy.getEngine().getOrders()) {
				if (iOrder.getLabel().equals(prevLabel)) {
					iOrder.close();
					IEngine.OrderCommand orderCommand = (side == Side.BUY) ? IEngine.OrderCommand.BUYLIMIT
							: IEngine.OrderCommand.SELLLIMIT;
					char timeInForce = msg.getChar(TimeInForce.FIELD);
					long goodTillTime = 0L;
					
					if (timeInForce == TimeInForce.DAY) {
						goodTillTime = System.currentTimeMillis() + 24 * 60 * 60
								* 1000;
					} else if ((timeInForce == TimeInForce.IMMEDIATE_OR_CANCEL)
							|| (timeInForce == TimeInForce.FILL_OR_KILL)) {
						goodTillTime = System.currentTimeMillis() + 60 * 1000;
					}
					strategy.getEngine().submitOrder(newLabel, instrument,
							orderCommand, amount, price, 0.0, 0.0, 0.0,
							goodTillTime);
				}
			}
		}
	}
	
	/**
     * Returns a Trade Order Request for the given symbols on the given exchange.
	 * @param tradeSessionID 
     * @param message a <code>IMessage</code> value containing the Dukascopy execution report
     * @param strategy a <code>FIXStrategy</code> the Dukascopy data strategy
	 * @throws FieldNotFound 
     */
	public static void receiveExecutionReport(IMessage message,
			FIXStrategy strategy) throws FieldNotFound {

		// Only trades from FIX are supported
		if (!message.getOrder().getLabel().startsWith(MLABEL)) {
			return;
		}

		String orderID = message.getOrder().getId();
		String execID = UUID.randomUUID().toString();

		String clOrderID = message.getOrder().getLabel().substring(2);
		
		Set<Message> messages = new HashSet<Message>();
		messages.addAll(strategy.getFIXRequests());
		for(Message order:messages){
			if (order.isSetField(OrigClOrdID.FIELD)) {
				String label = MLABEL + order.getString(OrigClOrdID.FIELD);
				if (label.equals(message.getOrder().getLabel())) {
					clOrderID = order.getString(ClOrdID.FIELD);
				}
			}
		}

		char ordStatus = 0;
		char side = (message.getOrder().getOrderCommand()
				.equals(IEngine.OrderCommand.BUYLIMIT))
				|| (message.getOrder().getOrderCommand()
						.equals(IEngine.OrderCommand.BUY)) ? Side.BUY
				: Side.SELL;
		char orderType = (message.getOrder().getOrderCommand()
				.equals(IEngine.OrderCommand.BUYLIMIT))
				|| (message.getOrder().getOrderCommand()
						.equals(IEngine.OrderCommand.SELLLIMIT)) ? OrdType.LIMIT
				: OrdType.MARKET;

		BigDecimal orderQty = new BigDecimal(""
				+ message.getOrder().getRequestedAmount());
		BigDecimal orderPrice = new BigDecimal(""
				+ message.getOrder().getOpenPrice());
		Currency instrument = new Currency(message.getOrder().getInstrument()
				.toString());

		Date transactTime = new Date(message.getOrder().getCreationTime());
		String inAccount = strategy.getAccountId();

		String inText = null;
		BigDecimal cumQty = new BigDecimal(0.0);
		BigDecimal avgPrice = new BigDecimal(0.0);
		OrdRejReason rejReason = new OrdRejReason(OrdRejReason.OTHER);

		Message exeReport = null;
		switch (message.getType()) {
		case ORDER_SUBMIT_OK: {
			ordStatus = OrdStatus.NEW;
			exeReport = msgFactory.newExecutionReport(orderID, clOrderID,
					execID, ordStatus, side, orderQty, orderPrice, null, null,
					cumQty, avgPrice, instrument, inAccount, inText);
			exeReport.setField(new TransactTime(transactTime));
			exeReport.setField(new OrdType(orderType));
			break;
		}
		case ORDER_SUBMIT_REJECTED: {
			inText = message.getReasons().toArray()[0].toString();
			exeReport = msgFactory.newRejectExecutionReport(orderID, clOrderID,
					execID, side, orderQty, cumQty, avgPrice, instrument,
					rejReason, inAccount, inText);
			exeReport.setField(new TransactTime(transactTime));
			exeReport.setField(new OrdType(orderType));
			break;
		}
		case ORDER_FILL_OK: {
			ordStatus = OrdStatus.FILLED;
			cumQty = orderQty;
			avgPrice = orderPrice;
			exeReport = msgFactory.newExecutionReport(orderID, clOrderID,
					execID, ordStatus, side, orderQty, orderPrice, null, null,
					cumQty, avgPrice, instrument, inAccount, inText);
			exeReport.setField(new TransactTime(transactTime));
			exeReport.setField(new OrdType(orderType));
			break;
		}
		case ORDER_FILL_REJECTED: {
			inText = message.getReasons().toArray()[0].toString();
			exeReport = msgFactory.newRejectExecutionReport(orderID, clOrderID,
					execID, side, orderQty, cumQty, avgPrice, instrument,
					rejReason, inAccount, inText);
			exeReport.setField(new TransactTime(transactTime));
			exeReport.setField(new OrdType(orderType));
			break;
		}
		case ORDER_CLOSE_OK: {
			if (message.getOrder().getState().equals(State.CANCELED)) {
				ordStatus = OrdStatus.CANCELED;
				exeReport = msgFactory.newExecutionReport(orderID, clOrderID,
						execID, ordStatus, side, orderQty, orderPrice, null,
						null, cumQty, avgPrice, instrument, inAccount, inText);
				exeReport.setField(new TransactTime(transactTime));
				exeReport.setField(new OrdType(orderType));
				exeReport.setField(new OrigClOrdID(message.getOrder().getLabel().substring(2)));
			} else {
				return;
			}
			break;
		}
		case ORDER_CLOSE_REJECTED: {
			inText = message.getReasons().toArray()[0].toString();
			exeReport = msgFactory.newRejectExecutionReport(orderID, clOrderID,
					execID, side, orderQty, cumQty, avgPrice, instrument,
					rejReason, inAccount, inText);
			exeReport.setField(new TransactTime(transactTime));
			exeReport.setField(new OrdType(orderType));
			exeReport.setField(new OrigClOrdID(message.getOrder().getLabel().substring(2)));
			break;
		}
		case ORDER_CHANGED_OK: {
			ordStatus = OrdStatus.REPLACED;
			exeReport = msgFactory.newExecutionReport(orderID, clOrderID,
					execID, ordStatus, side, orderQty, orderPrice, null, null,
					cumQty, avgPrice, instrument, inAccount, inText);
			exeReport.setField(new TransactTime(transactTime));
			exeReport.setField(new OrdType(orderType));
			exeReport.setField(new OrigClOrdID(message.getOrder().getLabel().substring(2)));
			break;
		}
		case ORDER_CHANGED_REJECTED: {
			inText = message.getReasons().toArray()[0].toString();
			exeReport = msgFactory.newRejectExecutionReport(orderID, clOrderID,
					execID, side, orderQty, cumQty, avgPrice, instrument,
					rejReason, inAccount, inText);
			exeReport.setField(new TransactTime(transactTime));
			exeReport.setField(new OrdType(orderType));
			exeReport.setField(new OrigClOrdID(message.getOrder().getLabel().substring(2)));
			break;
		}
		default:
			return;
		}

		strategy.getFIXSession().send(exeReport);
	}		
}