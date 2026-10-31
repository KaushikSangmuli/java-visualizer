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
    name: 'Nested loops with a function call',
    className: 'NestedLoops',
    code: `public class NestedLoops {
    static int multiply(int a, int b) {
        return a * b;
    }

    public static void main(String[] args) {
        int total = 0;
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 2; col++) {
                int cell = multiply(row, col);
                total = total + cell;
            }
        }
        int countdown = 3;
        while (countdown > 0) {
            countdown--;
        }
        System.out.println("total = " + total);
    }
}`,
  },
  {
    name: 'Collections: list, stack, queue, map, set',
    className: 'CollectionsDemo',
    code: `import java.util.*;

public class CollectionsDemo {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        list.add(5);
        list.add(8);
        list.add(2);

        LinkedList<String> chain = new LinkedList<>();
        chain.add("ann");
        chain.add("bob");
        chain.addFirst("zed");

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1);
        stack.push(2);
        stack.pop();
        stack.push(3);

        Queue<Integer> queue = new LinkedList<>();
        queue.offer(10);
        queue.offer(20);
        queue.poll();
        queue.offer(30);

        PriorityQueue<Integer> pq = new PriorityQueue<>();
        pq.add(7);
        pq.add(3);
        pq.add(9);

        Set<String> seen = new HashSet<>();
        seen.add("x");
        seen.add("y");

        Map<String, Integer> freq = new HashMap<>();
        for (String word : new String[] {"a", "b", "a"}) {
            freq.put(word, freq.getOrDefault(word, 0) + 1);
        }
        System.out.println(list + " " + chain + " " + stack + " " + queue + " " + pq.peek() + " " + seen + " " + freq);
    }
}`,
  },
  {
    name: 'Binary search tree + TreeMap / TreeSet',
    className: 'BinaryTree',
    code: `import java.util.*;

public class BinaryTree {
    static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
    }

    static TreeNode insert(TreeNode node, int val) {
        if (node == null) return new TreeNode(val);
        if (val < node.val) node.left = insert(node.left, val);
        else node.right = insert(node.right, val);
        return node;
    }

    static void inorder(TreeNode node, List<Integer> out) {
        if (node == null) return;
        inorder(node.left, out);
        out.add(node.val);
        inorder(node.right, out);
    }

    public static void main(String[] args) {
        TreeNode root = null;
        for (int value : new int[] {50, 30, 70, 20, 40, 60, 80}) {
            root = insert(root, value);
        }
        List<Integer> sorted = new ArrayList<>();
        inorder(root, sorted);
        TreeMap<String, Integer> ages = new TreeMap<>();
        ages.put("mia", 31); ages.put("al", 25); ages.put("zoe", 19); ages.put("bo", 40);
        TreeSet<Integer> ids = new TreeSet<>(List.of(5, 1, 9, 3, 7));
        System.out.println(sorted + " " + ages + " " + ids);
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
