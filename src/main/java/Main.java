
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;


/**
 *
 * @author anna-monica
 */
public class Main {

    public static int N = 100;
    public static int f = 49;
    public static int timeout = 50;
    public static int waittime = 70000;
    static long timeSarted;

    public static void main(String[] args) throws InterruptedException {
//        N = Integer.parseInt(args[0]);
//        f = Integer.parseInt(args[1]);
//        timeout = Integer.parseInt(args[2]);
//        waittime=Integer.parseInt(args[3]);

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("GIVING UP System started with N=" + N + ", f=" + f + ", timeout=" + timeout);

        ArrayList<ActorRef> references = new ArrayList<>();
        
        for (int i = 0; i < N; i++) {
            timeSarted = System.currentTimeMillis();
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N, timeSarted), "" + i);
            references.add(a);
        }

        //give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }

        //picking f random processes and sending them crash messages
        Collections.shuffle(references);
        Set<ActorRef> fault_prone = new HashSet<>();

        for (int i = 0; i < f; i++) {
            fault_prone.add(references.get(i));
            references.get(i).tell(new CrashMsg(), ActorRef.noSender());
        }

        //sending launch messages to all
        for (ActorRef actor : references) {
            actor.tell(new LaunchMsg(), ActorRef.noSender());
        }
        //wait 
        Thread.sleep(timeout);

        //picking a random leader
        int index;
        do {
            index = new Random().nextInt(references.size());
        } while (fault_prone.contains(references.get(index)));

        ActorRef leader = references.get(index);
        system.log().error("..............................................................................Leader is p" + leader.path().name());

        //send the others a hold message
        for (ActorRef actor : references) {
            if (!actor.equals(leader)) {
                actor.tell(new HoldMsg(), ActorRef.noSender());
                system.log().info("\t\t\tHOLD MESSAGE to p" + actor.path().name());
            }
        }

        try {
            waitBeforeTerminate();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }
    }

    public static void waitBeforeTerminate() throws InterruptedException {
        Thread.sleep(waittime);
    }

}
