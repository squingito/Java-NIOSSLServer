package pack;

import javax.net.ssl.*;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import java.net.*;
import java.nio.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;

public class NIOSSL<type extends NIOSSLInterface> {
    private ServerSocketChannel serverSocket;
    private SocketChannel clientSocket;
    private HashMap<SocketChannel,SocketChannelData> socketProfiles = new HashMap<>();
    private Queue<keyChangeRequest> keyChangeRequests = new LinkedList<>();
    private type serverSystem;
    private UserIOManager userIOManager;

    private ArrayList<SocketChannel> socks = new ArrayList<>();

    private int appBuffSize;
    private int socketBuffSize;

    private ByteBuffer appIn;
    private ByteBuffer appOut;
    private ByteBuffer socketIn;
    private ByteBuffer socketOut;
    private InetAddress hostAddressObj;

    private PacketEngine packetEngine = new PacketEngine();

    private Selector select;
    private int port;
    private boolean running = true;
    private boolean serverMode = true;

    public NIOSSL(byte[] hostAddressInput, int hostPort, boolean server, type serverSystem, UserIOManager userIOManager) {
        this.userIOManager = userIOManager;
        serverMode = server;
        this.serverSystem = serverSystem;
        if (hostAddressInput == null) {
            byte[] hostAddress = {127,0,0,1};
            this.port = 9999;
        } else {
            byte[] hostAddress = hostAddressInput;
            this.port = hostPort;
        }
        try {
            byte[] hostAddress = {127,0,0,1};
            hostAddressObj = InetAddress.getByAddress(hostAddress);
        } catch(UnknownHostException e) {
            userIOManager.print("problem with host");
            hostAddressObj = null;
        }

    }

    public void setUpServer() throws Exception{
        select = SelectorProvider.provider().openSelector();
        serverSocket = ServerSocketChannel.open();
        serverSocket.configureBlocking(false);
        serverSocket.socket().bind(new InetSocketAddress(hostAddressObj,port));
        serverSocket.register(select,SelectionKey.OP_ACCEPT);
        running = true;
    }

    public void setUpClient() throws Exception{
        select = SelectorProvider.provider().openSelector();
        clientSocket = SocketChannel.open();
        clientSocket.configureBlocking(false);
        clientSocket.connect(new InetSocketAddress(hostAddressObj, 9999));
        clientSocket.register(select,SelectionKey.OP_CONNECT);
        running = true;
    }

    public void run() {
        try {
            if (serverMode) {
                this.setUpServer();
            } else {
                this.setUpClient();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(running) {
            userIOManager.print("Running");
        }
        while(running) {
            try {

                try {
                    //Thread.sleep(1000);
                } catch (Exception e){
                    e.printStackTrace();
                }
                synchronized (keyChangeRequests) {
                    while(!keyChangeRequests.isEmpty()) {
                        keyChangeRequest changeRequest = keyChangeRequests.remove();
                        SocketChannel currentSock = changeRequest.getSock();
                        currentSock.keyFor(select).interestOps(changeRequest.getKeyChange());
                    }

                }



                select.select();
                Iterator selectedKeys = select.selectedKeys().iterator();

                while(selectedKeys.hasNext()) {
                    SelectionKey currentKey = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if(!currentKey.isValid()) {
                        continue;
                    } else if (currentKey.isAcceptable()){
                        userIOManager.print("accpeting");
                        userIOManager.print("HANDSHAKING");
                        this.acceptSock();

                    } else if (currentKey.isConnectable()){
                        userIOManager.print("connecting");
                        userIOManager.print("HANDSHAKING");
                        this.connectToSock(currentKey);

                    } else if (currentKey.isReadable()) {
                        SocketChannel currentSock = (SocketChannel) currentKey.channel();
                        SSLEngineWrapper wrap = socketProfiles.get(currentSock).getSSLWrapper();
                        if(!wrap.checkHandshake()) {
                            this.readHandshake(currentSock);
                        } else {
                            this.readSock(currentSock);
                        }

                    } else if (currentKey.isWritable()) {
                        SocketChannel currentSock = (SocketChannel) currentKey.channel();
                        SSLEngineWrapper wrap = socketProfiles.get(currentSock).getSSLWrapper();
                        if(!wrap.checkHandshake()) {
                            this.writeHandshake(currentSock);
                        } else {
                            this.writeSock(currentSock);
                        }

                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public boolean acceptSock() {
        SocketChannel acceptedSock;
        try {
            acceptedSock = serverSocket.accept();
            socks.add(acceptedSock);
            acceptedSock.configureBlocking(false);
            acceptedSock.register(select, SelectionKey.OP_READ);
            SSLEngineWrapper sslEngineWrap = new SSLEngineWrapper(acceptedSock,true,null,null,null);
            synchronized (socketProfiles) {
                socketProfiles.put(acceptedSock,new SocketChannelData(sslEngineWrap));
            }
            this.checkBuffers(sslEngineWrap.getApplicationBuffer(),sslEngineWrap.getConnectionBuffer());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean connectToSock(SelectionKey key) {
        SocketChannel sock = (SocketChannel) key.channel();
        SSLEngineWrapper sslEngineWrap;
        try {
            if (sock.isConnectionPending()) {
                sock.finishConnect();
                key.interestOps(SelectionKey.OP_WRITE);
                sslEngineWrap = new SSLEngineWrapper(sock,false,null,null,null);
                synchronized (socketProfiles) {
                    socketProfiles.put(sock,new SocketChannelData(sslEngineWrap));
                }
                this.checkBuffers(sslEngineWrap.getApplicationBuffer(), sslEngineWrap.getConnectionBuffer());
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            terminateKey(key);
            return false;
        }
        return false;
    }

    public void send(SocketChannel sock, byte[] data) {
            synchronized (keyChangeRequests) {
                keyChangeRequests.add(new keyChangeRequest(sock, SelectionKey.OP_WRITE));
            }
            synchronized (sock) {
                socketProfiles.get(sock).addOutGoingMessageRequests(data);
            }
            select.wakeup();
    }

    private void readSock(SocketChannel sock) {
        SSLEngineWrapper sslEngineWrap = socketProfiles.get(sock).getSSLWrapper();
        boolean allDataRead = false;
        packetEngine.buildData();
        do {

            refreshBuffer(appIn);
            refreshBuffer(socketIn);

            int bytesIn;
            try {
                bytesIn = sock.read(socketIn);
            } catch (Exception e) {
                this.terminateKey(sock);
                return;
            }

            if (bytesIn == -1) {
                this.terminateKey(sock);
                userIOManager.print("disconnect");
            }

            //userIOManager.print(readBuffer(socketIn, true));
            socketIn.rewind();

            if (!sslEngineWrap.checkHandshakeFailed()) {
                sslEngineWrap.sslUnwrap(socketIn, appIn);
            } else {
                appIn.put(socketIn.array());
            }

            //userIOManager.print("app In --" + readBuffer(appIn, true));
            allDataRead = packetEngine.isDataReady(Arrays.copyOfRange(appIn.array(), 0, appIn.position()));
        } while (!allDataRead);
        byte[] finalData = packetEngine.outputData();
        String output = new String(finalData);
        if(output.indexOf((char) 0b00000000) != 0 && output.indexOf((char) 0b00000000) != 1) {
            output = output.substring(0,output.indexOf((char) 0b00000000));
        }

        serverSystem.addToIncomingDataQueue(new IncomingData(sock, output));
        sock.keyFor(select).interestOps(SelectionKey.OP_WRITE);
    }

    private void writeSock(SocketChannel sock) {
        synchronized (sock) {
            Queue<byte[]> dataList = socketProfiles.get(sock).getOutGoingMessageRequests();
            SSLEngineWrapper sslEngineWrap = socketProfiles.get(sock).getSSLWrapper();
            for (int i = 0; i < dataList.size(); i++) {

                byte[] data = dataList.remove();

                byte[][] packets = packetEngine.packetDivider(data);
                for (int j = 0; j < packets.length; j++) {
                    refreshBuffer(socketOut);
                    refreshBuffer(appOut);

                    appOut.put(packets[j]);
                    appOut.rewind();

                    readBuffer(appOut, true);

                    if (!sslEngineWrap.checkHandshakeFailed()) {
                        sslEngineWrap.sslWrap(appOut, socketOut);
                    } else {
                        socketOut.put(appOut);
                    }

                    socketOut.rewind();

                    readBuffer(socketOut, true);

                    try {
                        sock.write(socketOut);
                    } catch (Exception e) {
                        e.printStackTrace();
                        terminateKey(sock);
                    }
                }
            }
            if(dataList.isEmpty()) {
                sock.keyFor(select).interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void readHandshake(SocketChannel sock) {
        refreshBuffer(socketIn);
        refreshBuffer(appIn);

        SSLEngineWrapper sslEngineWrapper = socketProfiles.get(sock).getSSLWrapper();

        int bytesIn;

        try {
            bytesIn = sock.read(socketIn);
        } catch (Exception e) {
            e.printStackTrace();
            terminateKey(sock);
            return;
        }

        if (bytesIn == -1) {
            this.terminateKey(sock);
            userIOManager.print("Disconnect");
        }

        //userIOManager.print("Socket In --" + readBuffer(socketIn,true));
        socketIn.rewind();

        sslEngineWrapper.checkHandshakeStatus(Arrays.copyOfRange(socketIn.array(),0,30));

        if (!sslEngineWrapper.checkHandshakeFailed()) {
            sslEngineWrapper.sslUnwrap(socketIn, appIn);
        } else {
            appIn.put(socketIn.array());
        }

        //userIOManager.print("App In --" + readBuffer(appIn,true));
        appIn.rewind();

        sslEngineWrapper.checkHandshakeStatus(Arrays.copyOfRange(appIn.array(),0,30));

        if (!sslEngineWrapper.checkHandshake()) {
            sock.keyFor(select).interestOps(SelectionKey.OP_WRITE);
        } else {
            finalizeSSL(sock);
        }
    }

    private void writeHandshake(SocketChannel sock) {
        refreshBuffer(socketOut);
        refreshBuffer(appOut);

        SSLEngineWrapper sslEngineWrap = socketProfiles.get(sock).getSSLWrapper();

        appOut.put(sslEngineWrap.getHandshakingStatus());

        //userIOManager.print("App Out --" + readBuffer(appOut,true));
        appOut.rewind();

        if (!sslEngineWrap.checkHandshakeFailed()) {
            sslEngineWrap.sslWrap(appOut, socketOut);
        }

        //userIOManager.print("Socket Out --" + readBuffer(socketOut, true));
        socketOut.rewind();

        try {
            sock.write(socketOut);
        } catch (Exception e) {
            e.printStackTrace();
            terminateKey(sock);
        }

        if(sslEngineWrap.checkHandshake()) {
            finalizeSSL(sock);
        } else {
            sock.keyFor(select).interestOps(SelectionKey.OP_READ);
        }
    }

    private void finalizeSSL(SocketChannel sock) {
        sock.keyFor(select).interestOps(SelectionKey.OP_READ);
        SSLEngineWrapper sslEngineWrapper = socketProfiles.get(sock).getSSLWrapper();
        if(!sslEngineWrapper.checkHandshakeFailed()) {
            userIOManager.print("SSL COMPLETE");
        } else {
            userIOManager.print("SSL FAILED");
        }
    }

    private boolean terminateKey(SocketChannel sock) {
        try {
            SelectionKey key = sock.keyFor(select);
            socks.remove(sock);
            sock.close();
            key.cancel();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    private boolean terminateKey(SelectionKey key) {
        return this.terminateKey((SocketChannel) key.channel());
    }

    public boolean getRunning() {
        return running;
    }

    public static void refreshBuffer(ByteBuffer buffer) {
        buffer.rewind();
        buffer.flip();
        buffer.compact();
    }

    public static String readBuffer(ByteBuffer buffer, boolean ssl) {
        int pos = buffer.position();
        buffer.rewind();
        byte current;
        StringBuilder str = new StringBuilder();
        while(buffer.hasRemaining()) {
            current = buffer.get();
            if(!ssl && current != 0b00000000) {
                break;
            } else {
                str.append((char) current);
            }
        }
        buffer.position(pos);
        return str.toString();
    }

    private boolean checkBuffers(int appBuffSize, int socketBuffSize) {
        if(this.appBuffSize < appBuffSize || this.socketBuffSize < socketBuffSize) {
            socketIn = ByteBuffer.allocate(socketBuffSize);
            socketOut = ByteBuffer.allocate(socketBuffSize);
            appIn = ByteBuffer.allocate(appBuffSize + 50);
            appOut = ByteBuffer.allocate(1024);
            return true;
        }
        return false;
    }

    public SocketChannel getSock(int i) {
        if (socks.size() > i) {
            return socks.get(i);
        } else {
            return null;
        }
    }

}
