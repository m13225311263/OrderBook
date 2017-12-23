package baoying.orderbook.testtool;

import baoying.orderbook.app.MatchingEngineFIXWrapper;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

//This is a simplified version.   
//For production code, please read common.DefaultQFJSingleSessionInitiator.
public class FirstQFJClientBatch {

    private final static Logger log = LoggerFactory.getLogger(FirstQFJClientBatch.class);

    String  getQFJConfigContent(List<String> clientCompIDs)throws Exception{

        //https://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
        String qfjConfigBaseContent = CharStreams.toString(
                new InputStreamReader(
                        FirstQFJClientBatch.class.getClassLoader().getResourceAsStream("testtool/FirstQFJClient.qfj.config_base.txt"), Charsets.UTF_8));

        StringBuilder clientSessions = new StringBuilder("#====autoGeneratedSessions====");
        for(String c: clientCompIDs){
            clientSessions.append("\n")
                    .append("[session]").append("\n")
                    .append("SenderCompID=").append(c).append("\n");
        }
        return qfjConfigBaseContent+clientSessions;
    }

    public void execute( String symbol,
                            String price,
                            String qty ,
                            String ordType,
                            String side,

                            String clientCompIDPrefix,
                            int fixClientNum,
                            int ratePerMinute  ) throws  Exception{


        List<String> clientIDs = new ArrayList<>();
        {
            IntStream.range(0, fixClientNum).forEach(it -> clientIDs.add(clientCompIDPrefix + String.valueOf(it)));
        }
        String qfjConfigContent = getQFJConfigContent(clientIDs);
        Application application = new FirstMessageCallback();
        SessionSettings settings = new SessionSettings(new ByteArrayInputStream(qfjConfigContent.getBytes())) ;
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new SLF4JLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        SocketInitiator initiator = new SocketInitiator(application, storeFactory, settings, logFactory,
                messageFactory);

        initiator.start();

        // after start, you have to wait several seconds before sending
        // messages.
        // in production code, you should check the response Logon message.
        // Refer: DefaultQFJSingSessionInitiator.java
        TimeUnit.SECONDS.sleep(5);

        final int period;
        final TimeUnit unit;
        final int msgNumPerPeriod ;
        {
            if(ratePerMinute <= 60){

                period = 60 / ratePerMinute;
                unit = TimeUnit.SECONDS;
                msgNumPerPeriod = 1;

            }else if(ratePerMinute <= 60 *2) {

                period = 1;
                unit = TimeUnit.SECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/60);

            }else if(ratePerMinute <= 60 * 10) {
                //2 intervals
                period = 500;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 2));

            }else if(ratePerMinute <= 60 *50) {
                //10 intervals
                period = 100;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 10));

            }else if(ratePerMinute <= 60 *100) {

                //20 intervals
                period = 50;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 20));

            }else if(ratePerMinute <= 60 *200) {

                //50 intervals
                period = 20;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 50));

            }else{
                //100 intervals
                period = 10;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 10));            }
        }

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger totalSent = new AtomicInteger(0);
        Runnable command = new Runnable() {
            @Override
            public void run(){
                try {
                    IntStream.range(0, msgNumPerPeriod).forEach(it -> {

                        int nextClientCompIDIndex = totalSent.get() % fixClientNum;
                        String clientCompID = clientIDs.get(nextClientCompIDIndex);

                        SessionID sessionID = new SessionID("FIXT.1.1", clientCompID, MatchingEngineFIXWrapper.serverCompID, "");
                        if (!Session.doesSessionExist(sessionID)) {
                            log.warn("ignore the realtime ER to client, since he:{} is not online now", clientCompID);
                            return;
                        }

                        try {
                            Message order = FIXOrderBuilder.buildNewOrderSingle(clientCompID,
                                    symbol,
                                    price,
                                    qty,
                                    ordType,
                                    side);
                            Session.sendToTarget(order, sessionID);
                        } catch (Exception e) {
                            log.error("exception while sending",e);
                        }

                        int sent = totalSent.incrementAndGet();
                        if (sent >= Integer.MAX_VALUE / 2) {
                            totalSent.getAndSet(0);
                        }
                    });

                }catch (Exception e){
                    log.error("exception while schedule sending",e);
                }
            }
        };
        long initialDelay = 0;
        executor.scheduleAtFixedRate( command,  initialDelay, period,   unit);

    }
    static class Args {
        @Parameter
        private List<String> parameters = new ArrayList<>();

        @Parameter(names =  "-clientNum", description = "number of clients")
        private int numOfClients = 1;

        @Parameter(names =  "-ratePerMinute", description = "rate of sending - per minute for overall(clients as a whole). ")
        private int ratePerMinute = 10;

        @Parameter(names =  "-client_prefix", description = "client compid prefix, e.g. LTC$$_FIX_, BACKGROUD_FIX_")
        private String clientCompIDPrefix = "BACKGROUND_FIX_";

        @Parameter(names =  "-symbol", description = "symbol")
        private String symbol = "USDJPY";

        @Parameter(names =  "-side", description = "side, Bid or  Offer")
        private String side = "Bid";

        @Parameter(names = "-qty", description = "quantity of base ccy")
        private String qty="100";

        @Parameter(names = "-ordType", description = "order type - Market, or Limit")
        private String orderType="Limit";

        @Parameter(names = "-px", description = "required for Limit order")
        private String px = "112";

        @Parameter(names =  "-d", description = "duration in second, after the X second, shutdown")
        private int durationInSecond = 60;
    }

    public static void main(String[] args) throws Exception {

        Args argsO = new Args();
        JCommander.newBuilder()
                .addObject(argsO)
                .build()
                .parse(args);
        String symbol = argsO.symbol;
        String price = argsO.px;
        String qty = argsO.qty;
        String ordType = argsO.orderType; //Market or Limit
        String side = argsO.side;//Bid or Offer

        String clientCompIDPrefix = argsO.clientCompIDPrefix + System.currentTimeMillis() + "_";
        int fixClientNum = argsO.numOfClients;
        int ratePerMinute = argsO.ratePerMinute;

        FirstQFJClientBatch firstQFJClientBatch = new FirstQFJClientBatch();

        firstQFJClientBatch.execute( symbol,price,qty ,ordType, side   ,clientCompIDPrefix  ,fixClientNum,ratePerMinute);

        TimeUnit.SECONDS.sleep(argsO.durationInSecond);
        log.info("exiting, since time out:"+argsO.durationInSecond+" seconds");
        System.exit(0);
    }
}