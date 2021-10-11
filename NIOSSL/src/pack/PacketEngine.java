package pack;

import java.nio.charset.StandardCharsets;

public class PacketEngine {
    private int buffSize = 1024;
    private int digits = 4;
    private int packetSize = buffSize - ((2*digits) + 3);
    private byte[] data = null;
    private boolean currentlyBuilding = false;
    private int lastPacketNum;
    private boolean dataReady = false;

    public byte[][] packetDivider(byte[] data) {
        int amountOfPackets = (data.length / packetSize) + 1;
        byte[][] packets = new byte[amountOfPackets][buffSize];

        for (int j = 1; j <= amountOfPackets; j++) {
            byte[] data2 = new byte[buffSize];

            if (data.length > packetSize * j) {
                System.arraycopy(data, (packetSize * (j-1)), data2, 0, packetSize);
            } else {
                System.arraycopy(data, (packetSize * (j-1)), data2, 0, data.length - (packetSize * (j-1)));
            }
            byte[] arrayEndingTag = String.format("<%0" + digits + "d/%0" + digits + "d>", j, amountOfPackets).getBytes();
            System.arraycopy(arrayEndingTag, 0, data2, packetSize, ((2*digits) + 3));
            packets[j - 1] = data2;
        }
        return packets;
    }

    public boolean isDataReady(byte[] data) {
        currentlyBuilding = true;
        byte[] arrayEndingTag = new byte[((2*digits) + 3)];
        System.arraycopy(data,data.length - ((2*digits) + 3),arrayEndingTag,0,((2*digits) + 3));
        String endingTag = new String(arrayEndingTag, StandardCharsets.UTF_8);
        int packetNum = Integer.parseInt(endingTag.substring(1,1+digits));
        int totalPackets = Integer.parseInt(endingTag.substring(2 + digits,2 * digits + 2));

        if(this.data == null) {
            this.data = new byte[totalPackets * packetSize];
        }
        System.arraycopy(data, 0,this.data,(packetNum - 1) * packetSize,packetSize);
        if (packetNum == totalPackets) {
            dataReady = true;
            return true;
        } else {
            return false;
        }
    }

    public void buildData() {
        data = null;
        dataReady = false;
        currentlyBuilding = true;
    }

    public byte[] outputData() {
        if(dataReady == true) {
            currentlyBuilding = false;
            return data;
        } else {
            return null;
        }
    }


}
