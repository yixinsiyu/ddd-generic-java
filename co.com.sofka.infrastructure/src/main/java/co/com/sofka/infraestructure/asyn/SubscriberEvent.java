package co.com.sofka.infraestructure.asyn;


import co.com.sofka.business.asyn.ListenerEvent;
import co.com.sofka.domain.generic.DomainEvent;
import co.com.sofka.infraestructure.bus.EventBus;
import co.com.sofka.infraestructure.event.ErrorEvent;
import co.com.sofka.infraestructure.repository.EventStoreRepository;
import co.com.sofka.infraestructure.store.StoredEvent;

import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The type Subscriber event.
 */
public class SubscriberEvent implements Flow.Subscriber<DomainEvent> {

    private static Logger logger = Logger.getLogger(SubscriberEvent.class.getName());

    private final EventStoreRepository repository;
    private final EventBus eventBus;
    private final ListenerEvent listenerEvent;
    private Flow.Subscription subscription;

    /**
     * Instantiates a new Subscriber event.
     *
     * @param repository    the repository
     * @param eventBus      the event bus
     * @param listenerEvent the listener event
     */
    public SubscriberEvent(EventStoreRepository repository, EventBus eventBus, ListenerEvent listenerEvent) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.listenerEvent = listenerEvent;
    }


    /**
     * Instantiates a new Subscriber event.
     *
     * @param repository the repository
     * @param eventBus   the event bus
     */
    public SubscriberEvent(EventStoreRepository repository, EventBus eventBus) {
        this(repository, eventBus, null);
    }


    /**
     * Instantiates a new Subscriber event.
     *
     * @param repository the repository
     */
    public SubscriberEvent(EventStoreRepository repository) {
        this(repository, null, null);
    }

    /**
     * Instantiates a new Subscriber event.
     */
    public SubscriberEvent() {
        this(null, null, null);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
        Optional.ofNullable(listenerEvent).ifPresent(listener ->
                listener.onSubscribe(subscription)
        );
    }

    @Override
    public final void onNext(DomainEvent event) {
        logger.info("###### Process event -> " + event.type);

        Optional.ofNullable(eventBus).ifPresent(bus -> {
            var exchange = event.type.substring(0, event.type.lastIndexOf("."));
            bus.publish(exchange, event);
            logger.info("Event published OK");
        });

        Optional.ofNullable(repository).ifPresent(repo -> {
            logger.info("Saving event for aggregate root [" + event.aggregateRootId() + "]");
            StoredEvent storedEvent = StoredEvent.wrapEvent(event);
            Optional.ofNullable(event.aggregateRootId()).ifPresent(aggregateId -> {
                repo.saveEvent(event.getAggregateName(), aggregateId, storedEvent);
                logger.info("Event saved with store specification of --> " + event.getAggregateName());
            });
        });

        Optional.ofNullable(listenerEvent).ifPresent(listener -> {
            listener.setSubscriber(this);
            logger.info("Notify other case");
            listener.onNext(event);
        });
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {


        logger.log(Level.SEVERE, "Error on event", throwable.getCause());

        Optional.ofNullable(eventBus).ifPresent(bus -> {
            var event = new ErrorEvent(throwable);
            bus.publishError(event);
        });
        Optional.ofNullable(listenerEvent).ifPresent(listener ->
                listener.onError(throwable)
        );
        subscription.cancel();
    }

    @Override
    public void onComplete() {
        Optional.ofNullable(listenerEvent).ifPresent(ListenerEvent::onComplete);
    }
}
