package pack;

import java.util.Scanner;

public class UserIOManager {
    private Scanner scan = new Scanner(System.in);

    public synchronized void print(String text) {
        System.out.println(text);
    }

    public synchronized String takeInput() {
        return scan.nextLine();
    }
}
