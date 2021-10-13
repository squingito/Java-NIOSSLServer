package pack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class UserIOManager {
    private Scanner scan = new Scanner(System.in);

    public synchronized void print(String text) {
        System.out.println(text);
    }

    public synchronized String takeInput() {
        return scan.nextLine();
    }

    public boolean hasNext() {
        try {
            Thread.sleep(10);
            return System.in.available() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
