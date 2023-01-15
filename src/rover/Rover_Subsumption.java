/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rover;

import Environment.Environment;
import ai.Subsumption;
import agents.LARVAFirstAgent;
import ai.Choice;
import ai.DecisionSet;
import ai.Rule;
import console.Console;
import static console.Console.gray;
import static console.Console.green;
import data.Ole;
import data.OleConfig;
import geometry.Compass;
import geometry.Point3D;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import messaging.Sentence;
import world.Perceptor;

public class Rover_Subsumption extends LARVAFirstAgent {

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, COMISSIONING, JOINPROBLEM, SOLVEPROBLEM, DEAD, CLOSEPROBLEM, EXIT, BLOCKED,
    }
    Status mystatus, mysubstatus;
    String service = "PManager",
            problemManager = "", sessionKey, sessionManager;
    ACLMessage open, store;
    Sentence mySentence;
    // Heuristic

    ACLMessage _inbox, _outbox;
    String _key, _EMPTY = "", map = "World6";

    // DBA2021
    String _receiver;
    public String _mySensors[] = new String[]{
        glossary.Sensors.ALIVE.name(),
        glossary.Sensors.GROUND.name(),
        glossary.Sensors.ANGULAR.name(),
        glossary.Sensors.COMPASS.name(),
        glossary.Sensors.DISTANCE.name(),
        glossary.Sensors.ENERGY.name(),
        glossary.Sensors.GPS.name(),
        glossary.Sensors.LIDAR.name(),
        glossary.Sensors.ONTARGET.name(),
        glossary.Sensors.THERMAL.name(),
        glossary.Sensors.VISUAL.name()
    }, myAttachments[] = new String[_mySensors.length];

    protected DecisionSet A, lastPlan;
    boolean step = true, showConsole = false;

    Subsumption Subsumption;
    Console console;
    int cw = 75, ch = 50;
    int limitEnergy = 400;
    double originaldistance;
    boolean border = false;

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
        Choice.setDecreasing();
        A = new DecisionSet();
        lastPlan = new DecisionSet();
        E = new Environment();
        E.setTarget(new Point3D(0, 0, 0));
        step = true;
        Subsumption = new Subsumption();
        Ole oModules = problemCfg.getTab("LARVA").getOle("Subs. modules");
        if (oModules.getBoolean("Reach goal")) {
            this.SSA2_ReachGoal();
        }
        if (oModules.getBoolean("Just move")) {
            this.SSA2_JustMove();
        }
        if (oModules.getBoolean("Simple avoid")) {
            this.SSA2_SimpleAvoid();
        }
        if (oModules.getBoolean("Go to goal")) {
            this.SSA2_GoToGoal();

        }
        if (oModules.getBoolean("Long avoid R")) {
            this.SSA2_LongAvoidR();
        }
        if (oModules.getBoolean("Long avoid L")) {
            this.SSA2_LongAvoidL();
        }
        if (oModules.getBoolean("Seek goal")) {
            this.SSA2_SeekGoal();
        }
        if (oModules.getBoolean("Just fly")) {
            this.SSA2_JustFly();
        }
        if (oModules.getBoolean("Recharge")) {
            this.SSA2_Recharge();
        }
        A.
                addChoice(new Choice("STOP")).
                addChoice(new Choice("IDLE")).
                addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
        this.doNotExit();
    }

    @Override
    public void Execute() {

        Info("Status: " + mystatus.name());
        switch (mystatus) {
            case CHECKIN:
                mystatus = CheckIn();
                break;
            case OPENPROBLEM:
                mystatus = openProblem(problemName);
                break;
            case COMISSIONING:
                mystatus = Comissioning();
                break;
            case JOINPROBLEM:
                mystatus = joinProblem();
//                E.left = false;
//                E.right = false;
                if (showConsole) {
                    console = new Console("Register of decisions", cw, ch, 8);
                }
                break;
            case SOLVEPROBLEM:
                mystatus = solveProblem();
                break;
            case CLOSEPROBLEM:
                mystatus = closeProblem();
                break;
            case CHECKOUT:
                mystatus = CheckOut();
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

    Status CheckIn() {
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

    public Status Comissioning() {
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
            this.doReadPerceptions();
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

    Status CheckOut() {
        Info("Checking out");
        this.doLARVACheckout();
        return Status.EXIT;
    }

    @Override
    public double Reward(Environment E) {
        return E.getDistance();
    }

//    protected boolean G(Environment E) {
//        if (!Ve(E)) {
//            return false;
//        }
//        return E.getOntarget();
//    }
//
//    protected boolean Ve(Environment E) {
//        if (E == null || E.getGround() < 0
//                || E.getGPS().getX() < 0 || E.getGPS().getX() >= E.getWorldWidth()
//                || E.getGPS().getY() < 0 || E.getGPS().getY() >= E.getWorldHeight()
//                || E.getEnergy() == 0) {
//            return false;
//        }
//        return true;
//    }
//    @Override
//    protected Choice Ag(Environment E, DecisionSet A) {
//        if (G(E)) {
//            return null;
//        } else if (A.isEmpty()) {
//            return null;
//        } else {
//            A = Prioritize(E, A);
//            if (A.BestChoice().getName().equals("IDLE")) {
//                return A.SecondBestChoice();
//            } else {
//                return A.BestChoice();
//            }
//        }
//    }
//    @Override
//    protected Environment S(Environment E, Choice a) {
//        return null;
//    }
//
//    @Override
//    public boolean Va(Environment E, Choice a) {
//        return false;
//    }
//
//    @Override
//    protected double U(Environment E) {
//        return 0;
//    }
    @Override
    protected DecisionSet Prioritize(Environment E, DecisionSet A) {
        Subsumption.setDecisionSet(A);
        return Subsumption.MakeBestDecision(E);
    }

    Status solveProblem() {
        // Analizar objetivo
        if (G(E)) {
            Info("The problem is over");
            this.Message("The problem " + problemName + " has been solved");
            return Status.CLOSEPROBLEM;
        }
        this.printMyStatusSSA(E, A);
        // Game over
        Choice a = Ag(E, A);
        if (a == null) {
            Info("Found no action to execute");
            Alert("Found no action to execute");
            return Status.CLOSEPROBLEM;
        } else if (a.getName().equals("STOP") || a.getName().equals("IDLE")) {
            Info("Halting the problem");
            Alert("Halting the problem");
            return Status.CLOSEPROBLEM;
        } else {// Execute
            Info("Excuting " + a);
            this.doExecute(a);
            doReadPerceptions();
            this.printMyStatusSSA(E, A);
            if (!Ve(E)) {
                this.Error("The agent is not alive");
                return Status.CLOSEPROBLEM;
            }
            return mystatus;
        }
    }

//    Status solveProblem() {
//        Status next;
//        /// Percibir        
//        doReadPerceptions();
//
//        // Analizar objetivo
//        // Analizar objetivo
//        if (G(E)) {
//            Info("The problem is over");
//            this.Message("The problem " + problemName + " has been solved");
//            next = Status.CLOSEPROBLEM;
//        } else if (!Ve(E)) {
//            this.Error("The agent is not alive");
//            next = Status.CLOSEPROBLEM;
//        } else {
//            // Decide
//            Choice a = Ag(E, A);
//            Info("Found plan" + A);
//            // Game over
//            if (a == null) {
//                Info("Found no action to execute");
//                Alert("Found no action to execute");
//                next = Status.CLOSEPROBLEM;
//            } else if (a.getName().equals("STOP")) {
//                Info("Halting the problem");
//                Alert("Halting the problem");
//                next = Status.CLOSEPROBLEM;
//            } else {// Execute
//                Info("Excuting " + a);
////                while (E.getCompass())
//                this.doExecute(a);
//                next = mystatus;
//            }
//        }
//        Info(Subsumption.toString());
//        this.printMyStatusSSA();
//        return next;
//    }
    protected void SSA2_JustMove() {
        Rule r;

        ////////////////////////////////////////////////////////////////// LAYER
        Subsumption.addLayer("Direct Drive", 100);
        /////////////////////////////////////////// RULE
        Subsumption.addRule("Direct Drive", new Rule("Just move forward").
                setCondition(() -> {
                    return !E.getOntarget();
                }).
                setBody(() -> {
                    return "MOVE";
                }));
    }

    protected void SSA2_SimpleAvoid() {
        Rule r;
        ////////////////////////////////////////////////////////////////// LAYER
        Subsumption.addLayer("Avoid obstacle", 500);
        Subsumption.supress("Avoid obstacle", "Direct Drive");
        /////////////////////////////////////////// RULE
        Subsumption.addRule("Avoid obstacle", new Rule("Avoid").
                setCondition(() -> {
                    return !E.isFreeFront();
                }).
                setBody(() -> {
                    return "RIGHT";
                }));

    }

    protected void SSA2_GoToGoal() {
        Rule r;

        ////////////////////////////////////////////////////////////////// LAYER
        Subsumption.addLayer("Go To Goal", 250);
        Subsumption.supress("Go To Goal", "Direct Drive");
        /////////////////////////////////////////// RULE
        Subsumption.addRule("Go To Goal", new Rule("Left to the goal").
                setCondition(() -> {
                    return !E.getOntarget()  && E.isTargetLeft() && E.isFreeFrontLeft();
                }).
                setBody(() -> {
                    return "LEFT";
                }));
        /////////////////////////////////////////// RULE
        Subsumption.addRule("Go To Goal", new Rule("Right to the goal").
                setCondition(() -> {
                    return !E.getOntarget() && E.isTargetRight() && E.isFreeFrontRight();
                }).
                setBody(() -> {
                    return "RIGHT";
                }));

    }

    protected void SSA2_ReachGoal() {
        Rule r;

        ////////////////////////////////////////////////////////////////// LAYER
        Subsumption.addLayer("Reach Goal", 1000);

        /////////////////////////////////////////// RULE
        Subsumption.addRule("Reach Goal", new Rule("Goal reached").
                setCondition(() -> {
                    return E.getOntarget();
                }).
                setBody(() -> {
                    return "STOP";
                }));

    }

// 15/04/11:28 : uso de left & right
    protected void SSA2_LongAvoidR() {
//        Info("Building rule system");
//        // Processing variables
//        Rule r;
//
//        ////////////////////////////////////////////////////////////////// LAYER
//        Subsumption.addLayer("Long avoid", 520);
//        Subsumption.supress("Long avoid", "Direct Drive");
//        Subsumption.inhibit("Long avoid", "Avoid obstacle");
//        Subsumption.inhibit("Long avoid", "Avoid obstacle+");
//
//        /////////////////////////////////////////// RULE
//        r = new Rule("Wall to right");
//        r.setCondition(() -> {
//            return !E.isFreeFront();
//        });
//        r.setBody(() -> {
//            border = true;
//            if (!border) {
//                border = true;
//                originaldistance = E.getDistance();
//
//            }
//            return "LEFT";
//        });
//        Subsumption.addRule("Long avoid", r);
//
//        r = new Rule("Continue wall right");
//        r.setCondition(() -> {
//            return (border && E.isFreeFrontRight()); // && E.getDistance() >= lastDistance);
//        });
//        r.setBody(() -> {
//            return "RIGHT";
//        });
//        Subsumption.addRule("Long avoid", r);
//
    }

    protected void SSA2_LongAvoidL() {
        Info("Building rule system");
        // Processing variables
        Rule r;

        ////////////////////////////////////////////////////////////////// LAYER
        Subsumption.addLayer("Long avoid", 520);
        Subsumption.supress("Long avoid", "Direct Drive");
        Subsumption.inhibit("Long avoid", "Avoid obstacle");
        Subsumption.inhibit("Long avoid", "Avoid obstacle+");

        /////////////////////////////////////////// RULE
        r = new Rule("Start border obstacle to left");
        r.setCondition(() -> {
            return !border && !E.getOntarget() && !E.isFreeFront();
        });
        r.setBody(() -> {
            if (!border) {
                border = true;
                originaldistance = E.getDistance();
            }
            return "RIGHT";
        });
        Subsumption.addRule("Long avoid", r);     
        
        r = new Rule("Follow border to left");
        r.setCondition(() -> {
            return border && E.isFreeFrontLeft();
        });
        r.setBody(() -> {
            return "LEFT";
        });
        Subsumption.addRule("Long avoid", r);     

        r = new Rule("Follow border to right");
        r.setCondition(() -> {
            return border && !E.isFreeFront();
        });
        r.setBody(() -> {
            return "RIGHT";
        });
        Subsumption.addRule("Long avoid", r);     

    }

    public void SSA2_SeekGoal() {
        Rule r;

        ////////////////////////////////////////////////////////////////// LAYER
        Subsumption.addLayer("Seek goal", 550);
        Subsumption.supress("Seek goal", "Direct Drive");
        Subsumption.inhibit("Seek goal", "Go To Goal");
        /////////////////////////////////////////// RULE
        r = new Rule("Back to goal to left");
        r.setCondition(() -> {
            return (border && E.isFreeLeft()&& E.isTargetLeft() && E.getDistance()<originaldistance);
        });
        r.setBody(() -> {
            border=false;
            return "LEFT";
        });
        Subsumption.addRule("Seek goal", r);

        r = new Rule("Back to goal to right");
        r.setCondition(() -> {
            return (border && E.isFreeRight()&& E.isTargetRight() && E.getDistance()<originaldistance);
        });
        r.setBody(() -> {
            border=false;
            return "RIGHT";
        });
        Subsumption.addRule("Seek goal", r);

        r = new Rule("Goal to left");
        r.setCondition(() -> {
            return (!border && E.isFreeFrontLeft()&& E.isTargetLeft());
        });
        r.setBody(() -> {
            return "LEFT";
        });
        Subsumption.addRule("Seek goal", r);

        r = new Rule("Goal to right");
        r.setCondition(() -> {
            return (!border && E.isFreeFrontRight()&& E.isTargetRight() );
        });
        r.setBody(() -> {
            return "RIGHT";
        });
        Subsumption.addRule("Seek goal", r);

//        ////////////////////////////////////////////////////////////////// LAYER
//        Subsumption.addLayer("Follow Up", 519);
//        r = new Rule("FollowUp");
//        r.setCondition(() -> {
//            return (E.left || E.right);
//        });
//        r.setBody(() -> {
//            if (E.right
//                    && ((E.isFreeFrontLeft()&& E.isTargetLeft() && E.getDistance() < E.lastDistance)
//                    || (E.getLidarRightmost() >= 0 && E.getDistance() < E.lastDistance && Math.abs(E.getRelativeAngular()) <= 45))) {
//                E.right = false;
//                E.left = false;
//                E.lastDistance = -1;
//            }
//            if (E.left
//                    && ((E.getLidarRight() >= 0 && E.getRelativeAngular() <= - 45 && E.getDistance() < E.lastDistance)
//                    || (E.getLidarLeftmost() >= 0 && E.getDistance() < E.lastDistance && Math.abs(E.getRelativeAngular()) <= 45))) {
//                E.right = false;
//                E.left = false;
//                E.lastDistance = -1;
//            }
//            return "IDLE";
//        });
//        Subsumption.addRule("Follow Up", r);
    }

    protected void SSA2_JustFly() {
//        // Processing variables
//        Rule r;
//
//        ////////////////////////////////////////////////////////////////// LAYER
//        Subsumption.addLayer("Fly", 700);
//
//        /////////////////////////////////////////// RULE
//        r = new Rule("Takeoff").
//                setCondition(() -> {
//                    return E.getDistance() > 0
//                            && E.getAltitude() < E.getMaxlevel();
//                }).
//                setBody(() -> {
//                    E.left = false;
//                    E.right = false;
//                    return "UP";
//                });
//        Subsumption.addRule("Fly", r);
//
//        /////////////////////////////////////////// RULE
//        r = new Rule("Land").
//                setCondition(() -> {
//                    return E.getDistance() == 0
//                            && E.getGround() > 0;
//                }).
//                setBody(() -> {
//                    E.left = false;
//                    E.right = false;
//                    return "DOWN";
//                });
//        Subsumption.addRule("Fly", r);
    }

    protected void SSA2_Recharge() {
//        // Processing variables
//        Rule r;
//
//        ////////////////////////////////////////////////////////////////// LAYER
//        Subsumption.addLayer("Recharge", 800);
//
//        /////////////////////////////////////////// RULE
//        r = new Rule("Down to recharge");
//        r.setCondition(() -> {
//            return E.getGround() > 0
//                    && E.getEnergy() <= limitEnergy;
//        });
//        r.setBody(() -> {
//            E.left = false;
//            E.right = false;
//            return "DOWN";
//        });
//        Subsumption.addRule("Recharge", r);
//
//        /////////////////////////////////////////// RULE
//        r = new Rule("Recharge");
//        r.setCondition(() -> {
//            return E.getEnergy() <= limitEnergy
//                    && E.getGround() == 0;
//        });
//        r.setBody(() -> {
//            return "RECHARGE";
//        });
//        Subsumption.addRule("Recharge", r);
    }

    public void printMyStatusSSA(Environment E, DecisionSet A) {
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
        svalue = String.format(label("X:") + value(" %03d"), E.getGPS().getXInt());
        console.print(svalue);
        svalue = String.format(label("\tY:") + value(" %03d"), E.getGPS().getYInt());
        console.print(svalue);
        svalue = String.format(label("\tZ:") + value(" %03d") + "/" + value("%03d"), E.getGPS().getZInt(), E.getMaxlevel());
        console.print(svalue);
        svalue = String.format(label("\tG:") + value(" %03d"), E.getGround());
        console.print(svalue);
        svalue = String.format("\t" + label("\tSTEP:") + value(" %03d"), E.getNSteps());
        console.print(svalue);
        console.setCursorXY(2, 6);
        svalue = String.format(label("C:") + value(" %2s (%4dº)"), Compass.NAME[E.getCompass() / 45], E.getCompass());
        console.print(svalue);
        console.setCursorXY(2, 7);
        db1 = E.getDistance();
        if (db1 == Perceptor.NULLREAD) {
            svalue = label("D: ") + value("XXXXX") + label("\tA: ") + value("XXXXX");
        } else {
            svalue = String.format(label("D: ") + value("%05.2f") + label("\tA: ") + value("%5.2fº (%5.2fº))"),
                    E.getDistance(), E.getAngular(), E.getRelativeAngular());
        }
        console.print(svalue);
        int Obstacle[][] = E.getShortRadar();
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
        Obstacle = E.getShortDistances();
        for (int y = 0; y < Obstacle.length; y++) {
            for (int x = 0; x < Obstacle[0].length; x++) {
                console.setCursorXY(7 + x * 6, 9 + y);
                if (x == Obstacle[0].length / 2 && y == Obstacle.length / 2) {
                    console.print(String.format(label("%5d"), E.getShortDistances()[x][y]));
                } else {
                    console.print(String.format(value("%5d"), E.getShortDistances()[x][y]));
                }
            }
        }
        console.setCursorXY(2, 12);
        console.println("A=" + this.Prioritize(E, A).toString());
        console.println("   " + (border ? Console.defText(green)+"BORDER"+Console.defText(gray) : "------") );
//        console.println("   G   L   F   R");
//        console.println("   " + (G(E) ? "+" : "-") + "  " + (E.isFreeFrontLeft() ? "+" : "-") + "  " + (E.isFreeFront() ? "+" : "-") + "  " + (E.isFreeFrontRight() ? "+" : "-"));
//        console.println(E.getGPSVector().toString());
//        Obstacle = E.getAbsoluteLidar();
//        console.println("Lidar");
//        for (int y = 0; y < Obstacle[0].length; y++) {
//            for (int x = 0; x < Obstacle.length; x++) {
//                svalue = String.format("%4s",
//                        (Obstacle[x][y] == Perceptor.NULLREAD
//                                ? "XXX" : String.format("%3d", Obstacle[x][y])));
//                if (x == Obstacle.length / 2 && y == Obstacle[0].length / 2) {
//                    console.print(label(svalue));
//                } else {
//                    console.print(value(svalue));
//                }
//            }
//            console.println("");
//        }
        console.setCursorXY(2, 15);
        console.println(this.Subsumption.toString());
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
