package pack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class SocketChannelData {
    private Queue<byte[]> outGoingMessageRequests;
    private SSLEngineWrapper sslWrapper;

    public SocketChannelData(SSLEngineWrapper sslWrapper) {
        outGoingMessageRequests = new LinkedList<>();
        this.sslWrapper = sslWrapper;
    }

    public Queue<byte[]> getOutGoingMessageRequests() {
        return outGoingMessageRequests;
    }

    public void addOutGoingMessageRequests(byte[] bytes) {
        this.outGoingMessageRequests.add(bytes);
    }

    public SSLEngineWrapper getSSLWrapper() {
        return sslWrapper;
    }

    public void setSSLWrapper(SSLEngineWrapper sslWrapper) {
        this.sslWrapper = sslWrapper;
    }

}
