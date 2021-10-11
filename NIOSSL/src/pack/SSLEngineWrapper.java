package pack;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;



import javax.net.ssl.*;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Arrays;

public class SSLEngineWrapper {
        private KeyStore keyStore;
        private KeyStore keyStoreTrust;
        private String password;
        private SSLEngine serverEngine;
        private boolean handshake = false;
        private boolean handshakeFailed = false;
        private byte[] handshakeResponce = new byte[1];
        private SSLContext sslc;
        private String keyStoreFilePath;
        private String keyStoreTrustFilePath;

        public SSLEngineWrapper(SocketChannel sock, boolean server,String keyStoreFilePath, String keyStoreTrustFilePath, String password) {
            this.keyStoreFilePath = keyStoreFilePath;
            this.keyStoreTrustFilePath = keyStoreTrustFilePath;
            this.password = password;
            if (this.keyStoreFilePath == null) {
                this.keyStoreFilePath = "C:\\Users\\squin\\IdeaProjects\\NIOSSL\\src\\pack\\myKeyStore.jks";
                this.keyStoreTrustFilePath = "C:\\Users\\squin\\IdeaProjects\\NIOSSL\\src\\pack\\myTrustStore2.jts";
                this.password = "abc123";
            }
            try {
                keyStore = KeyStore.getInstance("JKS");
                keyStore.load(new FileInputStream(this.keyStoreFilePath), this.password.toCharArray());
                keyStoreTrust = KeyStore.getInstance("JKS");
                keyStoreTrust.load(new FileInputStream(this.keyStoreTrustFilePath), this.password.toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
                keyManagerFactory.init(keyStore, this.password.toCharArray());
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
                trustManagerFactory.init(keyStoreTrust);
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagerFactory.getKeyManagers(),trustManagerFactory.getTrustManagers(),null);
                sslc = sslContext;
                serverEngine = sslContext.createSSLEngine();
                serverEngine.setUseClientMode(!server);
                if (server) {
                    serverEngine.setNeedClientAuth(true);
                }


            } catch (Exception e) {
                handshakeFailed = true;
                e.printStackTrace();

            }
        }

        public boolean checkHandshake() {
            return this.handshake;
        }

        public boolean checkHandshakeFailed() {
            return handshakeFailed;
        }

        public byte[] getHandshakingStatus() {
            if (Arrays.equals(handshakeResponce,"HANDSHAKE_FAILURE_RECEIVED____".getBytes())) {
                handshakeFailed = true;
                handshake = true;
                return "HANDSHAKE_FAILURE_RECEIVED____".getBytes();
            } else if (handshakeFailed) {
                return "FATAL_ERROR_HANDSHAKE_FAILED__".getBytes();
            } else if (Arrays.equals(handshakeResponce,"HANDSHAKE_COMPLETE____________".getBytes())){
                handshake = true;
                return "HANDSHAKE_COMPLETE_RESPONSE___".getBytes();
            } else {
                return "HANDSHAKE_COMPLETE____________".getBytes();
            }

        }

        public void checkHandshakeStatus(byte[] bytes) {
            handshakeResponce = bytes;
            if(Arrays.equals(bytes,"FATAL_ERROR_HANDSHAKE_FAILED__".getBytes())) {
                handshakeFailed = true;
            } else if (Arrays.equals(handshakeResponce,"HANDSHAKE_FAILURE_RECEIVED____".getBytes())) {
                handshakeFailed = true;
            } else if (Arrays.equals(handshakeResponce,"HANDSHAKE_COMPLETE_RESPONSE___".getBytes())) {
                handshake = true;
            }
        }

        private void runDelegatedTasks(SSLEngineResult result, SSLEngine engine) throws Exception {

            if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                //System.out.println("running delegated task");
                Runnable runnable;
                while ((runnable = engine.getDelegatedTask()) != null) {
                    runnable.run();
                }
                SSLEngineResult.HandshakeStatus hsStatus = engine.getHandshakeStatus();
                if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                    handshakeFailed = true;
                }
            }
        }

        public void sslWrap(ByteBuffer in, ByteBuffer out) {
            try {
                SSLEngineResult res = serverEngine.wrap(in, out);
                //debug(res);
                this.runDelegatedTasks(res, serverEngine);

            } catch (Exception e) {
                handshakeFailed = true;
                e.printStackTrace();
            }
        }

        public void sslUnwrap(ByteBuffer in, ByteBuffer out) {
            try {
                SSLEngineResult res = serverEngine.unwrap(in, out);
                //debug(res);
                runDelegatedTasks(res, serverEngine);

            } catch (Exception e) {
                e.printStackTrace();
                handshakeFailed = true;
            }
        }

        public static void debug(SSLEngineResult res) {
            System.out.println("Res status: " + res.getStatus());
            System.out.println("Handshake status: " + res.getHandshakeStatus());
        }

        public int getConnectionBuffer() {
            return serverEngine.getSession().getPacketBufferSize();
        }

        public int getApplicationBuffer() {
            return serverEngine.getSession().getApplicationBufferSize();
        }
    }