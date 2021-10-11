package pack;

import java.util.Scanner;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Worker implements Runnable{
    private NIOSSL server;
    private UserIOManager userIOManager;


    public Worker(NIOSSL server,UserIOManager userIOManager) {
        this.server = server;
        this.userIOManager = userIOManager;
    }

    @Override
    public void run() {
        IncomingData data = null;
        String input = null;
        try {
            Thread.sleep(10000);
            userIOManager.print("waiting");
        } catch (Exception e) {

        }
        while(server.getRunning()) {
            while (true) {
                try {
                    data = server.getIncomingData(false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (data != null) {
                    userIOManager.print("Incoming Message: " + data.getData());
                } else {
                    break;
                }
            }
            userIOManager.print("waiting for input");
            input = userIOManager.takeInput();
            if (!input.equalsIgnoreCase("read")) {
                server.send(server.getSock(0),input.getBytes());
            }
        }
    }

}
