package baoying.orderbook;

import baoying.orderbook.MarketDataMessage.AggregatedOrderBookRequest;
import baoying.orderbook.TradeMessage.OriginalOrder;
import baoying.orderbook.app.Util;
import baoying.orderbook.OrderBook.MEExecutionReportMessageFlag;
import baoying.orderbook.MarketDataMessage.OrderBookDelta;
import com.google.common.eventbus.AsyncEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchingEngine {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    public final MatchingDataStatistics statistics = new MatchingDataStatistics();

    public final List<String> _symbols;
	private final Map<String, OrderBook> _orderBooks;

	private final AsyncEventBus _outputMarketDataBus;
	private final AsyncEventBus _outputExecutionReportsBus;


	public MatchingEngine(List<String> symbols,
						  AsyncEventBus outputExecutionReportsBus,
						  AsyncEventBus outputMarketDataBus){

        _symbols = Collections.unmodifiableList(symbols);
        _orderBooks = new HashMap<>();
        for(String symbol: symbols){
            _orderBooks.put(symbol, new OrderBook(symbol));
        }

		_outputExecutionReportsBus = outputExecutionReportsBus;
		_outputMarketDataBus = outputMarketDataBus;

	}

	public List<MEExecutionReportMessageFlag> matchOrder(OriginalOrder order) {

		statistics.increaseRecvOrder();

		OrderBook orderBook = orderBook(order._symbol);


		Util.Tuple<List<MEExecutionReportMessageFlag>, List<OrderBookDelta>> matchResult
				= orderBook.matchOrder(order);

		sendToBus(matchResult);

		return matchResult._1;
    }

	private void sendToBus(Util.Tuple<List<MEExecutionReportMessageFlag>, List<OrderBookDelta>> matchResult) {

		matchResult._1.forEach( execRpt ->{
			_outputExecutionReportsBus.post(execRpt);
			statistics.increaseER();
		} );

		matchResult._2.forEach( ordBookDelta ->{
			_outputMarketDataBus.post(ordBookDelta);
			statistics.increaseMD();
		} );

	}

	public void addOrdBookRequest(AggregatedOrderBookRequest aggOrdBookRequest) {

		OrderBook orderBook = orderBook(aggOrdBookRequest._symbol);
		MarketDataMessage.AggregatedOrderBook aggOrderBook = orderBook.buildAggregatedOrderBook(aggOrdBookRequest._depth);
		_outputMarketDataBus.post(aggOrderBook);
    }

	public void addOrdBookRequest(MarketDataMessage.DetailOrderBookRequest request) {

		OrderBook orderBook = orderBook(request._symbol);
		MarketDataMessage.DetailOrderBook detailOrderBookSS = orderBook.buildDetailedOrderBook(request._depth);
		_outputMarketDataBus.post(detailOrderBookSS);
	}

	private OrderBook orderBook(String symbol){

	     OrderBook ob = _orderBooks.get(symbol);

	     if(ob == null){
	         throw new RuntimeException("cannot identify related order book from symbol:"+symbol);
         }
         return  ob;
    }


}
