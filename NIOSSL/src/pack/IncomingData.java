package pack;

import java.nio.channels.SocketChannel;

public class IncomingData {
    private SocketChannel sock;
    private String data;

    public IncomingData(SocketChannel sock, String data) {
        this.sock = sock;
        this.data = data;
    }

    public SocketChannel getSock() {
        return sock;
    }

    public String getData() {
        return data;
    }
}
