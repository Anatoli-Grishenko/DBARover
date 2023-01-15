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
import console.Console;
import data.OleConfig;
import geometry.Compass;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import messaging.Sentence;
import world.Perceptor;

public class Rover_Auto_2223 extends LARVAFirstAgent {

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, JOINPROBLEM, CHOOSEMISSION, SOLVEMISSION, DEAD, CLOSEPROBLEM, EXIT, BLOCKED,
    }
    Status mystatus, mysubstatus;
    String service = "PManager",
            problemManager = "", sessionKey, sessionManager;
    ACLMessage open, store;
    Sentence mySentence;
    // Heuristic0

    ACLMessage _inbox, _outbox;
    String _key, _EMPTY = "", map = "World6", alias = "CHOCOLATE", types[] = {"COLIBRI", "BLACKHAWK", "CORSAIR", "AOSHIMA", "HUMMER", "HEMTT"};

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

    boolean step = true;

    Console console;
    int cw = 100, ch = 100, bx, by;
    int limitEnergy = 400;
    double originaldistance;
    boolean border = false, autonav = false;
    String city, wall = "NO", type;
    OleConfig problemCfg;

    @Override
    public void setup() {
        super.setup();

        problemCfg = new OleConfig();
        problemCfg.loadFile("config/Problems.conf");
        problemName = problemCfg.getTab("LARVA").getField("Problem");
        sessionAlias = problemCfg.getTab("LARVA").getField("Session alias");
        bx = problemCfg.getTab("LARVA").getInt("X");
        by = problemCfg.getTab("LARVA").getInt("Y");
        logger.onTabular();
        logger.onOverwrite();
        logger.setLoggerFileName(getAID().getLocalName() + ".json");
        Info("Setup and configure agent");
        A = new DecisionSet();
        E = new Environment();
        _key = "";
        Choice.setIncreasing();
        step = true;
        Info("Searching who is ProblemManager");
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service " + service + " is down");
            mystatus = Status.EXIT;
            return;
        }
        problemManager = this.DFGetAllProvidersOf("PManager").get(0);
        Info("Found problem manager " + problemManager);

        if (sessionAlias.length() > 0 && this.DFGetAllProvidersOf("OPEN SESSION " + sessionAlias).size() > 0) {
            autonav = true;
            type = types[(int) (Math.random() * types.length)];
        } else {
            autonav = false;
            type = problemCfg.getTab("LARVA").getField("Type");
        }
        mystatus = Status.CHECKIN;
        this.DFSetMyServices(new String[]{"TYPE " + type});
        switch (type) {
            default:
            case "HEMTT":
            case "HUMMER":
            case "CORSAIR":
                A.
                        addChoice(new Choice("STOP")).
                        addChoice(new Choice("IDLE")).
                        addChoice(new Choice("MOVE")).
                        addChoice(new Choice("LEFT")).
                        addChoice(new Choice("RIGHT"));
            case "COLIBRI":
            case "BLACKHAWK":
            case "AOSHIMA":
                A.
                        addChoice(new Choice("STOP")).
                        addChoice(new Choice("IDLE")).
                        addChoice(new Choice("UP")).
                        addChoice(new Choice("DOWN")).
                        addChoice(new Choice("MOVE")).
                        addChoice(new Choice("LEFT")).
                        addChoice(new Choice("RIGHT"));

        }
        this.doNotExit();
    }

    @Override
    public boolean G(Environment e) {
        return E.isOverCurrentMission();
    }

    @Override
    public double Reward(Environment E) {
        return E.getDistance();
    }

    @Override
    protected Choice Ag(Environment E, DecisionSet A) {
        if (G(E)) {
            return null;
        } else if (A.isEmpty()) {
            return null;
        } else {
            if (E.getCurrentTask().startsWith("MOVE")) {
                A = Prioritize(E, A);
                return A.BestChoice();
            } else {
                return null;
            }
        }
    }

    @Override
    public boolean Va(Environment E, Choice a) {
        switch (type) {
            default:
            case "HEMTT":
            case "HUMMER":
            case "CORSAIR":
                return VaV6(E, a);
            case "COLIBRI":
            case "BLACKHAWK":
            case "AOSHIMA":
                return VaV7(E, a);

        }
    }

    // HEMTT, HUMMER, CORSAIR
    public boolean VaV6(Environment E, Choice a) {
        boolean res = false;
        if (a == null) {
            return res;
        }
        if (wall.equals("NO")) {
            switch (a.getName().toUpperCase()) {
                case "LEFT":
                    if (!E.isFreeFront() && E.isTargetLeft() && E.isTargetAhead()) {
                        originaldistance = E.getDistance();
                        wall = "RIGHT";
                        res = true;
                    } else if (E.isTargetLeft()) {
                        if (E.isTargetAhead() && !E.isFreeFrontLeft()) {
                            res = false;
                        } else {
                            res = true;
                        }
                    }
                    break;
                case "RIGHT":
                    if (!E.isFreeFront() && E.isTargetRight() && E.isTargetAhead()) {
                        res = true;
                        originaldistance = E.getDistance();
                        wall = "LEFT";
                    } else if ((E.isTargetRight())) {
                        if (E.isTargetAhead() && !E.isFreeFrontRight()) {
                            res = false;
                        } else {
                            res = true;
                        }
                    }
                    break;
                case "MOVE":
                    if (!E.getOntarget() && E.isFreeFront() && E.isTargetAhead()) { // && (E.isTargetFront() || E.isTargetLeft() || E.isTargetRight())
                        res = true;
                    }
            }
        } else {
            switch (a.getName().toUpperCase()) {
                case "LEFT":
                    if (E.isFreeLeft() && E.isTargetLeft() && E.getDistance() < originaldistance && wall.equals("RIGHT")) {
                        wall = "NO";
                        res = true;
                    } else if (E.isFreeFrontLeft() && wall.equals("LEFT")) {
                        res = true;
                    } else if (wall.equals("RIGHT") && !E.isFreeFront()) {
                        res = true;
                    }
                    break;
                case "RIGHT":
                    if (E.isFreeRight() && E.isTargetRight() && E.getDistance() < originaldistance && wall.equals("LEFT")) {
                        wall = "NO";
                        res = true;
                    } else if (!E.isFreeFront() && wall.equals("LEFT")) {
                        res = true;
                    } else if (E.isFreeFrontRight() && wall.equals("RIGHT")) {
                        res = true;
                    }
                    break;
                case "MOVE":
                    if (!E.getOntarget() && E.isFreeFront() && !E.isFreeFrontLeft() && wall.equals("LEFT")) {
                        res = true;
                    } else if (!E.getOntarget() && E.isFreeFront() && !E.isFreeFrontRight() && wall.equals("RIGHT")) {
                        res = true;
                    }
            }

        }
        if (!wall.equals("NO") && E.getDistance() < 2) {
            wall = "NO";
        }
        return res;
    }

    // COLIBIR, BLACKHAWK, AOSHIMA
    public boolean VaV7(Environment E, Choice a) {
        boolean res = false;
        if (a == null) {
            return res;
        }
        if (wall.equals("NO")) {
            switch (a.getName().toUpperCase()) {
                case "DOWN":
                    if (E.getDistance() == 0 && E.getGround() > 0) {
                        res = true;
                    }
                    break;
                case "UP":
                    if (E.getDistance() > 0 && E.getAltitude() != E.getMaxlevel()) {
                        res = true;
                    }
                    break;
                case "LEFT":
                    if (E.getAltitude() == E.getMaxlevel() && !E.isFreeFront()
                            && E.isTargetLeft() && E.isTargetAhead()) {
                        originaldistance = E.getDistance();
                        wall = "RIGHT";
                        res = true;
                    } else if (E.isTargetLeft() && E.isFreeFrontLeft()) {
                        res = true;
                    }
                    break;
                case "RIGHT":
                    if (E.getAltitude() == E.getMaxlevel() && !E.isFreeFront() && E.isTargetRight() && E.isTargetAhead()) {
                        res = true;
                        originaldistance = E.getDistance();
                        wall = "LEFT";
                    } else if (E.getAltitude() == E.getMaxlevel() && (E.isTargetRight() && E.isFreeFrontRight())) {
                        res = true;
                    }
                    break;
                case "MOVE":
                    if (!E.getOntarget() && E.getAltitude() == E.getMaxlevel() && E.isFreeFront() && !E.isTargetBack()) { // && (E.isTargetFront() || E.isTargetLeft() || E.isTargetRight())
                        res = true;
                    }
                    break;
            }
        } else {
            switch (a.getName().toUpperCase()) {
                case "DOWN":
                    if (E.getDistance() == 0 && E.getGround() > 0) {
                        res = true;
                    }
                    break;
                case "UP":
                    if (E.getDistance() > 0 && E.getAltitude() != E.getMaxlevel()) {
                        res = true;
                    }
                    break;
                case "LEFT":
                    if (E.getAltitude() == E.getMaxlevel() && E.isFreeLeft() && E.isTargetLeft() && E.getDistance() < originaldistance && wall.equals("RIGHT")) {
                        wall = "NO";
                        res = true;
                    } else if (E.getAltitude() == E.getMaxlevel() && E.isFreeFrontLeft() && wall.equals("LEFT")) {
                        res = true;
                    } else if (E.getAltitude() == E.getMaxlevel() && wall.equals("RIGHT") && !E.isFreeFront()) {
                        res = true;
                    }
                    break;
                case "RIGHT":
                    if (E.getAltitude() == E.getMaxlevel() && E.isFreeRight() && E.isTargetRight() && E.getDistance() < originaldistance && wall.equals("LEFT")) {
                        wall = "NO";
                        res = true;
                    } else if (E.getAltitude() == E.getMaxlevel() && !E.isFreeFront() && wall.equals("LEFT")) {
                        res = true;
                    } else if (E.getAltitude() == E.getMaxlevel() && E.isFreeFrontRight() && wall.equals("RIGHT")) {
                        res = true;
                    }
                    break;
                case "MOVE":
                    if (!E.getOntarget() && E.getAltitude() == E.getMaxlevel() && E.isFreeFront() && !E.isFreeFrontLeft() && wall.equals("LEFT")) {
                        res = true;
                    } else if (!E.getOntarget() && E.getAltitude() == E.getMaxlevel() && E.isFreeFront() && !E.isFreeFrontRight() && wall.equals("RIGHT")) {
                        res = true;
                    }
                    break;
            }

        }
        if (!wall.equals("NO") && E.getDistance() < 2) {
            wall = "NO";
        }
        return res;
    }

    @Override
    protected DecisionSet Prioritize(Environment E, DecisionSet A) {
        for (Choice a : A) {
            if (Va(E, a)) {
                if (a.getName().equals("LEFT") || a.getName().equals("RIGHT")) {
                    a.setUtility(Reward(S(S(E, a), new Choice("MOVE"))));
                } else {
                    a.setUtility(U(S(E, a)));
                }
            } else {
                a.setUtility(Choice.MAX_UTILITY);
            }
        }
        A.sort();
        return A;
    }

    Status solveMission() {
        // Analizar objetivo
        if (G(E)) {
            Info("The problem is over");
            if (!autonav) {
                this.Message("The problem " + problemName + " has been solved");
            }
            return Status.CLOSEPROBLEM;
        }
        printMyStatusFunctional(E, A);
        // Game over
        Choice a = Ag(E, A);
        if (a == null) {
            Info("Found no action to execute");
            if (!autonav) {
                Alert("Found no action to execute");
            }
            return Status.CLOSEPROBLEM;
        } else if (a.getName().equals("STOP")) {
            Info("Halting the problem");
            if (!autonav) {
                Alert("Halting the problem: " + E.getStatus());
            }
            return Status.CLOSEPROBLEM;
        } else if (a.getName().equals("IDLE")) {
            Info("Halting the problem");
            if (!autonav) {
                Alert("Halting the problem: don't know what to do");
            }
            return Status.CLOSEPROBLEM;
        } else {// Execute
            Info("Excuting " + a);
            if (!this.doExecute(a)) {
                return Status.BLOCKED;
            }
            doReadPerceptions();
            printMyStatusFunctional(E, A);
            if (!Ve(E)) {
                if (!autonav) {
                    this.Error("The agent is not alive");
                }
                return Status.CLOSEPROBLEM;
            } else if (E.checkCurrentTask()) {
                return doneTask(E.getCurrentTask());
            }
            return mystatus;
        }
    }
    //
    //    @Override

    public void Execute() {

        Info("Status: " + mystatus.name());
//        Info("Mission: "+E.getCurrentMission()+"/"+E.getCurrentGoal());
        switch (mystatus) {
            case CHECKIN:
                mystatus = checkIn();
                break;
            case OPENPROBLEM:
                mystatus = openProblem(problemName);
                break;
            case JOINPROBLEM:
                mystatus = joinProblemAsks();
                if (showConsole) {
                    console = new Console("Register of decisions", cw, ch, 8);
                }
                break;
            case CHOOSEMISSION:
                mystatus = chooseMission();
                break;
            case SOLVEMISSION:
                mystatus = solveMission();
                break;
            case CLOSEPROBLEM:
                if (autonav) {
                    mystatus = Status.CHOOSEMISSION;
                } else {
                    mystatus = closeProblem();
                }
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
//        System.out.println(E.getDeepPerceptions().printStatus("Myself"));
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
        // First check  if alias is open
        String opener;
        if (autonav) {
            Info("Session " + sessionAlias + " seems to be already open");
            opener = this.DFGetAllProvidersOf("OPEN ALIAS " + sessionAlias).get(0);
            for (String service : this.DFGetAllServicesProvidedBy(opener)) {
                if (service.startsWith(sessionAlias)) {
                    sessionKey = service.split(" ")[1];
                    if (this.DFGetAllProvidersOf("SESSION MANAGER " + this.sessionKey).isEmpty()) {
                        Error("Sorry service SESSION MANAGER not found");
                        return Status.CLOSEPROBLEM;
                    }
                    this.sessionManager = this.DFGetAllProvidersOf("SESSION MANAGER " + this.sessionKey).get(0);
                    Info("Assigned to " + sessionManager + " in problem " + problemName + " during session " + sessionKey);
                    return Status.JOINPROBLEM;
                }
            }
            Error("Sorry service SESSION MANAGER not found");
            return Status.CHECKOUT;

        } else {
            Info("Request " + problemManager + " to open problem " + problemName);
            this.outbox = new ACLMessage();
            outbox.setSender(getAID());
            outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
            if (sessionAlias.length() > 0) {
                outbox.setContent("Request open " + problemName + " alias " + sessionAlias);
            } else {
                outbox.setContent("Request open " + problemName);
            }
            this.LARVAsend(outbox);
            open = this.myblockingReceive(problemManager);
            if (open == null) {
                this.Alert("The server does not answer");
                return Status.EXIT;
            }
            Info("Analyzing answer");
            mySentence = new Sentence().parseSentence(open.getContent());
            if (mySentence.isNext("AGREE")) {
                sessionKey = mySentence.next(3);
                this.DFAddMyServices(new String[]{"OPEN SESSION "+sessionKey, sessionKey+" " + sessionKey});
                inbox = LARVAblockingReceive();
                sessionManager = inbox.getSender().getLocalName();
                Info("Assigned to " + sessionManager + " in problem " + problemName + " during session " + sessionKey);
                return Status.JOINPROBLEM;
            } else {
                Error(mySentence.getSentence());
                return Status.CHECKOUT;
            }
        }

    }

    Status joinProblemAsks() {
        String ctarget = null;
        Info("Join problem " + problemName + " with " + sessionManager);
        String command, cities[];
        if (!autonav) {
            if (type.equals("HUMMER") || type.equals("HEMTT")) {
                this.doQueryCities("CITIES");
            } else if (type.equals("COLIBRI") || type.equals("BLACKHAWK")) {
                this.doQueryCities("CITIES");
            } else if (type.equals("CORSAIR")) {
                this.doQueryCities("PORT");
            } else if (type.equals("AOSHIMA")) {
                this.doQueryCities("HYBRID");
            }

        }

        if (autonav) {
            city = "Barung";
        } else {
            cities = E.getCityList();
            city = this.inputSelect("Please type the base or leave empty", cities, "");
        }

        if (city != null && city.length() > 0) {
            command = "Request join session " + sessionKey + " in " + city;
        } else {
//            command = "Request join session " + sessionKey + " AT 25 18";
//            command = "Request join session " + sessionKey + " AT 10 18";
            command = "Request join session " + sessionKey + " AT "
                    + problemCfg.getTab("LARVA").getInt("X")
                    + " " + problemCfg.getTab("LARVA").getInt("Y");
        }
        if (autonav) {
            outbox = new ACLMessage();
            outbox.setSender(this.getAID());;
            outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        } else {
            outbox = inbox.createReply();
        }
        outbox.setContent(command);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        mySentence = new Sentence().parseSentence(inbox.getContent());
        if (mySentence.isNext("CONFIRM")) {
            this.doReadPerceptions();
            this.DFAddMyServices(new String[]{"SESSION " + sessionKey});
            Info("Session manager " + sessionManager + " has joined to the session " + sessionKey);
            return Status.CHOOSEMISSION;
        } else {
            Info("Could not join the session " + sessionKey);
            Error(mySentence.getSentence());
            return Status.CLOSEPROBLEM;
        }
    }

    protected void activateCurrentMission(String mission) {
        this.DFAddMyServices(new String[]{"MISSION " + mission});
        E.activateMission(mission);
    }

    protected Status activateTask(String task) {
        this.DFAddMyServices(new String[]{"TASK " + task});
        String parameters[] = E.getCurrentTask().split(" ");
        if (parameters[0].equals("MOVEIN")) {
            outbox = inbox.createReply();
            outbox.setContent("Request course in " + parameters[1] + " Session " + sessionKey);
            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();
            if (!inbox.getContent().toUpperCase().startsWith("FAILURE")) {
                E.setExternalPerceptions(inbox.getContent());
                return Status.SOLVEMISSION;
            } else {
                if (!autonav) {
                    Error("Unable to find a path to " + parameters[1]);
                }
                return Status.CLOSEPROBLEM;
            }
        } else if (parameters[0].equals("MOVETO")) {
            outbox = inbox.createReply();
            outbox.setContent("Request course to " + Integer.parseInt(parameters[1]) + " "
                    + Integer.parseInt(parameters[2]) + " Session " + sessionKey);
            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();
            if (!inbox.getContent().toUpperCase().startsWith("FAILURE")) {
                E.setExternalPerceptions(inbox.getContent());
                return Status.SOLVEMISSION;
            } else {
                Error("Unable to find a path to " + parameters[1] + " " + parameters[2]);
                return Status.CLOSEPROBLEM;
            }
        } else {
            return Status.CLOSEPROBLEM;
        }

    }

    protected Status doneTask(String task) {
        this.DFRemoveMyServices(new String[]{"TASK " + task});
        if (!E.isOverCurrentMission()) {
            E.nextTask();
            return activateTask(E.getCurrentTask());
        } else {
            return mystatus;
        }
    }

    Status chooseMission() {
        Info("Querying the missions");
        outbox = inbox.createReply();
        outbox.setContent("Query missions session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        E.setExternalObjects(inbox.getContent());
        String m;
        if (E.getMissionsSize() == 1) {
            m = E.getMissionName(0);
        } else {
            if (autonav) {
                m = E.getAllMissions()[(int) (E.getMissionsSize() * Math.random())];
            } else {
                m = this.inputSelect("Please chhoose a mission", E.getAllMissions(), "");
            }
//            E.activateCurrentMission(E.getAllMissions()[(int) (E.getMissionSetSize() * Math.random())]);
        }
//        String parameters[] = E.getCurrentGoal().split(" ");
//        if (parameters[0].equals("MOVEIN")) {
//            outbox = inbox.createReply();
//            outbox.setContent("Request course in " + parameters[1] + " Session " + sessionKey);
//            this.LARVAsend(outbox);
//            inbox = this.LARVAblockingReceive();
//            if (!inbox.getContent().toUpperCase().startsWith("FAILURE")) {
//                E.setExternalPerceptions(inbox.getContent());
//                return Status.SOLVEMISSION;
//            } else {
//                Error("Unable to find a path to " + parameters[1]);
//                return Status.CLOSEPROBLEM;
//            }
//        } else if (parameters[0].equals("MOVETO")) {
//            outbox = inbox.createReply();
//            outbox.setContent("Request course to " + Integer.parseInt(parameters[1]) + " "
//                    + Integer.parseInt(parameters[2]) + " Session " + sessionKey);
//            this.LARVAsend(outbox);
//            inbox = this.LARVAblockingReceive();
//            if (!inbox.getContent().toUpperCase().startsWith("FAILURE")) {
//                E.setExternalPerceptions(inbox.getContent());
//                return Status.SOLVEMISSION;
//            } else {
//                Error("Unable to find a path to " + parameters[1] + " " + parameters[2]);
//                return Status.CLOSEPROBLEM;
//            }
//
//        }
        activateCurrentMission(m);
        return activateTask(E.getCurrentTask());
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

    Status doQueryCities(String type) {
        Info("Querying " + type);
        outbox = inbox.createReply();
        outbox.setContent("Query " + type + " session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
//        Info("\n\n-------------------------------" + this.E..printSensors() + "-------------------------------\n\n");
        E.setExternalPerceptions(inbox.getContent());
        return mystatus;
    }

    boolean doExecute(Choice a) {
        String action = a.getName();
        Info("Executing " + action);
        outbox = inbox.createReply();
        outbox.setContent("Request execute " + action + " session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        mySentence = new Sentence().parseSentence(inbox.getContent());
        if (mySentence.isNext("INFORM")) {
            Info("Action " + action + " is ok");
            return true;
        } else {
            Info("Action " + action + " has failed");
            if (!autonav) {
                Error(mySentence.getSentence());
            }
            return false;
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

    public ACLMessage myblockingReceive(String senderName) {
        return LARVAblockingReceive(MessageTemplate.MatchSender(new AID(senderName, AID.ISLOCALNAME)));
    }

    public String outOf(String[] list, String not) {
        String res;
        int i, n = list.length;
        do {
            i = (int) (Math.random() * n);
        } while (!list[i].equals(not));
        return list[i];

    }

    public void printMyStatusFunctional(Environment E, DecisionSet A) {
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
                + "\tDISTANCES  \tWALL: " + wall));
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
        console.println("   G   L   F   R");
        console.println("   " + (G(E) ? "+" : "-") + "  " + (E.isFreeFrontLeft() ? "+" : "-") + "  " + (E.isFreeFront() ? "+" : "-") + "  " + (E.isFreeFrontRight() ? "+" : "-"));
        console.println(E.getGPSVector().toString());
        Obstacle = E.getAbsoluteLidar();
        console.println("Lidar");
        for (int y = 0; y < Obstacle[0].length; y++) {
            for (int x = 0; x < Obstacle.length; x++) {
                svalue = String.format("%4s",
                        (Obstacle[x][y] == Perceptor.NULLREAD
                                ? "XXX" : String.format("%3d", Obstacle[x][y])));
                if (x == Obstacle.length / 2 && y == Obstacle[0].length / 2) {
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
