package theevgeny.zendb;

public class Main {
    public static void main(String[] args) {
        ZenDB zenDB = new ZenDB("Test", "Test/Test/");
        String a = zenDB.getRoot().getString("testKey1");
        int b = zenDB.getRoot().getInt("testKey2");
    }
}
