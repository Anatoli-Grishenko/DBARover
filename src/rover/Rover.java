package rover;

import appboot.LARVABoot;
import static crypto.Keygen.getHexaKey;

public class Rover {

    public static void main(String[] args) {
        LARVABoot connection = new LARVABoot(LARVABoot.METAL);

//            connection.Boot("localhost",1099);
            connection.loadAgent("RReactive2223", Rover_Reactive_2223.class);
            connection.loadAgent("RDeliberative2223", Rover_Deliberative_2223.class);
            connection.loadAgent("RSubsumption2223", Rover_Subsumption_2223.class);
            
        connection.WaitToShutDown();
//        jade.WaitToClose().ShutDown();
    }

}
