package com.ga.HomeHub.booking.cache;

import org.apache.ignite.Ignition;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class CatalogCache{
    private static final Logger logger=LoggerFactory.getLogger(CatalogCache.class);

    @Value("${homehub.cache.enabled:false}")
    private boolean enabled;

    @Value("${homehub.cache.address:localhost:10800}")
    private String address;

    //Cached category data expires after 60 seconds.
    public String read(String key,Supplier<String> database){
        if(!enabled){
            return database.get();
        }

        try(IgniteClient client=connect()){
            String value=cache(client).get(key);
            if(value!=null){
                return value;
            }
        }catch(RuntimeException e){
            logger.warn("Category cache unavailable; reading from database");
            return database.get();
        }

        //Database exceptions must propagate instead of being treated as cache failures.
        String value=database.get();

        try(IgniteClient client=connect()){
            cache(client).put(key,value);
        }catch(RuntimeException e){
            logger.warn("Could not cache category; returning database result");
        }

        return value;
    }

    //Invalidate only after the category change commits successfully.
    public void invalidateAfterCommit(){
        if(!enabled){
            return;
        }

        if(TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization(){
                        @Override
                        public void afterCommit(){
                            invalidate();
                        }
                    }
            );
        }else{
            invalidate();
        }
    }

    private void invalidate(){
        try(IgniteClient client=connect()){
            cache(client).removeAll();
        }catch(RuntimeException e){
            logger.warn("Category cache invalidation failed; entries expire through TTL");
        }
    }

    private ClientCache<String,String> cache(IgniteClient client){
        return client.<String,String>getOrCreateCache("catalog-v1")
                .withExpirePolicy(
                        new CreatedExpiryPolicy(new Duration(TimeUnit.SECONDS,60))
                );
    }

    private IgniteClient connect(){
        return Ignition.startClient(
                new ClientConfiguration()
                        .setAddresses(address)
                        .setTimeout(1000)
        );
    }
}