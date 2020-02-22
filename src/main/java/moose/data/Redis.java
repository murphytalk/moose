package moose.data;

import io.vertx.core.Vertx;
import moose.marketdata.MarketData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;


/* Why this Java version

   As of now, the Kotlin compiler has yet to take the advantage of JVM instruction invokedynamic
   see https://youtrack.jetbrains.com/issue/KT-26060

   But in this particular case, since the lambda has to capture the closure,
   the Java version (use invokedynamic to generate the lambda code and then cache it)'s performance is almost same as
   that of Kotlin version (create an anonymous class every time)

   See https://stackoverflow.com/questions/30002380/why-are-java-8-lambdas-invoked-using-invokedynamic
*/
public class Redis extends AbstractRedis {
    public Redis(@NotNull Vertx vertx, @Nullable String hostname, @Nullable Integer port, @Nullable Logger logger) {
        super(vertx, hostname, port, logger);
    }

    @Override
    public void publish(MarketData marketData){
        var api = getApi();
        if(api!=null){
            getApi().hmset(List.of(
                    marketData.getTicker().getName(),
                    "price", Integer.toString(marketData.getPayload().getPrice()),
                    "received_time", Long.toString(marketData.getPayload().getReceivedTime()),
                    "published_time", Long.toString(marketData.getPublishTime())
            ),  it -> {
                var logger = getLogger();
                if(it.succeeded()) {
                    if(logger!=null) logger.debug("Saved to Redis: {}",marketData);
                }
                else{
                    if(logger!=null) logger.error("Failed save to Redis {}",it.cause().getMessage());
                }
            });
        }
   }

}
