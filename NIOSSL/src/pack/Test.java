package pack;

import java.util.Arrays;

public class Test {
    public static void main(String[] args) {
        byte[] data = new byte[2222];
        for(int i = 0; i<data.length; i++) {
            data[i] = 0b00000010;
        }
        System.out.println(Arrays.toString(data));

        PacketEngine engine = new PacketEngine();

        byte[][] datas = engine.packetDivider(data);

        for(int i = 0; i < datas.length; i++) {
            System.out.println(Arrays.toString(datas[i]));
        }
        boolean ready = false;
        engine.buildData();
        int i = 0;
        do {
            ready = engine.isDataReady(datas[i++]);

        } while (!ready);
        System.out.println(Arrays.toString(engine.outputData()));

    }
}
