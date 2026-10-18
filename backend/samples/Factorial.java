public class Factorial {
    public static void main(String[] args) {
        int n = 3;
        int result = fact(n);
        System.out.println("fact(" + n + ") = " + result);
    }
    static int fact(int n) {
        if (n <= 1) return 1;
        return n * fact(n - 1);
    }
}
