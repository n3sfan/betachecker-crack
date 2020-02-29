package idk;

public class Test {

    public static void main(String[] args) {
        test(0);
    }

    private static String test(int i) {
        try {
            System.out.println("test");
        } catch (Exception e) {
            System.out.println(e.toString());
        }

        System.out.println("yolo");
        return null;
    }

}
