package org.marketcetera.apiexchange.brokers.dk;


import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.marketcetera.apiexchange.quickfix.FIXFactory;

import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.Session;


import com.dukascopy.api.*;
import com.dukascopy.api.system.IClient;

/**
 * Dukascopy market data strategy. Send market data request, receive tick data
 * 
 * 
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 *
 */
public class DataStrategy implements FIXStrategy {
    private IClient client = null;
    private IAccount account = null;
    private IEngine engine;
    
    Session session;
    private final Set<Message> mRequests;
    private final MessageProcessor messageProcessor;
    
    DataStrategy(IClient inClient){
    	client = inClient;
    	mRequests = new CopyOnWriteArraySet<Message>();
    	messageProcessor = new MessageProcessor();
    }
    
    public void onStart(IContext context) throws JFException {
    	engine = context.getEngine();
    }

    public void onAccount(IAccount inAccount) throws JFException {
    	account = inAccount;
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
    	mRequests.clear();
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    	/*try {
			FIXFactory.receiveMarketData(instrument, tick, this);
		} catch (FieldNotFound e) {
			FIXFactory.ErrorLogging("DK Strategy. Error onTick: " + e);
		}*/
    	messageProcessor.add(instrument, tick, this);
    }
    
    public void newDKMarketDataRequest(Message msg) throws FieldNotFound {
		FIXFactory.newDKMarketDataRequest(msg, this);
	}

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    }

	@Override
	public String getFIXSessionType() {
		return "DATA";
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
		return (account==null)?"dkAccount":account.getAccountId();
	}

	@Override
	public IEngine getEngine() {
		return engine;
	}
	
	 /**
     * Processes incoming messages.
     */
    private class MessageProcessor
            implements Runnable
    {
		public class ExpensiveTask implements Runnable {

			private final Instrument instrument;
			private final ITick tick;
			private final FIXStrategy strategy;
			public ExpensiveTask(Instrument instrument, ITick tick, FIXStrategy strategy) {
				this.instrument = instrument;
				this.tick = tick;
				this.strategy = strategy;
			}

			public void run() {
				try {
					FIXFactory.receiveMarketData(instrument, tick, strategy);
				} catch (FieldNotFound e) {
					FIXFactory.ErrorLogging("DK Strategy. Error onTick: " + e);
				}
			}

		}
    	
        /* (non-Javadoc)
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run()
        {
            try {
                while(true) {
                	Instrument instruments = instrumentsToProcess.take();
                	ITick ticks = ticksToProcess.take();
                	FIXStrategy strategy = strategiesToProcess.take();
                    executorService.submit(new ExpensiveTask(instruments, ticks, strategy));
                 }    

            } catch (InterruptedException ignored) {
            	 System.out.println("messagesToProcess error: " + ignored);
            }
        }
        
        /**
         * Create a new MessageProcessor instance.
         */
        private MessageProcessor()
        {
        	instrumentsToProcess = new LinkedBlockingDeque<Instrument>();
        	ticksToProcess = new LinkedBlockingDeque<ITick>();
        	strategiesToProcess = new LinkedBlockingDeque<FIXStrategy>();
            thread = new Thread(this,
                                "Dukascopy Message Processing Tread"); //$NON-NLS-1$
            thread.start();
        }
        
        /**
         * Add the message to the queue.
         */
        private void add(Instrument instrument, ITick tick, FIXStrategy strategy){
        	instrumentsToProcess.add(instrument);
        	ticksToProcess.add(tick);
        	strategiesToProcess.add(strategy);
        }
        
        /**
         * thread on which the messages are processed
         */
        private final Thread thread;
        /**
         * the list of messages to be processed
         */
        private final BlockingDeque<Instrument> instrumentsToProcess;
        private final BlockingDeque<ITick> ticksToProcess;
        private final BlockingDeque<FIXStrategy> strategiesToProcess;
        /**
         * the Executor Service submitting the messages in the same broker session
         */
    	private final ExecutorService executorService = Executors.newCachedThreadPool();
    }
}
