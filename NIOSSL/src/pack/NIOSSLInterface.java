package pack;

import java.util.Queue;

public interface NIOSSLInterface {
    public void addToIncomingDataQueue(IncomingData data);
    public IncomingData getIncomingData(boolean blocking);

}
