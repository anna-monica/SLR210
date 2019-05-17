
import akka.actor.ActorRef;
import java.util.ArrayList;

public class Members {
            public final ArrayList<ActorRef> references;
            public final String data;

    public Members(ArrayList<ActorRef> references) {
        this.references = references;
        String s="[ ";
        for (ActorRef a : references){
            s+=a.path().name()+" ";
        }
        s+="]";    
        data=s;
    }
            
}