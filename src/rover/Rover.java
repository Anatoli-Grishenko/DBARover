package rover;

import appboot.LARVABoot;
import static crypto.Keygen.getHexaKey;

public class Rover {

    public static void main(String[] args) {
        LARVABoot connection = new LARVABoot(LARVABoot.METAL);

//            connection.Boot("localhost",1099);
            connection.loadAgent("RReactive", Rover_Reactive.class);
            connection.loadAgent("RDeliberative", Rover_Deliberative.class);
//            connection.loadAgent("DeliberativeA", TieExplorerAI_Deliberative2.class);
            connection.loadAgent("RSubsumption", Rover_Subsumption.class);
            
        connection.WaitToShutDown();
//        jade.WaitToClose().ShutDown();
    }

}
