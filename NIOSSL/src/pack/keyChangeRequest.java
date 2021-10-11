package pack;


import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class keyChangeRequest {
    private SocketChannel sock;
    private int changeTo;

    public keyChangeRequest(SocketChannel sock,int key) {
        this.sock = sock;
        this.changeTo = key;
    }

    public SocketChannel getSock() {
        return sock;
    }

    public int getKeyChange() {
        return changeTo;
    }
}

