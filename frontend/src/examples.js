export const examples = [
  {
    name: 'Nested function calls and loop',
    className: 'Main',
    code: `public class Main {
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
}`,
  },
  {
    name: 'Heap & static fields',
    className: 'MemoryDemo',
    code: `import java.util.ArrayList;
import java.util.List;

public class MemoryDemo {
    static int runs = 1;
    static Node firstNode;

    public static void main(String[] args) {
        int[] values = {4, 9, 2};
        Node first = new Node("first");
        Node second = new Node("second");
        first.next = second;
        second.next = first;
        firstNode = first;

        List<String> names = new ArrayList<>();
        names.add(first.label);
        names.add(second.label);
        System.out.println("Largest value: " + largest(values));
    }

    static int largest(int[] values) {
        int result = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > result) result = values[i];
        }
        return result;
    }

    static class Node {
        String label;
        Node next;
        Node(String label) { this.label = label; }
    }
}`,
  },
  {
    name: 'Factorial recursion',
    className: 'Factorial',
    code: `public class Factorial {
    public static void main(String[] args) {
        int n = 3;
        int result = fact(n);
        System.out.println("fact(" + n + ") = " + result);
    }

    static int fact(int n) {
        if (n <= 1) return 1;
        return n * fact(n - 1);
    }
}`,
  },
  {
    name: 'Largest in an array',
    className: 'Largest',
    code: `public class Largest {
    public static void main(String[] args) {
        int[] numbers = {12, 45, 7, 23};
        int largest = numbers[0];
        for (int i = 1; i < numbers.length; i++) {
            if (numbers[i] > largest) largest = numbers[i];
        }
        System.out.println("Largest element: " + largest);
    }
}`,
  },
];
