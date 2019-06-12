
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Process extends UntypedAbstractActor {

    // Logger attached to actor
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final int N;//number of processes
    private final int id;//id of current process
    private Launcher launcher;

    private int ballot;
    private Integer proposal;
    private int readballot;
    private int imposeballot;
    private Integer estimate;

    private long timeSarted;//time the process was created
    private State state;

    ArrayList<Tuple<Integer>> states;
    int stateNo;//numbers of states collected

    private Members processes;//other processes' references

    public Process(int ID, int nb, long timeSarted) {
        N = nb;
        id = ID;
        ballot = id - N;
        proposal = -1;
        readballot = 0;
        imposeballot = 0;
        estimate = -1;
        states = new ArrayList(N);
        stateNo = 0;
        for (int i = 0; i < N; i++) {
            states.add(new Tuple(-1, 0));
        }

        this.timeSarted = timeSarted;
        state = State.CORRECT;

    }

    @Override
    public String toString() {
        return "Process{" + "id=" + id + ", ballot=" + ballot + ", proposal=" + proposal + ", readballot=" + readballot + ", imposeballot=" + imposeballot + ", estimate=" + estimate + ", states=" + states + ", stateNo=" + stateNo + ", acksReceived=" + acksReceived + '}';
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb, long timeSarted) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb, timeSarted);
        });
    }

    /**
     * @param v method called when a Propose message is Received with value v
     */
    private void ofconsProposeReceived(Integer v) {

        proposal = v;
        ballot += N;
        states = new ArrayList(N);
        stateNo = 0;
        for (int i = 0; i < N; i++) {
            states.add(new Tuple(-1, 0));
        }
        for (ActorRef actor : processes.references) {
            actor.tell(new ReadMsg(ballot), this.getSelf());
            log.info("Read ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());

        }

    }

    /**
     * method called when a Read message is Received
     *
     * @param newBallot
     * @param pj
     */
    private void readReceived(int newBallot, ActorRef pj) {
        if (readballot >= newBallot || imposeballot >= newBallot) {
            pj.tell(new AbortMsg(newBallot), this.getSelf());
            log.info("Abort ballot " + newBallot + " msg: p" + self().path().name() + " -> p" + pj.path().name());
        } else {
            readballot = newBallot;
            pj.tell(new GatherMsg(newBallot, imposeballot, estimate), this.getSelf());
            log.info("Gather ballot " + newBallot + " msg: p" + self().path().name() + " -> p" + pj.path().name());
        }
    }

    /**
     *
     * @param ballot
     */
    private void abortReceived(int ballot) {
        log.info("p" + self().path().name() + " Aborting... ballot: " + ballot);
    }

    /**
     * method called when a Gather message is Received
     *
     * @param ballot
     * @param estballot
     * @param est
     * @param pj
     */
    private void gatherReceived(int ballot, int estballot, Integer est, ActorRef pj) {
        states.set(Integer.parseInt(pj.path().name()), new Tuple(est, estballot));
        stateNo++;//count number of collected responses
        for (Tuple t : states) {//only for logging puposes
            log.info(t.x + "," + t.y);
        }

        //#states ≥ majority //collected a majority of responses
        if (stateNo >= N / (2 * 1.0)) {
            Tuple<Integer> maxTuple = new Tuple(-1, 0);
            //pick the tuple with highest estballot
            for (Tuple tuple : states) {
                if (!(tuple.x.equals(-1)) && !(tuple.y == 0) && tuple.y >= maxTuple.y) {
                    maxTuple = tuple;
                }
            }

            if (!(maxTuple.x.equals(-1)) && !(maxTuple.y == 0)) {//if found a state[pk]≠[nil,0]
                proposal = maxTuple.x;
            }

            //states:=[nil,0]^N
            states = new ArrayList(N);
            stateNo = 0;
            for (int i = 0; i < N; i++) {
                states.add(new Tuple(-1, 0));
            }

            for (ActorRef actor : processes.references) {
                actor.tell(new ImposeMsg(ballot, proposal), this.getSelf());
                log.info("Impose proposal " + proposal + " msg: p" + self().path().name() + " -> p" + actor.path().name());
            }
        }

    }

    /**
     * method called when an Impose message is Received
     *
     * @param newBallot
     * @param v
     * @param pj
     */
    private void imposeReceived(int newBallot, Integer v, ActorRef pj) {
        if (readballot > newBallot || imposeballot > newBallot) {
            pj.tell(new AbortMsg(newBallot), this.getSelf());
            log.info("Abort ballot " + newBallot + " msg: p" + self().path().name() + " -> p" + pj.path().name());
        } else {
            estimate = v;
            imposeballot = newBallot;
            pj.tell(new AckMsg(newBallot), this.getSelf());
            log.info("Ack ballot " + newBallot + " msg: p" + self().path().name() + " -> p" + pj.path().name());
        }
    }

    int acksReceived = 0;//counting numbers of ACK messages received

    /**
     * method called when an Ack message is Received
     *
     * @param ballot
     * @param pj
     */
    private void ackReceived(int ballot, ActorRef pj) {
        acksReceived++;
        if (acksReceived >= N / (2 * 1.0)) {//received acks from majority
            for (ActorRef actor : processes.references) {

                actor.tell(new DecideMsg(proposal), this.getSelf());
                log.info("Decide proposal " + proposal + " msg: p" + self().path().name() + " -> p" + actor.path().name());
            }
        }
    }

    /**
     * method called when a Decide message is Received
     *
     * @param v
     * @param pj
     * @throws Exception
     */
    private void decideReceived(int v, ActorRef pj) throws Exception {
        for (ActorRef actor : processes.references) {
            actor.tell(new DecideMsg(v), this.getSelf());
            log.info("Decide value " + v + " msg: p" + self().path().name() + " -> p" + actor.path().name());
        }

        //measure elapsed time
        long timeDecided = System.currentTimeMillis();
        long timeElapsed = timeDecided - timeSarted;
        long millis = timeElapsed % 1000;
        long second = (timeElapsed / 1000) % 60;
        long minute = (timeElapsed / (1000 * 60)) % 60;
        long hour = (timeElapsed / (1000 * 60 * 60)) % 24;
        String time = String.format("%02d:%02d:%02d.%d", hour, minute, second, millis);

        state = State.DECIDED;
        log.error("..............................................................................p" + self().path().name() + " Decided on value: " + v + " after: " + time);
//        this.getContext().getSystem().terminate();
    }

    @Override
    public void onReceive(Object message) throws Throwable {

//        log.info(this.toString());
        if (this.state == State.FAULTY_PRONE) {
            double probability = 0.5;//pick with probability 0.5 wether to go silent or not
            double random = new Random().nextDouble();
            if (random > probability) {
                this.state = State.SILENT;
                log.info("\t\t\t\t\t\tp" + self().path().name() + " SILENT");
            }

        } else if (!(state == State.DECIDED) && !(this.state == State.SILENT)) {

            if (message instanceof Members) {//save the system's info
                Members m = (Members) message;
                processes = m;
                log.info("p" + self().path().name() + " received processes info");

            } else if (message instanceof CrashMsg) {//receive the crash message
                this.state = State.FAULTY_PRONE;
                log.info("\tp" + self().path().name() + " in now faulty prone");
            } else if (message instanceof LaunchMsg) {//receive the launch message and start the thread responsible of making the proposals
                launcher = new Launcher(new Random().nextInt(2));
                launcher.start();
            } //messages defined by the OFC algorithm seen in course
            else if (message instanceof OfconsProposeMsg) {
                OfconsProposeMsg m = (OfconsProposeMsg) message;
                this.ofconsProposeReceived(m.v);
            } else if (message instanceof ReadMsg) {
                ReadMsg m = (ReadMsg) message;
                this.readReceived(m.ballot, getSender());
            } else if (message instanceof AbortMsg) {
                AbortMsg m = (AbortMsg) message;
                this.abortReceived(m.ballot);
            } else if (message instanceof GatherMsg) {
                GatherMsg m = (GatherMsg) message;
                this.gatherReceived(m.ballot, m.imposeballot, m.estimate, getSender());
            } else if (message instanceof ImposeMsg) {
                ImposeMsg m = (ImposeMsg) message;
                this.imposeReceived(m.ballot, m.proposal, getSender());
            } else if (message instanceof AckMsg) {
                AckMsg m = (AckMsg) message;
                this.ackReceived(m.ballot, getSender());
            } else if (message instanceof DecideMsg) {
                DecideMsg m = (DecideMsg) message;
                this.decideReceived(m.proposal, getSender());
            } else if (message instanceof HoldMsg) {
                this.state = State.ON_HOLD;
            }

        }

    }

    /**
     *
     * @param <X> class encapsulating a tuple
     */
    private static class Tuple<X> {

        public final X x;
        public final int y;

        public Tuple(X x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "{" + x + "," + y + '}';
        }

    }

    /**
     * Enumeration containing the possible states of a process
     */
    enum State {
        FAULTY_PRONE, SILENT, ON_HOLD, CORRECT, DECIDED;
    }

    /**
     * Class was added in order to execute the never ending proposals when a
     * process is launched. The real reason behind this class being a thread is
     * that it could be stopped by the process when a HOLD message is received.
     * If the proposals were all done in a function inside the process, it
     * wouldn't be able to check its mailbox and receive the HOLD message it
     * would therefore loop forever.
     */
    class Launcher extends Thread {

        private int proposal;

        public Launcher(int proposal) {
            this.proposal = proposal;
            this.setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            int i = 0;
            while (!(state == Process.State.DECIDED) && !(state == Process.State.ON_HOLD) && !(state == Process.State.SILENT)) {

                log.info("--------------TRY " + i + " p" + self().path().name() + " proposing ");
                self().tell(new OfconsProposeMsg(proposal), ActorRef.noSender());

                try {
                    Thread.sleep(0, 0);//this apparently has the effect of slowing down the thread, giving the opportunity to the process te receive other messages.
                    //the value of the sleep method depends on the machine and the memory available.
                } catch (InterruptedException ex) {
                    Logger.getLogger(Process.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            log.error("p" + self().path().name() + "'s THREAD STOPPED proposing because " + state);

        }

    }

}
