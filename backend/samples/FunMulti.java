public class Main {
    static int fun_add(int a, int b) {
        return a + b;
    }

    static int fun_multi(int x, int y) {
        int times = Math.min(x, y);
        int number = Math.max(x, y);
        int result = 0;
        for (int i = 0; i < times; i++) {
            result = fun_add(result, number);
        }
        return result;
    }

    public static void main(String[] args) {
        int a = 4, b = 2;
        System.out.println(a + " x " + b + " = " + fun_multi(a, b));
    }
}
