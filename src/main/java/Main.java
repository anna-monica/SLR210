
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.ArrayList;

/**
 *
 * @author anna-monica
 */
public class Main {

    public static void main(String[] args) {
        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");

        int N = 3;
        ArrayList<ActorRef> references = new ArrayList<ActorRef>();

        for (int i = 0; i < N; i++) {
            // Instantiate first and second actor
            final ActorRef a = system.actorOf(Process.createActor(i,N), "a"+i);
            references.add(a);
        }
        
        //give each process a view of all the other processes
        Members m = new Members(references);
        for(ActorRef actor: references){
            actor.tell(m, ActorRef.noSender());
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
        Thread.sleep(5000);
    }

}
