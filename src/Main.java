// ============================================================
// Main.java
//
// Entry point. Delegates entirely to Simulation — contains no
// business logic of its own.
// ============================================================
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Intelligent Dynamic Traffic Signal Optimization");
        System.out.println("Using Greedy Algorithms & Priority Queues");
        System.out.println("Authors: Riyansha Joshi · Shashi Anand\n");

        System.out.println("Select mode:");
        System.out.println("  1 → Automated Demo");
        System.out.println("  2 → Interactive Simulation");
        System.out.print("Choice [1/2]: ");

        int choice = 1;
        try { choice = Integer.parseInt(scanner.nextLine().trim()); }
        catch (NumberFormatException e) { choice = 1; }

        if (choice == 2) Simulation.runInteractive(scanner);
        else             Simulation.runDemo();

        scanner.close();
    }
}