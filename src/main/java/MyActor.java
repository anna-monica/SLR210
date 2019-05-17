
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MyActor extends UntypedAbstractActor {

    // Logger attached to actor
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public MyActor() {
    }

    // Static function creating actor
    public static Props createActor() {
        return Props.create(MyActor.class, () -> {
            return new MyActor();
        });
    }

    @Override
    public void onReceive(Object message) throws Throwable {

        
        if (message instanceof Members) {
            Members m = (Members) message;
            log.info("Received message with data: " + m.data);
            for (ActorRef actor : m.references) {
//                actor.tell(new MyHelloMessage("Hello "+actor.path().name()+" my name is "+this.self().path().name()), this.getSelf());
                actor.tell(new QuorumRequest(), this.getSelf());
                log.info("Request: "+self().path().name()+" -> "+actor.path().name());
                
            }
        } else if (message instanceof QuorumRequest) {
            this.getSender().tell(new QuorumResponse(), this.getSelf());
        }
        else if (message instanceof QuorumResponse) {
            log.info("Response: "+self().path().name()+" <- "+getSender().path().name());
        }else if (message instanceof MyHelloMessage) {
            MyHelloMessage m = (MyHelloMessage) message;
            log.info("Received: " + m.data);

        }
    }

    static public class MyHelloMessage {

        public final String data;

        public MyHelloMessage(String data) {
            this.data = data;
        }
    }

}
