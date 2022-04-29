/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rover;

import agents.LARVAFirstAgent;
import ai.Choice;
import ai.DecisionSet;
import Environment.Environment;
import ai.Functional;
import ai.Plan;
import console.Console;
import data.Ole;
import data.OleConfig;
import geometry.Compass;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.Collections;
import messaging.Sentence;
import swing.OleDialog;
import world.Perceptor;

public class Rover_Deliberative extends LARVAFirstAgent {

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, COMISSIONING, JOINPROBLEM, SOLVEPROBLEM, DEAD, CLOSEPROBLEM, EXIT, BLOCKED,
    }
    Status mystatus, mysubstatus;
    String service = "PManager",
            problemManager = "", sessionKey, sessionManager;
    ACLMessage open, store;
    Sentence mySentence;
    // Heuristic0

    ACLMessage _inbox, _outbox;
    String _key, _EMPTY = "", map = "World6";

    // DBA2021
    String _receiver;
    public String _mySensors[] = new String[]{
        glossary.sensors.ALIVE.name(),
        glossary.sensors.ALTITUDE.name(),
        glossary.sensors.ANGULAR.name(),
        glossary.sensors.COMPASS.name(),
        glossary.sensors.DISTANCE.name(),
        glossary.sensors.ENERGY.name(),
        glossary.sensors.GPS.name(),
        glossary.sensors.LIDAR.name(),
        glossary.sensors.ONTARGET.name(),
        glossary.sensors.THERMAL.name(),
        glossary.sensors.VISUAL.name()
    }, myAttachments[] = new String[_mySensors.length];

    boolean step = true, showConsole = false;

    Console console;
    int cw = 100, ch = 100;
    int limitEnergy = 400;

    @Override
    public void setup() {
        super.setup();
        OleConfig problemCfg = new OleConfig();
        problemCfg.loadFile("config/Problems.conf");
        problemName = problemCfg.getTab("LARVA").getField("Problem");
        showConsole = this.oleConfig.getTab("Display").getBoolean("Show console");
        logger.onTabular();
        logger.onOverwrite();
        logger.setLoggerFileName(getAID().getLocalName() + ".json");
        Info("Setup and configure agent");
        myAttachments = new String[_mySensors.length];
        mystatus = Status.CHECKIN;
        _key = "";
        A = new DecisionSet();
        E = new Environment();
         E.setTarget(new Point3D(0,0,0));
       step = true;
        A.
                addChoice(new Choice("HALT")).
                addChoice(new Choice("IDLE")).
                addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
        this.doNotExit();
    }

 
    @Override
    protected boolean Ve(Environment E) {
        if (E == null || E.getGround() < 0
                || E.getX() < 0 || E.getX() >= E.getWorldWidth()
                || E.getY() < 0 || E.getY() >= E.getWorldHeight()
                || E.getEnergy() == 0) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean G(Environment E) {
        if (!Ve(E)) {
            return false;
        }
        return E.isOntarget();
    }

    @Override
    protected Environment T(Environment E, Choice a) {
        if (E != null) {
            return E.simmulate(a);
        } else {
            return null;
        }
    }

    @Override
    public boolean Va(Environment E, Choice a) {
        if (a == null) {
            return false;
        }
        switch (a.getName().toUpperCase()) {
            case "LEFT":
                return !E.isOntarget() && E.isFreeFrontLeft()
                        && E.isTargetLeft() && E.getEnergy() >= limitEnergy;
            case "RIGHT":
                return !E.isOntarget() && (E.isFreeFrontRight() && E.isTargetRight() || !E.isFreeFront())
                        && E.getEnergy() >= limitEnergy;
            case "MOVE":
                return !E.isOntarget() && E.getEnergy() >= limitEnergy && E.isFreeFront();
            case "IDLE":
                return true;
            case "HALT":
                return false;

        }
        return false;
    }

    @Override
    protected double U(Environment E) {
        if (E == null) {
            return Choice.MAX_UTILITY;
        } else if (!Ve(E)) {
            return Choice.MAX_UTILITY;
        } else if (E.isOntarget()) {
            return -1000;
        } else {
            return E.getDistance(); //E.getThermalHere();
        }
    }

    protected double UR(Environment E) {
        if (E == null) {
            return Choice.MAX_UTILITY;
        } else if (!Ve(E)) {
            return Choice.MAX_UTILITY;
        } else if (E.isOntarget()) {
            return -1000;
        } else {
            return T(E, new Choice("MOVE")).getDistance();
        }
    }

    @Override
    protected DecisionSet Prioritize(Environment E, DecisionSet A) {
        for (Choice a : A) {
            if (a.getName().equals("IDLE") && Va(E, a)) {
                a.setUtility(Choice.MAX_UTILITY / 1000);
            } else if (Va(E, a)) {
//                    a.setUtility(U(T(E, a)));
                if (a.getName().equals("LEFT") || a.getName().equals("RIGHT")) {
                    a.setUtility(UR(T(E, a)));
                } else {
                    a.setUtility(U(T(E, a)));
                }
            } else {
                a.setUtility(Choice.MAX_UTILITY);
            }
        }
        Collections.sort(A);
        Collections.reverse(A);
        return A;
    }

    protected Plan AgP(Environment E, DecisionSet A) {
        Plan plan = new Plan();
//        for (int i = 0; i < 10; i++) {
//            plan.add(new Choice("MOVE"));
//        }
//        plan.add(new Choice("LEFT"));
//        plan.add(new Choice("LEFT"));
//        return plan;
        Choice a;
        System.out.println("Searching plan");
        Environment Ei, Ef;
        Ei = E;
        for (int i = 0; i < E.getAbsoluteLidar()[0].length / 2 - 2; i++) {
//        for (int i = 0; i < 6; i++) { //E.getAbsoluteLidar()[0].length/2-2; i++) {
            if (Ei == null) {
                return plan;
            } else if (G(Ei)) {
                return plan;
            } else if (A.isEmpty()) {
                return plan;
            } else {
                a = Ag(Ei, A);
                this.printMyStatusFunctional(Ei, A);
                this.waitRemoteSemaphore();
                if (a != null && a.getUtility() != Perceptor.NULLREAD && !a.getName().equals("IDLE") && !G(E)) {
                    plan.add(a);
                    Ef = Ei.simmulate(a);
                    Ei = Ef;
                }
            }
        }
        return plan;
    }
   @Override
    protected Choice Ag(Environment E, DecisionSet A) {
        if (G(E)) {
            return null;
        } else if (A.isEmpty()) {
            return null;
        } else {
            A = Prioritize(E, A);
            return A.Best();
        }
    }
      Status solveProblemDeliberative() {
        /// Percibir        
        doReadPerceptions();

        // Analizar objetivo
        if (G(E)) {
            Info("The problem is over");
            this.Message("The problem " + problemName + " has been solved");
            return Status.CLOSEPROBLEM;
        }
        if (!Ve(E)) {
            this.Error("The agent is not alive");
            return Status.CLOSEPROBLEM;
        }
//        printMyStatusFunctional(E, A);

        // Game over
        Plan plan = AgP(E, A);
        while (!plan.isEmpty()) {
            Choice a = plan.get(0);
            plan.remove(0);
            if (a.getName().equals("HALT")) {
                Info("Halting the problem");
                Alert("Halting the problem");
                return Status.CLOSEPROBLEM;
            } else {// Execute
                Info("Excuting " + a);
                this.doExecute(a);
                this.printMyStatusFunctional(E, A);
//                try {
//                    Thread.sleep(this.frameDelay / 5);
//                } catch (Exception ex) {
//                }
            }
        }
        return mystatus;
    }
//    @Override

    public void Execute() {

        Info("Status: " + mystatus.name());
        switch (mystatus) {
            case CHECKIN:
                mystatus = checkIn();
                break;
            case OPENPROBLEM:
                mystatus = openProblem(problemName);
                break;
            case COMISSIONING:
                mystatus = getResources();
                break;
            case JOINPROBLEM:
                mystatus = joinProblem();
                if (showConsole) {
                    console = new Console("Register of decisions", cw, ch, 8);
                }
                break;
            case SOLVEPROBLEM:
                mystatus = solveProblemDeliberative();
                break;
            case CLOSEPROBLEM:
                mystatus = closeProblem();
                break;
            case CHECKOUT:
                mystatus = checkOut();
                break;
            case BLOCKED:
                this.defaultBehaviour.block();
                break;
            case EXIT:
            default:
                this.doExit();
                break;
        }
    }

    @Override
    public void takeDown() {
        Info("Taking down and deleting agent");
        super.takeDown();

    }

    Status checkIn() {
        Info("Loading passport and checking-in");
        if (!this.doLARVACheckin()) {
            Info("Check-in failed");
            return Status.EXIT;
        } else {
            Info("Check-in OK");
            return Status.OPENPROBLEM;
        }

    }

    Status openProblem(String problemName) {
        Info("Searching who is ProblemManager");
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service " + service + " is down");
            return Status.EXIT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        Info("Request " + problemManager + " to open problem " + problemName);
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problemName);
        this.LARVAsend(outbox);
        open = LARVAblockingReceive();
        if (open == null) {
            this.Alert("The server does not answer");
            return Status.EXIT;
        }
        Info("Analyzing answer");
        mySentence = new Sentence().parseSentence(open.getContent());
        if (mySentence.isNext("AGREE")) {
            sessionKey = mySentence.next(3);
            inbox = LARVAblockingReceive();
            sessionManager = inbox.getSender().getLocalName();
            Info("Assigned to " + sessionManager + " in problem " + problemName + " during session " + sessionKey);
            return Status.COMISSIONING;
        } else {
            Error(mySentence.getSentence());
            return Status.CHECKOUT;
        }

    }

    public Status getResources() {
        String storem;
        String message = "Acquired the following products\n";

        Info("Searching for stores to commision the sensors");
        if (this.DFGetAllProvidersOf("STORE " + this.sessionKey).isEmpty()) {
            Error("Sorry service STORE not found");
            return Status.CLOSEPROBLEM;
        }
        storem = this.DFGetAllProvidersOf("STORE " + sessionKey).get(0);
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(storem, AID.ISLOCALNAME));
        outbox.setContent("QUERY PRODUCTS SESSION " + this.sessionKey);
        this.LARVAsend(outbox);
        store = this.LARVAblockingReceive();
        Info("ShoppingList for STORE agent " + storem + ": " + store.getContent());
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(storem, AID.ISLOCALNAME));
        for (int i = 0; i < _mySensors.length; i++) {
            Info("Request sensor " + _mySensors[i] + " to " + storem);
            outbox.setContent("REQUEST PRODUCT " + _mySensors[i] + " SESSION " + sessionKey);
            LARVAsend(outbox);
            store = LARVAblockingReceive();
            myAttachments[i] = store.getContent().split(" ")[2];
            message += _mySensors[i] + "  " + myAttachments[i] + "\n";
        }
        message += "\nContinue to solve problem?";
//        if (this.Confirm(message)) {
//            return Status.JOINPROBLEM;
//        } else {
//            return Status.CLOSEPROBLEM;
//        }
        Ole okey = new Ole();
        okey.setField("sessionkey", sessionKey);
        okey.setField("sessionmanager", sessionManager);
        okey.setField("storemanager", storem);
        okey.saveAsFile("./", "session.key", true);
        Info("Comissioning is over");
        return Status.JOINPROBLEM;

    }

    Status joinProblem() {
        Info("Join problem " + problemName + " with " + sessionManager);
        String command = "Request join session " + sessionKey
                + " attach sensors";
        for (int i = 0; i < this._mySensors.length; i++) {
            command += " " + myAttachments[i];
        }
        outbox = inbox.createReply();
        outbox.setContent(command);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
//        Info("Session Manager says: " + inbox.getContent());
        mySentence = new Sentence().parseSentence(inbox.getContent());
        if (mySentence.isNext("CONFIRM")) {
            Info("Session manager " + sessionManager + " has joined to the session " + sessionKey);
            return Status.SOLVEPROBLEM;
        } else {
            Info("Could not join the session " + sessionKey);
            Error(mySentence.getSentence());
            return Status.CLOSEPROBLEM;
        }
    }

    Status doReadPerceptions() {
        Info("Querying the sensors");
        outbox = inbox.createReply();
        outbox.setContent("Query sensors session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
//        Info("\n\n-------------------------------" + this.E..printSensors() + "-------------------------------\n\n");
        E.setExternalPerceptions(inbox.getContent());
        return mystatus;
    }

    void doExecute(Choice a) {
        String action = a.getName();
        Info("Executing " + action);
        outbox = inbox.createReply();
        outbox.setContent("Request execute " + action + " session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        mySentence = new Sentence().parseSentence(inbox.getContent());
        if (mySentence.isNext("INFORM")) {
            Info("Action " + action + " is ok");
            return;
        } else {
            Info("Action " + action + " has failed");
            Error(mySentence.getSentence());
        }
    }

    Status closeProblem() {
        Info("Closing session " + sessionKey + " with poblem " + problemName);
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        return Status.CHECKOUT;
    }

    Status checkOut() {
        Info("Checking out");
        this.doLARVACheckout();
        return Status.EXIT;
    }

    public void printMyStatusFunctional(Environment e, DecisionSet A) {
        if (!showConsole) {
            return;
        }
        int iv1, iv2;
        double db1, db2;
        String svalue;
        console.clearScreen();
        console.setText(Console.white);
        console.setCursorXY(2, 2);
        console.print(this.getLocalName());
        console.setCursorXY(2, 5);
        svalue = String.format(label("X:") + value(" %03d"), e.getX());
        console.print(svalue);
        svalue = String.format(label("\tY:") + value(" %03d"), e.getY());
        console.print(svalue);
        svalue = String.format(label("\tZ:") + value(" %03d"), e.getZ());
        console.print(svalue);
        svalue = String.format(label("\tG:") + value(" %03d"), e.getGround());
        console.print(svalue);
        svalue = String.format("\t" + label("\tSTEP:") + value(" %03d"), e.getNsteps());
        console.print(svalue);
        console.setCursorXY(2, 6);
        svalue = String.format(label("C:") + value(" %2s (%4dº)"), Compass.NAME[e.getCompass() / 45], e.getCompass());
        console.print(svalue);
        svalue = String.format(label("GOAL:") + value(" %3.1f %3.1f"), e.getTarget().getX(), e.getTarget().getY());
        console.print(svalue);
        console.setCursorXY(2, 7);
        db1 = e.getDistance();
        if (db1 == Perceptor.NULLREAD) {
            svalue = label("D: ") + value("XXXXX") + label("\tA: ") + value("XXXXX");
        } else {
            svalue = String.format(label("D: ") + value("%05.1f") + label("\tA: ") + value("%5.1fº (%5.1fº))"),
                    e.getDistance(), e.getAngular(), e.getRelativeangular());
//            svalue = String.format(label("D: ") + value("%05.1f") + label("\tA: ") + value("%5.1f/%5.1fº (%5.1f/%5.1fº))"),
//                    e.getDistance(), e.getAngular(), E.getAngular(), e.getRelativeangular(), E.getRelativeangular());
        }
        console.print(svalue);
        int Obstacle[][] = e.getShortRadar();
        console.setCursorXY(2, 8);
        console.print(label("RADAR"
                + "\tDISTANCES"));
        for (int y = 0; y < Obstacle.length; y++) {
            for (int x = 0; x < Obstacle[0].length; x++) {
                console.setCursorXY(2 + x, 9 + y);
                if (x == Obstacle[0].length / 2 && y == Obstacle.length / 2) {
                    console.print(label("" + Obstacle[x][y]));
                } else {
                    console.print(value("" + Obstacle[x][y]));
                }
            }
        }
        Obstacle = e.getShortDistances();
        for (int y = 0; y < Obstacle.length; y++) {
            for (int x = 0; x < Obstacle[0].length; x++) {
                console.setCursorXY(7 + x * 6, 9 + y);
                if (x == Obstacle[0].length / 2 && y == Obstacle.length / 2) {
                    console.print(String.format(label("%5d"), e.getShortDistances()[x][y]));
                } else {
                    console.print(String.format(value("%5d"), e.getShortDistances()[x][y]));
                }
            }
        }
        console.setCursorXY(2, 12);
        console.println("A=" + this.Prioritize(e, A).toString());
        Obstacle = e.getAbsoluteLidar();
        console.println("Lidar");
        for (int y = 0; y < Obstacle.length; y++) {
            for (int x = 0; x < Obstacle[0].length; x++) {
                svalue = String.format("%4s",
                        (Obstacle[x][y] == Perceptor.NULLREAD
                                ? "XXX" : String.format("%3d", Obstacle[x][y])));
                if (x == Obstacle[0].length / 2 && y == Obstacle.length / 2) {
                    console.print(label(svalue));
                } else {
                    console.print(value(svalue));
                }
            }
            console.println("");
        }
        Obstacle = e.getAbsoluteThermal();
        console.println("Thermal");
        for (int y = 0; y < Obstacle.length; y++) {
            for (int x = 0; x < Obstacle[0].length; x++) {
                svalue = String.format("%4s",
                        (Obstacle[x][y] == Perceptor.NULLREAD
                                ? "XXX" : String.format("%3d", Obstacle[x][y])));
                if (x == Obstacle[0].length / 2 && y == Obstacle.length / 2) {
                    console.print(label(svalue));
                } else {
                    console.print(value(svalue));
                }
            }
            console.println("");
        }
        console.setCursorXY(2, 12);
        console.setText(Console.white);
        console.doFrame(1, 1, cw, 3);
        console.doFrame(1, 4, cw, ch - 3);
    }

    public String label(String x) {
        return Console.defText(Console.white) + x;
    }

    public String value(String x) {
        return Console.defText(Console.gray) + x;
    }

}
