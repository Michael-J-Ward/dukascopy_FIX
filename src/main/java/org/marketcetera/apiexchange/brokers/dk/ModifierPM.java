package org.marketcetera.apiexchange.brokers.dk;

import java.util.*;

import com.dukascopy.api.*;

/**
 * @author <a href="mailto:etradealgo@gmail.com">Otmane El Rhazi</a>
 * @version 1.0.0
 */
public class ModifierPM implements IStrategy {
	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IContext context;
	private IIndicators indicators;
	private IUserInterface userInterface;

	private int m_pattern = 4 * 60 / 5;// 4h
	private int m_forecast = 60 / 5;// 30min
	private int m_initbarsnb = 12 * 30 * 24 * 60 / 5;// 1y
	private double alpha = 15;
	private double beta = 5;
	private String m_label = "PAT_";
	private double m_size = 0.01;
	private int m_tradenb = 0;
	private Period m_period = Period.FIVE_MINS;
	private Map<Instrument, List<Double>> m_bars = new HashMap<Instrument, List<Double>>();
	private List<Instrument> m_instrument = new ArrayList<Instrument>();

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.context = context;
		this.indicators = context.getIndicators();
		this.userInterface = context.getUserInterface();

		// Setup the trade nb tracker
		for (IOrder order : engine.getOrders()) {
			if (order.getState() == IOrder.State.FILLED
					&& order.getLabel().startsWith(m_label)) {
				int nb = Integer.parseInt(order.getLabel().split("_")[1]);
				m_tradenb = Math.max(m_tradenb, nb);
			}
		}

		// The instrument list
		m_instrument.add(Instrument.GBPUSD);
		m_instrument.add(Instrument.EURUSD);
		m_instrument.add(Instrument.USDCHF);
		m_instrument.add(Instrument.AUDUSD);
		m_instrument.add(Instrument.NZDUSD);
		m_instrument.add(Instrument.USDJPY);
		m_instrument.add(Instrument.USDCAD);

		// The initial data
		for (Instrument instrument : m_instrument) {
			List<Double> data = new ArrayList<Double>();
			long prevBarTime = history.getPreviousBarStart(m_period, history
					.getLastTick(instrument).getTime());
			List<IBar> bidBars = history.getBars(instrument, m_period,
					OfferSide.BID, Filter.WEEKENDS, m_initbarsnb, prevBarTime,
					0);
			List<IBar> askBars = history.getBars(instrument, m_period,
					OfferSide.ASK, Filter.WEEKENDS, m_initbarsnb, prevBarTime,
					0);
			for (int i = 0; i < m_initbarsnb; i++) {
				data.add((bidBars.get(i).getClose() + askBars.get(i).getClose()) / 2.0);
			}
			m_bars.put(instrument, data);
		}
		console.getOut().println(
				"Info. instruments: " + m_instrument + " , period: " + m_period
						+ " , barsnb: " + m_initbarsnb);
	}

	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {

		// Working hours
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(askBar.getTime());
		boolean friday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
		boolean saturday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY;
		boolean sunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		if (saturday || (friday && hour > 22) || (sunday && hour < 22)) {
			return;
		}

		if (period.equals(m_period) && m_instrument.contains(instrument)) {
			long currentTimeB = System.currentTimeMillis();
			List<Double> data = m_bars.get(instrument);
			data.add((bidBar.getClose() + askBar.getClose()) / 2.0);
			m_bars.put(instrument, data);

			// The reference pattern
			List<Double> barsPattern = data.subList(data.size() - m_pattern,
					data.size());

			// The past pattern
			List<Double> barsEntrypy = new ArrayList<Double>();
			for (int i = 0; i < data.size() - 2 * m_pattern; i++) {
				List<Double> barsPast = data.subList(i, i + m_pattern);
				double entropy = getEntropy(barsPattern, barsPast);
				barsEntrypy.add(entropy);
			}

			// The matching patthern details
			double entropyMin = barsEntrypy.get(0);
			int indexMin = 0;
			for (int i = 0; i < barsEntrypy.size(); i++) {
				if (barsEntrypy.get(i) < entropyMin) {
					indexMin = i;
					entropyMin = barsEntrypy.get(i);
				}
			}

			long currentTimeA = System.currentTimeMillis();
			// console.getOut().println("Info-Current: "+Arrays.toString(barsPattern.toArray()));

			List<Double> barsPast = data
					.subList(indexMin, indexMin + m_pattern);
			// console.getOut().println("Info-Min: "+Arrays.toString(barsPast.toArray()));

			List<Double> barsForecastIni = data.subList(indexMin + m_pattern
					- 1, indexMin + m_pattern - 1 + m_forecast);
			List<Double> barsForecast = new ArrayList<Double>();
			for (int i = 0; i < barsForecastIni.size(); i++) {
				barsForecast.add(getFunc(i + m_pattern, barsForecastIni,
						barsPattern, barsPast));
			}
			// console.getOut().println("Info-Forecast: "+Arrays.toString(barsForecast.toArray()));
			console.getOut().println(
					"Info. ms: " + (currentTimeA - currentTimeB)
					+ " , entropy: " + Math.round(entropyMin / instrument.getPipValue()
									* 10.00) / 10.00 + " , index: " + indexMin
					+ " , forecast: " + Arrays.toString(barsForecast.toArray()));

			double volume = getInstrumentPosition(instrument);

			boolean mLong = false;
			boolean mShort = false;
			if ((barsForecast.get(0) <= (barsForecast.get(m_forecast - 1) - alpha
					* instrument.getPipValue()))
					&& (entropyMin < beta * instrument.getPipValue())) {
				mLong = true;
			}
			if ((barsForecast.get(0) >= (barsForecast.get(m_forecast - 1) + alpha
					* instrument.getPipValue()))
					&& (entropyMin < beta * instrument.getPipValue())) {
				mShort = true;
			}

			if (volume == 0) {
				if (mShort) {
					// Go Short
					IEngine.OrderCommand orderCommand = IEngine.OrderCommand.SELL;
					m_tradenb++;
					String label = m_label + (m_tradenb) + "_"
							+ System.currentTimeMillis();
					engine.submitOrder(label, instrument, orderCommand, m_size);
					console.getOut().println("GO SHORT - " + instrument);
				} else if (mLong) {
					// Go Long
					IEngine.OrderCommand orderCommand = IEngine.OrderCommand.BUY;
					m_tradenb++;
					String label = m_label + (m_tradenb) + "_"
							+ System.currentTimeMillis();
					engine.submitOrder(label, instrument, orderCommand, m_size);
					console.getOut().println("GO LONG - " + instrument);
				}
			} else {
				if (volume > 0) {
					if ((barsForecast.get(0) > barsForecast.get(m_forecast - 1))
							&& (entropyMin < beta * instrument.getPipValue())) {
						closeInstrumentPosition(instrument);
						console.getOut().println("GO FLAT - " + instrument);
					}
					if (mShort) {
						// Go Short
						IEngine.OrderCommand orderCommand = IEngine.OrderCommand.SELL;
						m_tradenb++;
						String label = m_label + (m_tradenb) + "_"
								+ System.currentTimeMillis();
						engine.submitOrder(label, instrument, orderCommand,
								m_size);
						console.getOut().println("GO SHORT - " + instrument);
					}
				} else if (volume < 0) {
					if ((barsForecast.get(0) < barsForecast.get(m_forecast - 1))
							&& (entropyMin < beta * instrument.getPipValue())) {
						closeInstrumentPosition(instrument);
						console.getOut().println("GO FLAT - " + instrument);
					}
					if (mLong) {
						// Go Long
						IEngine.OrderCommand orderCommand = IEngine.OrderCommand.BUY;
						m_tradenb++;
						String label = m_label + (m_tradenb) + "_"
								+ System.currentTimeMillis();
						engine.submitOrder(label, instrument, orderCommand,
								m_size);
						console.getOut().println("GO LONG - " + instrument);
					}
				}
			}
		}
	}

	public Double getEntropy(List<Double> barsPattern, List<Double> barsPast)
			throws JFException {
		double entropy = 0;

		for (int i = 0; i < barsPattern.size(); i++) {
			entropy += Math.abs(barsPattern.get(i)
					- getFunc(i, barsPast, barsPattern, barsPast));
		}
		return entropy / barsPattern.size();
	}

	public Double getFunc(int i, List<Double> data, List<Double> barsPattern,
			List<Double> barsPast) {
		double an = barsPattern.get(barsPattern.size() - 1)
				/ barsPast.get(barsPattern.size() - 1);
		double a0 = barsPattern.get(0) / barsPast.get(0);
		double ai = a0 + (i / (barsPattern.size() - 1)) * (an - a0);

		int j = i;
		if (j >= barsPattern.size()) {
			j = i - barsPattern.size();
		}

		double value = data.get(j) * ai;
		return value;
	}

	public Double getInstrumentPosition(Instrument instrument)
			throws JFException {
		double volume = 0.00;
		for (IOrder order : engine.getOrders(instrument)) {
			if (order.getState() == IOrder.State.FILLED
					&& order.getLabel().startsWith(m_label)) {
				volume += (order.isLong() ? 1.00 : (-1.00)) * order.getAmount();
			}
		}
		return volume;
	}

	public void closeInstrumentPosition(Instrument instrument)
			throws JFException {
		for (IOrder order : engine.getOrders(instrument)) {
			order.close();
		}
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
	}

	public void onStop() throws JFException {
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}
}