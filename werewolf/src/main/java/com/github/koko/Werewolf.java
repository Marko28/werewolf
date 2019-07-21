package com.github.koko;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.entity.user.*;
import org.javacord.api.entity.server.*;

import java.io.*;
import java.util.*;


public class Werewolf {

    public static void main(String[] args) {
        String token = ""; // Put in your bot's token.
        DiscordApi api = new DiscordApiBuilder().setToken(token).login().join();

        api.addMessageCreateListener(Werewolf::onMessageCreate);

        // Print the invite url of your bot
        System.out.println("You can invite the bot by using the following url: " + api.createBotInvite());

    }

    private static void onMessageCreate(MessageCreateEvent event) {
        String message = event.getMessage().getContent();

        if (message.startsWith("!ww")) {
            String prompt = message.substring(3);
            if (prompt.equals(" join")) {
                newPlayer(event);
            } else if (prompt.equals(" play")) {
                //if (event.getMessage().getAuthor().isBotOwner()) {
                    if (getPhase() == 0) {
                        setPhase(1);
                        int x = allocateRoles();
                        if (x == -1) {
                            event.getChannel().sendMessage("There are not enough players to start a werewolf game.");
                        } else if (x == -2) {
                            event.getChannel().sendMessage("There are too many players to start a werewolf game.");
                            resetGame();
                            event.getChannel().sendMessage("The werewolf game has been reset.");
                        } else {
                            sendNight(x, event);
                            setPhase(2);
                            int nww = sendRoles(event);
                            messageWolf(nww, event);
                        }

                    } else {
                        event.getChannel().sendMessage("There is a werewolf game in progress.");
                    }
                /*} else {
                    event.getChannel().sendMessage("Only the bot owner can start a werewolf game.");
                }*/
            } else if (prompt.equals(" reset")) {
                //if (event.getMessage().getAuthor().isBotOwner()) {
                    resetGame();
                    event.getChannel().sendMessage("The werewolf game has been reset.");
                /*} else {
                    event.getChannel().sendMessage("Only the bot owner can reset a werewolf game.");
                }*/
            } else if (prompt.startsWith(" act ")) {
                if (getPhase() == 2) {
                    prompt = prompt.substring(5);
                    applyAction(prompt, event);
                } else {
                    event.getChannel().sendMessage("The werewolf game is not in the night phase.");
                }
            } else if (prompt.equals(" reveal")) {
                //if (event.getMessage().getAuthor().isBotOwner()) {
                    revealRoles(event);
                    resetGame();
                /*} else {
                    event.getChannel().sendMessage("Only the bot owner can end a werewolf game.");
                }*/
            } else if (prompt.equals(" rules")) {
                sendRulesH(event);
            } else if (prompt.equals(" roles")) {
                sendRolesH(event);
            } else if (prompt.equals(" help")) {
                sendHelp(event, "");
            } else if (prompt.startsWith(" help ")) {
                prompt = prompt.substring(6);
                sendHelp(event, prompt);
            } else if (prompt.equals("")) {
                sendIntro(event);
            } else {
                event.getChannel().sendMessage("Invalid command.\n" +
                        "Please type '!ww help' for the list of commands.");
            }
        }

    }

    private static void newPlayer(MessageCreateEvent event) {
        String player = event.getMessage().getAuthor().getDiscriminatedName();
        String name = getDisplayName(player);
        if (getPhase() != 0) {
            event.getChannel().sendMessage("There is a werewolf game in progress.");
            return;
        }
        Scanner playerFile = scanFile("game.txt");

        int nPlayers = playerFile.nextInt();
        playerFile.nextLine();

        int isNew = isNewPlayer(playerFile, player, nPlayers);

        if (isNew == 1) {
            nPlayers++;
            event.getChannel().sendMessage(name + " has joined.");
            addNewPlayer(player, nPlayers);
        } else {
            event.getChannel().sendMessage(name + " has already joined.");
        }
        event.getChannel().sendMessage("Total: " + nPlayers);
    }

    private static String getDisplayName(String discriminatedName) {
        return discriminatedName.substring(0, discriminatedName.length()-5);
    }

    private static int isNewPlayer(Scanner playerFile, String player, int n) {
        String aPlayer;
        for (int i=0; i<n; i++) {
            aPlayer = playerFile.nextLine();
            if (aPlayer.equals(player)) {
                return 0;
            }
            playerFile.nextLine();
        }
        playerFile.close();
        return 1;
    }

    private static Scanner scanFile(String fileName) {
        Scanner theFile = null;
        try {
            theFile = new Scanner( new FileInputStream(fileName));
        } catch (FileNotFoundException e) {
            System.out.println("Broken 1.");
        }
        return theFile;
    }

    private static PrintWriter writeFile(String fileName) {
        PrintWriter theFile = null;
        try {
            theFile = new PrintWriter( new FileOutputStream(fileName));
        } catch (FileNotFoundException e) {
            System.out.println("Broken 2.");
        }
        return theFile;
    }

    private static void addNewPlayer(String player, int nPlayers) {
        Scanner playerFileIn = scanFile("game.txt");
        PrintWriter playerFileOut = writeFile("tmp.txt");
        String fileLine;

        playerFileIn.nextLine();
        playerFileOut.println(nPlayers);
        for (int i=0; i<nPlayers-1; i++) {
            fileLine = playerFileIn.nextLine();
            playerFileIn.nextLine();

            playerFileOut.println(fileLine);
            playerFileOut.println("0 0");
        }
        playerFileOut.println(player);
        playerFileOut.println("0 0");

        playerFileIn.close();
        playerFileOut.close();

        renameFile("tmp.txt", "game.txt");
    }

    private static void renameFile(String oldName, String newName) {
        File tmp = new File(oldName);
        File game = new File(newName);
        tmp.renameTo(game);
    }

    private static int getPhase() {
        Scanner phaseFile = scanFile("phase.txt");
        int i = phaseFile.nextInt();
        phaseFile.close();
        return i;
    }

    private static void setPhase(int n) {
        PrintWriter phaseFile = writeFile("phase.txt");
        phaseFile.println(n);
        phaseFile.close();
    }

    private static void resetGame() {
        setPhase(0);
        PrintWriter playerFile = writeFile("game.txt");
        playerFile.println(0);
        playerFile.close();
        int[] p = {0,0,0};
        writePile(p);
        PrintWriter actFile = writeFile("toAct.txt");
        actFile.close();
    }

    private static int allocateRoles() {
        Scanner playerFileIn = scanFile("game.txt");
        int nPlayers = playerFileIn.nextInt();
        playerFileIn.nextLine();

        Scanner setupFile = scanFile("setup.txt");
        int nMin = setupFile.nextInt();
        if (nPlayers < nMin) {
            setupFile.close();
            resetGame();
            return -1;
        }
        int nMax = setupFile.nextInt();
        if (nPlayers > nMax) {
            setupFile.close();
            resetGame();
            return -2;
        }
        setupFile.close();

        int[] roles = getSetup(nPlayers);

        int[] r = new Random().ints(0, nPlayers+3).distinct().limit(nPlayers+3).toArray();

        PrintWriter playerFileOut = writeFile("tmp.txt");
        playerFileOut.println(nPlayers);
        String s;
        int i;

        for (i=0; i<nPlayers; i++) {
            s = playerFileIn.nextLine();
            playerFileIn.nextLine();

            playerFileOut.println(s);
            s = String.format("%d %d", roles[r[i]], roles[r[i]]);
            playerFileOut.println(s);
        }

        playerFileIn.close();
        playerFileOut.close();

        int[] p = {roles[r[i++]], roles[r[i++]], roles[r[i]]};
        writePile(p);

        renameFile("tmp.txt", "game.txt");

        return nPlayers;
    }

    private static int[] getSetup(int n) {
        Scanner setupFile = scanFile("setup.txt");
        int nMin = setupFile.nextInt();
        setupFile.nextLine();

        while (n > nMin) {
            setupFile.nextLine();
            nMin++;
        }

        int[] roles = new int[n+3];
        for (int i=0; i<n+3; i++) {
            roles[i] = setupFile.nextInt();
        }
        setupFile.close();

        return roles;
    }

    private static void sendNight(int n, MessageCreateEvent event) {
        int roles[] = getSetup(n);
        String message = "Roles: " + getRole(roles[0]);
        for (int i=1; i<n+2; i++) {
            message += ", " + getRole(roles[i]);
        }
        message += " and " + getRole(roles[n+2]);
        event.getChannel().sendMessage(message);
        message = "@here NIGHT phase has begun.";
        event.getChannel().sendMessage(message);
        setPhase(2);
    }

    private static void writePile(int[] p) {
        String s = String.format("%d %d %d", p[0], p[1], p[2]);
        PrintWriter pileFile = writeFile("pile.txt");
        pileFile.println(s);
        pileFile.close();
    }

    private static User getUserFromName(String player, Server s) {
        User thePlayer;
        thePlayer = s.getMemberByDiscriminatedName(player).get();
        return thePlayer;
    }

    private static int sendRoles(MessageCreateEvent event) {
        Server server = event.getServer().get();
        String playerS, name, s;
        User thePlayer;
        int role;
        int nww = 0;
        Scanner playerFile = scanFile("game.txt");
        int nPlayers = playerFile.nextInt();
        playerFile.nextLine();

        for (int i=0; i<nPlayers; i++) {
            playerS = playerFile.nextLine();
            name = getDisplayName(playerS);
            thePlayer = getUserFromName(playerS, server);

            role = playerFile.nextInt();
            playerFile.nextLine();
            s = String.format("%s%s", name, roleMessage(role));
            thePlayer.sendMessage(s);
            if (role == 1) {
                nww++;
            }
        }
        playerFile.close();
        return nww; // return number of werewolves
    }

    private static String roleMessage(int n) {
        String m = String.format(", your role is %s.", getRole(n));
        if (n == 2||n == 3||n == 6) {
            m+= "\nYou have no night action.";
        } else {
            m += "\n Please wait for a prompt before completing your night action.";
        }
        return m;
    }

    private static String getRole(int n) {
        if (n == 1) {
            return "WEREWOLF";
        } else if (n == 2) {
            return "MINION";
        } else if (n == 3) {
            return "VILLAGER";
        } else if (n == 6) {
            return "INSOMNIAC";
        } else if (n == 7) {
            return "DRUNK";
        } else if (n == 8) {
            return "TROUBLEMAKER";
        } else if (n == 9) {
            return "ROBBER";
        } else if (n == 10) {
            return "SEER";
        }
        return "";
    }

    private static void messageWolf(int n, MessageCreateEvent event) {
        if (n==0) {
            messageMinion(event);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            messageSeer(event.getServer().get());
            return;
        }
        int nPlayers, role;
        User u;
        Scanner playerFile = scanFile("game.txt");
        nPlayers = playerFile.nextInt();
        playerFile.nextLine();

        if (n==1) {
            String ww = "";
            for (int i=0; i<nPlayers; i++) {
                ww = playerFile.nextLine();
                role = playerFile.nextInt();
                playerFile.nextLine();
                if (role == 1) {
                    break;
                }
            }
            u  = getUserFromName(ww, event.getServer().get());
            u.sendMessage("You are the lone wolf.");
            u.sendMessage("You may now view one card (of three) from the centre pile.");
            u.sendMessage("1: left          2: middle          3: right");
            u.sendMessage("Command: !ww act #");

            setToAct(ww, 1);
            messageMinion(event, ww);

        } else if (n==2) {

            String[] ww = new String[2], name = new String[2];
            int j = 0;
            for (int i=0; i<nPlayers; i++) {
                ww[j] = playerFile.nextLine();
                role = playerFile.nextInt();
                playerFile.nextLine();
                if (role == 1) {
                    if (j == 1) {
                        break;
                    }
                    j++;
                }
            }

            u = getUserFromName(ww[0], event.getServer().get());
            u.sendMessage("You have no night action.");
            name[1] = getDisplayName(ww[1]);
            u.sendMessage("The other WEREWOLF is " + name[1] + ".");

            u = getUserFromName(ww[1], event.getServer().get());
            u.sendMessage("You have no night action.");
            name[0] = getDisplayName(ww[0]);
            u.sendMessage("The other WEREWOLF is " + name[0] + ".");

            messageMinion(event, name[0], name[1]);
            messageSeer(event.getServer().get());
        }

        playerFile.close();
    }

    private static void setToAct(String s, int n) {
        PrintWriter actFile = writeFile("toAct.txt");
        actFile.println(s);
        actFile.println(n);
        actFile.close();
    }

    private static void applyAction(String prompt, MessageCreateEvent event) {
        String player = event.getMessage().getAuthor().getDiscriminatedName();
        Scanner actFile = scanFile("toAct.txt");
        String actor = actFile.nextLine();
        if (player.equals(actor)) {
            int role = actFile.nextInt();
            if (role == 1 || role == 9 || role == 7) {
                int i;
                try {
                    i = Integer.parseInt(prompt);
                } catch (Exception e) {
                    event.getChannel().sendMessage("Invalid command or person.");
                    actFile.close();
                    return;
                }
                if (role == 1) {
                    applyWolf(i, event.getMessage().getAuthor().asUser().get());
                } else if (role == 9) {
                    applyRobber(i, event.getMessage().getAuthor().asUser().get());
                } else if (role == 7) {
                    applyDrunk(i, event.getMessage().getAuthor().asUser().get());
                }
            } if (role == 10 || role == 8) {
                int i1, i2;
                try {
                    i1 = Integer.parseInt(prompt.substring(0,1));
                    i2 = Integer.parseInt(prompt.substring(2,3));
                } catch (Exception e) {
                    event.getChannel().sendMessage("Invalid command or person.");
                    actFile.close();
                    return;
                }
                if (role == 10) {
                    applySeer(i1, i2, event.getMessage().getAuthor().asUser().get());
                } else if (role == 8) {
                    applyTroubleMaker(i1, i2, event.getMessage().getAuthor().asUser().get());
                }
            }
        } else {
            event.getChannel().sendMessage("Invalid command or person.");
        }
        actFile.close();
    }

    private static void applyWolf(int n, User u) {
        if (n<1||n>3) {
            u.sendMessage("Invalid command or person.");
            return;
        }
        pickPile(n, u);
        Server[] server = u.getMutualServers().toArray(new Server[0]);
        messageSeer(server[0]);
    }

    private static void pickPile(int n, User u) {
        String pos = getPos(n);

        int[] x = getPile();

        String role = getRole(x[n-1]);
        String message = String.format("The role of the %s card is %s.", pos, role);
        u.sendMessage(message);
    }

    private static String getPos(int n) {
        String pos;
        if (n == 1) {
            pos = "LEFT";
        } else if (n == 2) {
            pos = "MIDDLE";
        } else {
            pos = "RIGHT";
        }
        return pos;
    }

    private static int[] getPile() {
        Scanner pileFile = scanFile("pile.txt");
        int[] p = new int[3];
        for (int i=0; i<3; i++) {
            p[i] = pileFile.nextInt();
        }
        pileFile.close();
        return p;
    }

    private static void messageMinion(MessageCreateEvent event, String... ww) {
        String player = getPlayerFromRole(2);
        if (player == null) {
            return;
        }

        String message;

        if (ww.length ==  0) {
            message = "There are no werewolves.";
        } else if (ww.length == 1) {
            message = String.format("The WEREWOLF is %s.", ww[0]);
        } else {
            message = String.format("The WEREWOLVES are %s and %s.", ww[0], ww[1]);
        }

        User u = getUserFromName(player, event.getServer().get());
        u.sendMessage(message);
    }

    private static String getPlayerFromRole(int n) {
        Scanner playerFile = scanFile("game.txt");
        String player;
        int x, nPlayers = playerFile.nextInt();
        playerFile.nextLine();

        for (int i=0; i<nPlayers; i++) {
            player = playerFile.nextLine();
            x = playerFile.nextInt();
            if (x == n) {
                playerFile.close();
                return player;
            }
            playerFile.nextLine();
        }

        playerFile.close();
        return null;
    }

    private static void messageSeer(Server s) {
        String player = getPlayerFromRole(10);
        if (player == null) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            messageRobber(s);
            return;
        }
        User u = getUserFromName(player, s);
        u.sendMessage("You may now view two cards (of three) from the centre pile.");
        u.sendMessage("1: left          2: middle          3: right");
        u.sendMessage("Command: !ww act # #");

        setToAct(player, 10);
    }

    private static void applySeer(int n1, int n2, User u) {
        if (n1<1||n2<1||n1>3||n2>3||n1==n2) {
            u.sendMessage("Invalid command or person.");
            return;
        }
        pickPile(n1, u);
        pickPile(n2, u);

        Server[] server = u.getMutualServers().toArray(new Server[0]);
        messageRobber(server[0]);
    }

    private static void messageRobber(Server s) {
        String player = getPlayerFromRole(9);
        if (player == null) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            messageTroublemaker(s);
            return;
        }

        User u = getUserFromName(player, s);
        u.sendMessage("You may now rob another player and look at your new role.");

        Scanner playerFile = scanFile("game.txt"); //to "game2.txt" if testing
        listOtherPlayers(playerFile, player, u);
        playerFile.close();
        u.sendMessage("Command: !ww act #");

        setToAct(player, 9);
    }

    private static void listOtherPlayers(Scanner playerFile, String p, User u) {
        String name;
        int nPlayers = playerFile.nextInt();
        playerFile.nextLine();

        for (int i = 1; i < nPlayers; i++) {
            name = playerFile.nextLine();
            playerFile.nextLine();
            if (name.equals(p)) {
                i--;
                continue;
            }
            name = getDisplayName(name);
            u.sendMessage(String.format("%d. %s", i, name));
        }
    }

    private static void applyRobber(int n, User u) {
        String fileName = "game.txt";
        Scanner playerFileIn = scanFile(fileName); //to "game2.txt" for testing
        int nP = playerFileIn.nextInt();
        if (n<1||n>=nP) {
            u.sendMessage("Invalid command or person.");
            return;
        }

        String[] players = new String[nP];
        int[][] roles = new int[nP][2];

        playerFileIn.nextLine();
        for (int i=0; i<nP; i++) {
            players[i] = playerFileIn.nextLine();
            roles[i][0] = playerFileIn.nextInt();
            roles[i][1] = playerFileIn.nextInt();
            playerFileIn.nextLine();
        }
        playerFileIn.close();

        Server[] server = u.getMutualServers().toArray(new Server[0]);
        String robber = u.getDiscriminatedName();
        int nR = getPlayerIndex(players, robber);
        if (n <= nR) {
            n--;
        }
        int tmp = roles[n][1];
        roles[n][1] = roles[nR][1];
        roles[nR][1] = tmp;

        PrintWriter playerFileOut = writeFile(fileName);
        playerFileOut.println(nP);
        for (int i=0; i<nP; i++) {
            playerFileOut.println(players[i]);
            playerFileOut.println(String.format("%d %d", roles[i][0], roles[i][1]));
        }
        playerFileOut.close();

        String message = "You robbed " + getDisplayName(players[n]) + ".";
        u.sendMessage(message);

        message = getDisplayName(robber) + ", your new role is ";
        if (tmp == 9) {
            message += "still ";
        }
        message += getRole(tmp) + ".";
        u.sendMessage(message);

        messageTroublemaker(server[0]);
    }

    private static void writeGame(String[] P, int[][] R) {
        PrintWriter playerFileOut = writeFile("game.txt");
        int nP = P.length;
        playerFileOut.println(nP);
        for (int i=0; i<nP; i++) {
            playerFileOut.println(P[i]);
            playerFileOut.println(String.format("%d %d", R[i][0], R[i][1]));
        }
        playerFileOut.close();
    }

    private static int getPlayerIndex(String[] players, String p) {
        int n = 0;
        for (String s : players) {
            if (s.equals(p)) {
                return n;
            }
            n++;
        }
        return -1;
    }

    private static void messageTroublemaker(Server s) {
        String player = getPlayerFromRole(8);
        if (player == null) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            messageDrunk(s);
            return;
        }

        User u = getUserFromName(player, s);
        u.sendMessage("You may now switch the roles of two other players.");

        Scanner playerFile = scanFile("game.txt"); // to "game3.txt" for testing
        listOtherPlayers(playerFile, player, u);
        playerFile.close();
        u.sendMessage("Command: !ww act # #");

        setToAct(player, 8);
    }

    private static void applyTroubleMaker(int n1, int n2, User u) {
        String fileName = "game.txt";
        Scanner playerFileIn = scanFile(fileName); // to "game3.txt" if testing
        int nP = playerFileIn.nextInt();
        if (n1<1||n2<1||n1>=nP||n2>nP||n1==n2) {
            u.sendMessage("Invalid command or person.");
            return;
        }

        String[] players = new String[nP];
        int[][] roles = new int[nP][2];

        playerFileIn.nextLine();
        for (int i=0; i<nP; i++) {
            players[i] = playerFileIn.nextLine();
            roles[i][0] = playerFileIn.nextInt();
            roles[i][1] = playerFileIn.nextInt();
            playerFileIn.nextLine();
        }
        playerFileIn.close();

        Server[] server = u.getMutualServers().toArray(new Server[0]);
        String tm = u.getDiscriminatedName();
        int nTM = getPlayerIndex(players, tm);
        if (n1 <= nTM) {
            n1--;
        }
        if (n2 <= nTM) {
            n2--;
        }

        int tmp = roles[n1][1];
        roles[n1][1] = roles[n2][1];
        roles[n2][1] = tmp;

        PrintWriter playerFileOut = writeFile(fileName);
        playerFileOut.println(nP);
        for (int i=0; i<nP; i++) {
            playerFileOut.println(players[i]);
            playerFileOut.println(String.format("%d %d", roles[i][0], roles[i][1]));
        }
        playerFileOut.close();

        String message = "You switched the roles of " + getDisplayName(players[n1]) + " and " + getDisplayName(players[n2]) + ".";
        u.sendMessage(message);
        messageDrunk(server[0]);
    }

    private static void messageDrunk(Server s) {
        String player = getPlayerFromRole(7);
        if (player == null) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            messageInsomniac(s);
            return;
        }

        User u = getUserFromName(player, s);
        u.sendMessage("You do not remember your role. Exchange your role card for a card in the centre pile.");
        u.sendMessage("1: left          2: middle          3: right");
        u.sendMessage("Command: !ww act #");

        setToAct(player, 7);
    }

    private static void applyDrunk(int n, User u) {
        if (n<1||n>3) {
            u.sendMessage("Invalid command or person.");
            return;
        }

        int[] pile = getPile();

        Scanner playerFileIn = scanFile("game.txt");
        int nP = playerFileIn.nextInt();

        String[] players = new String[nP];
        int[][] roles = new int[nP][2];

        playerFileIn.nextLine();
        for (int i=0; i<nP; i++) {
            players[i] = playerFileIn.nextLine();
            roles[i][0] = playerFileIn.nextInt();
            roles[i][1] = playerFileIn.nextInt();
            playerFileIn.nextLine();
        }
        playerFileIn.close();

        Server[] server = u.getMutualServers().toArray(new Server[0]);
        String d = u.getDiscriminatedName();
        int nD = getPlayerIndex(players, d);

        int tmp = roles[nD][1];
        roles[nD][1] = pile[n-1];
        pile[n-1] = tmp;

        writeGame(players, roles);
        writePile(pile);

        String pos = getPos(n);
        String message = "You exchanged your card with the " + pos + " card.";
        u.sendMessage(message);

        messageInsomniac(server[0]);
    }

    private static void messageInsomniac(Server s) {
        String player = getPlayerFromRole(6);
        if (player == null) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendDay(s);
            return;
        }

        Scanner playerFileIn = scanFile("game.txt");
        int nP = playerFileIn.nextInt();

        String p, message = getDisplayName(player) + ", your new role is ";
        int r = 0;

        playerFileIn.nextLine();
        for (int i=0; i<nP; i++) {
            p = playerFileIn.nextLine();
            if (p.equals(player)) {
                playerFileIn.nextInt();
                r = playerFileIn.nextInt();
                break;
            }
            playerFileIn.nextLine();
        }
        playerFileIn.close();

        User u = getUserFromName(player, s);
        if (r == 6) {
            message += "still INSOMNIAC.";
        } else {
            message += getRole(r) + ".";
        }
        u.sendMessage(message);

        sendDay(s);
    }

    private static void revealRoles(MessageCreateEvent event) {
        Scanner playerFileIn = scanFile("game.txt");
        int nP = playerFileIn.nextInt();

        String message, player;
        int role;

        playerFileIn.nextLine();
        for (int i=0; i<nP; i++) {
            player = playerFileIn.nextLine();
            playerFileIn.nextInt();
            role = playerFileIn.nextInt();
            playerFileIn.nextLine();
            message = getDisplayName(player) + ":     " + getRole(role);
            event.getChannel().sendMessage(message);
        }
        playerFileIn.close();
    }

    private static void sendDay(Server s) {
        String message = "@here DAY phase has begun.";
        s.getTextChannels().get(0).sendMessage(message);
        setPhase(3);
    }

    private static void sendRulesH(MessageCreateEvent event) {
        String sep = "- - - - - - - - - -";
        String message = "In Werewolf, each player takes on the role of a Villager, a Werewolf, or a special character. ";
        message += "It’s your job to figure out who the Werewolves are and to kill at least one of them in order to win...  ";
        message += "unless you’ve become a Werewolf yourself!";
        message += "\n" + sep + "\n";
        message += "Each player initially knows their role. ";
        message += "Three additional role cards are placed in the centre pile.";
        message += "\n" + sep + "\n";
        message += "NIGHT: several roles are called on at night to perform a night action\n";
        message += "DAY: players discuss amongst themselves who they believe the Werewolves are";
        message += "\n" + sep + "\n";
        message += "After the night phase, your role is the card that is currently in front of you, which may be different than your original role. ";
        message += "No one may look at any cards after the night phase.";
        message += "\n" + sep + "\n";
        message += "Players then vote. The player(s) with the most votes die.\n";
        message += "The village team wins if at least one Werewolf dies or if no one is a Werewolf and no one dies.";
        message += "\n" + sep + "\n";
        message += "Type '!ww roles' for role descriptions.";

        event.getChannel().sendMessage(message);
    }

    private static void sendRolesH(MessageCreateEvent event) {
        String sep = "- - - - - - - - - -";
        String message = "Werewolf team\n";
        message += "WEREWOLF: knows the other werewolves, may view one centre card if no other werewolf\n";
        message += "MINION: knows who the werewolves are";
        message += "\n" + sep + "\n";
        message += "Villager team\n";
        message += "SEER: looks at two of the centre cards\n";
        message += "ROBBER: robs a card from another player and looks at their new role\n";
        message += "TROUBLEMAKER: switches the role cards of two other players\n";
        message += "DRUNK: exchanges their role card for a card in the centre without looking at it\n";
        message += "INSOMNIAC: wakes up and look at their role card\n";
        message += "VILLAGER: has no special ability";
        message += "\n" + sep + "\n";
        message += "Type '!ww rules' for the rules of the game.";

        event.getChannel().sendMessage(message);
    }

    private static void sendHelp(MessageCreateEvent event, String s) {
        String message;
        switch (s) {
            case "":
                message = "Type '!ww help command' for further help.\n";
                message += "Commands: help rules roles join play act reveal reset";
                break;
            case "help":
                message = "Syntax: '!ww help'\n";
                message += "Sends the list of commands.";
                break;
            case "rules":
                message = "Syntax: '!ww rules'\nSends the rules of the werewolf game.";
                break;
            case "roles":
                message = "Syntax: '!ww roles'\nSends a description of each role.";
                break;
            case "join":
                message = "Syntax: '!ww join'\nJoins a new game if there is no on-going game.";
                break;
            case "play":
                message = "Syntax: '!ww play'\nInitiates a werewolf game.";
                break;
            case "act":
                message = "Syntax: '!ww act # [#]'\nPerforms a night action depending on role.";
                break;
            case "reveal":
                message = "Syntax: '!ww reveal'\nEnds the werewolf game and reveals everyone's role.";
                break;
            case "reset":
                message = "Syntax: '!ww reset'\nResets the werewolf game.";
                break;
            default:
                message = "Invalid command.\nPlease type '!ww help' for the list of commands.";
                break;
        }
        event.getChannel().sendMessage(message);
    }

    private static void sendIntro(MessageCreateEvent event) {
        String message = "Hi, I am the werewolf bot. ";
        message += "Please type '!ww help' for the list of commands and '!ww rules' for the rules of the game.";
        event.getChannel().sendMessage(message);
    }
}
