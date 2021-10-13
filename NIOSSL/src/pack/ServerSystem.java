package pack;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ServerSystem implements NIOSSLInterface{

    private UserIOManager userIOManager = new UserIOManager();
    private BlockingQueue<IncomingData> queue = new ArrayBlockingQueue<IncomingData>(1000);
    private NIOSSL<ServerSystem> server;

    public static void main(String[] args) {
        ServerSystem serverSystem = new ServerSystem();
    }

    public ServerSystem() {
        server = new NIOSSL(null,0,true, this, userIOManager);
        Worker worker = new Worker(this, server, this.userIOManager);
        Thread workerThread = new Thread(worker);
        workerThread.setDaemon(true);
        workerThread.start();
        server.run();
    }

    @Override
    public void addToIncomingDataQueue(IncomingData data) {
        queue.add(data);
    }

    @Override
    public IncomingData getIncomingData(boolean blocking) {
        if (blocking) {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return queue.poll();
        }
    }

    public IncomingData getIncomingData() {
        return this.getIncomingData(true);
    }

    public UserIOManager getUserIOManager() {
        return userIOManager;
    }
}
