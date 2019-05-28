
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;

public class Process extends UntypedAbstractActor {

    // Logger attached to actor
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private int N;
    private int id;
    private int ballot;
    private Integer proposal;
    private int readballot;
    private int imposeballot;
    private Integer estimate;

    ArrayList<Tuple<Integer>> states;

    private Members processes;

    public Process(int ID, int nb) {
        N = nb;
        id = ID;
        ballot = id - N;
        proposal = null;
        readballot = 0;
        imposeballot = 0;
        estimate = null;
        states = new ArrayList(N);
    }

    // Static function creating actor
    public static Props createActor(int ID, int nb) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb);
        });
    }

    //cours page 30
    private void ofconsProposeReceived(Integer v) {
        proposal = v;
        ballot += N;
        states = new ArrayList(N);
        for (ActorRef actor : processes.references) {
            actor.tell(new ReadMsg(ballot), this.getSelf());
            log.info("Read ballot " + ballot + " msg: " + self().path().name() + " -> " + actor.path().name());
        }
    }

    private void readReceived(int newBallot, ActorRef pj) {
        if (readballot >= newBallot || imposeballot >= newBallot) {
            pj.tell(new AbortMsg(newBallot), this.getSelf());
            log.info("Abort ballot " + newBallot + " msg: " + self().path().name() + " -> " + pj.path().name());
        } else {
            readballot = newBallot;
            pj.tell(new GatherMsg(newBallot, imposeballot, estimate), this.getSelf());
            log.info("Gather ballot " + newBallot + " msg: " + self().path().name() + " -> " + pj.path().name());
        }
    }

    private void abortReceived(int ballot) {
        log.info(self().path().name() + " Aborting... ballot: " + ballot);
    }

    //cours page 31
    private void gatherReceived(int ballot, int estballot, int est, ActorRef pj) {
        states.set(Integer.parseInt(pj.path().name()), new Tuple(est, estballot));
        int numStates = 0;
        numStates = states.stream().filter((tuple) -> (!(tuple.x == null) || !(tuple.y == 0))).map((_item) -> 1).reduce(numStates, Integer::sum);

        //#states ≥ majority //collected a majority of responses
        if (numStates >= N / 2) {
            Tuple<Integer> maxTuple = null;
            //pick the tuple with highest estballot
            for (Tuple tuple : states) {
                if (!(tuple.x == null) && !(tuple.y == 0) && tuple.y >= maxTuple.y) {
                    maxTuple = tuple;
                }
            }
            proposal = maxTuple.x;
            states = new ArrayList(N);

            for (ActorRef actor : processes.references) {
                actor.tell(new ImposeMsg(ballot, proposal), this.getSelf());
                log.info("Impose proposal " + proposal + " msg: " + self().path().name() + " -> " + actor.path().name());
            }
        }
    }

    private void imposeReceived(int newBallot, Integer v, ActorRef pj) {
        if (readballot > newBallot || imposeballot > newBallot) {
            pj.tell(new AbortMsg(newBallot), this.getSelf());
        } else {
            estimate = v;
            imposeballot = newBallot;
            pj.tell(new AckMsg(newBallot), this.getSelf());
        }
    }

    int acksReceived = 0;

    private void ackReceived(int ballot, ActorRef pj) {
        acksReceived++;
        if (acksReceived > N / 2) {//received acks from majority
            for (ActorRef actor : processes.references) {
                actor.tell(new DecideMsg(proposal), this.getSelf());
                log.info("Decide proposal " + proposal + " msg: " + self().path().name() + " -> " + actor.path().name());
            }
        }
    }

    private void decideReceived(int v, ActorRef pj) {
        for (ActorRef actor : processes.references) {
            actor.tell(new DecideMsg(v), this.getSelf());
            log.info("Decide value " + v + " msg: " + self().path().name() + " -> " + actor.path().name());
        }
        log.info(self().path().name() + " Decided on value: " + v);
    }

    @Override
    public void onReceive(Object message) throws Throwable {

        if (message instanceof Members) {
            Members m = (Members) message;
            processes = m;
            log.info(self().path().name() + " received message with processes info");

        } else if (message instanceof OfconsProposeMsg) {
            OfconsProposeMsg m = (OfconsProposeMsg) message;
            this.ofconsProposeReceived(m.v);
        } else if (message instanceof ReadMsg) {
            ReadMsg m = (ReadMsg) message;
            this.readReceived(m.ballot, getSender());
        } else if (message instanceof AbortMsg) {
            AbortMsg m = (AbortMsg) message;
            this.abortReceived(m.ballot);
        }else if (message instanceof GatherMsg) {
            GatherMsg m = (GatherMsg) message;
            this.gatherReceived(m.ballot,m.imposeballot,m.estimate ,getSender());
        }else if (message instanceof ImposeMsg) {
            ImposeMsg m = (ImposeMsg) message;
            this.imposeReceived(m.ballot,m.proposal,getSender());
        }else if (message instanceof AckMsg) {
            AckMsg m = (AckMsg) message;
            this.ackReceived(m.ballot,getSender());
        }else if (message instanceof DecideMsg) {
            DecideMsg m = (DecideMsg) message;
            this.decideReceived(m.proposal,getSender());
        }

    }

    private static class Tuple<X> {

        public final X x;
        public final int y;

        public Tuple(X x, int y) {
            this.x = x;
            this.y = y;
        }
    }

}
