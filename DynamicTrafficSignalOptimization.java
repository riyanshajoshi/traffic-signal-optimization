import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;

public class DynamicTrafficSignalOptimization {
    private static final long EMERGENCY_BONUS = 10_000;
    private static final long FAIRNESS_WEIGHT = 75;
    private static final int MIN_GREEN_SECONDS = 10;
    private static final int MAX_GREEN_SECONDS = 45;
    private static final int SECONDS_PER_VEHICLE = 2;
    private static final double EMERGENCY_PROBABILITY = 0.08;
    private static final double BURST_PROBABILITY = 0.18;

    static class Lane {
        String name;
        int vehicles;
        int waitingTime;
        boolean emergency;
        int skippedRounds;
        double normalArrivalMean;
        double peakArrivalMean;
        int totalGreenTime;
        int vehiclesServed;
        int totalArrivals;
        long accumulatedVehicleDelay;

        Lane(String name, int vehicles, int waitingTime, boolean emergency) {
            this.name = name;
            this.vehicles = vehicles;
            this.waitingTime = waitingTime;
            this.emergency = emergency;
            this.totalArrivals = vehicles;
        }

        Lane(String name, double normalArrivalMean, double peakArrivalMean, Random random) {
            this.name = name;
            this.normalArrivalMean = normalArrivalMean;
            this.peakArrivalMean = peakArrivalMean;
            this.vehicles = 4 + random.nextInt(16);
            this.waitingTime = 10 + random.nextInt(80);
            this.totalArrivals = this.vehicles;
        }
    }

    static class Candidate {
        long priority;
        int laneIndex;
        int waitingTime;
        int vehicles;
        boolean emergency;

        Candidate(long priority, int laneIndex, int waitingTime, int vehicles, boolean emergency) {
            this.priority = priority;
            this.laneIndex = laneIndex;
            this.waitingTime = waitingTime;
            this.vehicles = vehicles;
            this.emergency = emergency;
        }
    }

    static class TrafficUpdate {
        int arrivals;
        boolean emergencyDetected;
        boolean burstDetected;

        TrafficUpdate(int arrivals, boolean emergencyDetected, boolean burstDetected) {
            this.arrivals = arrivals;
            this.emergencyDetected = emergencyDetected;
            this.burstDetected = burstDetected;
        }
    }

    private static long calculatePriority(Lane lane) {
        int effectiveWait = Math.max(lane.waitingTime, 1);
        long congestionScore = (long) lane.vehicles * effectiveWait;
        long emergencyScore = lane.emergency ? EMERGENCY_BONUS : 0;
        long fairnessScore = (long) lane.skippedRounds * FAIRNESS_WEIGHT;
        return congestionScore + emergencyScore + fairnessScore;
    }

    private static PriorityQueue<Candidate> buildPriorityQueue(List<Lane> lanes) {
        PriorityQueue<Candidate> maxHeap = new PriorityQueue<>((a, b) -> {
            if (a.priority != b.priority) {
                return Long.compare(b.priority, a.priority);
            }
            if (a.emergency != b.emergency) {
                return Boolean.compare(b.emergency, a.emergency);
            }
            if (a.waitingTime != b.waitingTime) {
                return Integer.compare(b.waitingTime, a.waitingTime);
            }
            if (a.vehicles != b.vehicles) {
                return Integer.compare(b.vehicles, a.vehicles);
            }
            return Integer.compare(a.laneIndex, b.laneIndex);
        });

        for (int i = 0; i < lanes.size(); i++) {
            Lane lane = lanes.get(i);
            if (lane.vehicles == 0 && !lane.emergency) {
                continue;
            }

            maxHeap.add(new Candidate(
                    calculatePriority(lane),
                    i,
                    lane.waitingTime,
                    lane.vehicles,
                    lane.emergency));
        }

        return maxHeap;
    }

    private static int computeGreenDuration(Lane lane) {
        int duration = MIN_GREEN_SECONDS + lane.vehicles * SECONDS_PER_VEHICLE;
        return Math.min(MAX_GREEN_SECONDS, Math.max(MIN_GREEN_SECONDS, duration));
    }

    private static List<Lane> readManualIntersection(Scanner scanner) {
        int laneCount = readInt(scanner, "Enter number of lanes: ", 2, 12);
        List<Lane> lanes = new ArrayList<>();

        for (int i = 0; i < laneCount; i++) {
            System.out.println("\nLane " + (i + 1) + " details");
            String name = readNonEmptyString(scanner, "Lane name: ");
            int vehicles = readInt(scanner, "Current vehicle count: ", 0, 1000);
            int waitingTime = readInt(scanner, "Current waiting time in seconds: ", 0, 10000);
            boolean emergency = readYesNo(scanner, "Emergency vehicle present? (y/n): ");
            lanes.add(new Lane(name, vehicles, waitingTime, emergency));
        }

        return lanes;
    }

    private static void readManualSensorUpdates(List<Lane> lanes, Scanner scanner) {
        System.out.println("Enter live sensor updates before this decision:");

        for (Lane lane : lanes) {
            int arrivals = readInt(scanner, "New vehicles in " + lane.name + ": ", 0, 1000);
            boolean emergency = readYesNo(scanner, "New emergency in " + lane.name + "? (y/n): ");
            applyIncomingTraffic(lane, new TrafficUpdate(arrivals, emergency, false));
        }
    }

    private static List<Lane> createRandomIntersection(Random random) {
        List<Lane> lanes = new ArrayList<>();
        lanes.add(new Lane("North", 3.8, 8.0, random));
        lanes.add(new Lane("East", 2.2, 5.5, random));
        lanes.add(new Lane("South", 3.0, 6.8, random));
        lanes.add(new Lane("West", 1.8, 4.5, random));
        return lanes;
    }

    private static void simulateRandomSensorReadings(List<Lane> lanes, int cycle, Random random) {
        System.out.println("Random sensor update before decision:");

        for (Lane lane : lanes) {
            boolean peakTraffic = isPeakCycle(cycle);
            boolean burst = random.nextDouble() < BURST_PROBABILITY;
            double mean = peakTraffic ? lane.peakArrivalMean : lane.normalArrivalMean;

            if (burst) {
                mean *= 1.9;
            }

            TrafficUpdate update = readRandomLaneSensor(lane, mean, random);
            update.burstDetected = burst;
            applyIncomingTraffic(lane, update);

            System.out.printf("- %-8s: +%2d vehicles%s%s%n",
                    lane.name,
                    update.arrivals,
                    update.burstDetected ? " | sudden burst" : "",
                    update.emergencyDetected ? " | emergency detected" : "");
        }
    }

    private static boolean isPeakCycle(int cycle) {
        int position = cycle % 12;
        return position >= 4 && position <= 8;
    }

    private static TrafficUpdate readRandomLaneSensor(Lane lane, double meanArrivals, Random random) {
        int arrivals = samplePoisson(meanArrivals, random);
        boolean emergencyDetected = !lane.emergency
                && arrivals > 0
                && random.nextDouble() < EMERGENCY_PROBABILITY;
        return new TrafficUpdate(arrivals, emergencyDetected, false);
    }

    private static void applyIncomingTraffic(Lane lane, TrafficUpdate update) {
        lane.vehicles += update.arrivals;
        lane.totalArrivals += update.arrivals;

        if (update.emergencyDetected) {
            lane.emergency = true;
        }
    }

    private static int samplePoisson(double mean, Random random) {
        double limit = Math.exp(-mean);
        int count = 0;
        double product = 1.0;

        do {
            count++;
            product *= random.nextDouble();
        } while (product > limit);

        return count - 1;
    }

    private static void runManualSimulation(int cycles, List<Lane> lanes, Scanner scanner) {
        printProjectHeader("Manual user input mode");

        for (int cycle = 1; cycle <= cycles; cycle++) {
            System.out.println("\n==================== Decision Cycle " + cycle + " ====================");
            readManualSensorUpdates(lanes, scanner);
            executeDecision(lanes);
        }

        printFinalSummary(lanes);
    }

    private static void runRandomSimulation(int cycles, Random random, String seedMessage) {
        List<Lane> lanes = createRandomIntersection(random);
        printProjectHeader("Random sensor simulation mode");
        System.out.println(seedMessage);

        for (int cycle = 1; cycle <= cycles; cycle++) {
            System.out.println("\n==================== Decision Cycle " + cycle + " ====================");
            simulateRandomSensorReadings(lanes, cycle, random);
            executeDecision(lanes);
        }

        printFinalSummary(lanes);
    }

    private static void executeDecision(List<Lane> lanes) {
        System.out.println();
        printLaneTable(lanes);

        PriorityQueue<Candidate> maxHeap = buildPriorityQueue(lanes);

        if (maxHeap.isEmpty()) {
            System.out.println("\nNo active traffic at the intersection.");
            return;
        }

        Candidate selected = maxHeap.poll();
        Lane chosen = lanes.get(selected.laneIndex);
        int greenDuration = computeGreenDuration(chosen);
        int vehiclesThatCanPass = greenDuration / SECONDS_PER_VEHICLE;
        int vehiclesServedNow = Math.min(chosen.vehicles, vehiclesThatCanPass);

        System.out.println("\nGreedy choice: " + chosen.name + " lane gets GREEN signal.");
        System.out.println("Reason: highest priority score = " + selected.priority + ".");
        System.out.println("Green duration: " + greenDuration + " seconds.");
        System.out.println("Vehicles served in this cycle: " + vehiclesServedNow + ".");

        updateTrafficAfterGreen(lanes, selected.laneIndex, greenDuration);
    }

    private static void updateTrafficAfterGreen(List<Lane> lanes, int selectedIndex, int greenDuration) {
        for (int i = 0; i < lanes.size(); i++) {
            Lane lane = lanes.get(i);
            lane.accumulatedVehicleDelay += (long) lane.vehicles * greenDuration;

            if (i == selectedIndex) {
                int canPass = greenDuration / SECONDS_PER_VEHICLE;
                int served = Math.min(lane.vehicles, canPass);

                lane.vehicles -= served;
                lane.vehiclesServed += served;
                lane.totalGreenTime += greenDuration;
                lane.waitingTime = lane.vehicles == 0 ? 0 : greenDuration / 2;
                lane.skippedRounds = 0;
                lane.emergency = false;
            } else {
                lane.waitingTime += greenDuration;
                lane.skippedRounds++;
            }
        }
    }

    private static void printLaneTable(List<Lane> lanes) {
        System.out.printf("%-12s%10s%12s%12s%12s%14s%n",
                "Lane", "Vehicles", "Wait(s)", "Skipped", "Emergency", "Priority");
        printSeparator(72);

        for (Lane lane : lanes) {
            System.out.printf("%-12s%10d%12d%12d%12s%14d%n",
                    lane.name,
                    lane.vehicles,
                    lane.waitingTime,
                    lane.skippedRounds,
                    lane.emergency ? "YES" : "NO",
                    calculatePriority(lane));
        }
    }

    private static void printFinalSummary(List<Lane> lanes) {
        int totalServed = 0;
        int totalArrivals = 0;
        int totalGreen = 0;
        long totalDelay = 0;

        System.out.println("\n==================== Final Summary ====================");
        System.out.printf("%-12s%12s%12s%12s%14s%16s%n",
                "Lane", "Arrivals", "Served", "Remaining", "Green(s)", "Delay Score");
        printSeparator(78);

        for (Lane lane : lanes) {
            totalServed += lane.vehiclesServed;
            totalArrivals += lane.totalArrivals;
            totalGreen += lane.totalGreenTime;
            totalDelay += lane.accumulatedVehicleDelay;

            System.out.printf("%-12s%12d%12d%12d%14d%16d%n",
                    lane.name,
                    lane.totalArrivals,
                    lane.vehiclesServed,
                    lane.vehicles,
                    lane.totalGreenTime,
                    lane.accumulatedVehicleDelay);
        }

        System.out.println("\nTotal vehicles detected: " + totalArrivals);
        System.out.println("Total vehicles served: " + totalServed);
        System.out.println("Total green time allocated: " + totalGreen + " seconds");
        System.out.println("Total vehicle-delay score: " + totalDelay);
        System.out.println("\nComplexity note:");
        System.out.println("- Priority is computed for n lanes in each cycle.");
        System.out.println("- Building the max priority queue is O(n).");
        System.out.println("- Extracting the highest-priority lane is O(log n).");
        System.out.println("- Space complexity is O(n).");
    }

    private static void printProjectHeader(String mode) {
        System.out.println("\nDynamic Traffic Signal Optimization");
        System.out.println("Greedy Algorithm + Max Priority Queue");
        System.out.println("Priority = vehicles * waiting_time + emergency_bonus + fairness_factor");
        System.out.println("Mode: " + mode);
    }

    private static void printSeparator(int length) {
        for (int i = 0; i < length; i++) {
            System.out.print("-");
        }
        System.out.println();
    }

    private static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Ask again.
            }

            System.out.println("Please enter an integer from " + min + " to " + max + ".");
        }
    }

    private static String readNonEmptyString(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (!input.isEmpty()) {
                return input;
            }

            System.out.println("Please enter a non-empty value.");
        }
    }

    private static boolean readYesNo(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("y") || input.equals("yes")) {
                return true;
            }
            if (input.equals("n") || input.equals("no")) {
                return false;
            }

            System.out.println("Please enter y or n.");
        }
    }

    private static Random readRandomGenerator(Scanner scanner) {
        System.out.print("Enter random seed for repeatable run, or press Enter for live random: ");
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) {
            return new Random();
        }

        try {
            return new Random(Long.parseLong(input));
        } catch (NumberFormatException ex) {
            System.out.println("Invalid seed. Using live random instead.");
            return new Random();
        }
    }

    private static String randomSeedMessage(Random random) {
        return "Random generator ready. Use manual mode when exact user-entered data is required.";
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("DAA Project: Intelligent Dynamic Traffic Signal Optimization");
        System.out.println("1. Manual user input");
        System.out.println("2. Random sensor simulation");

        int mode = readInt(scanner, "Choose mode: ", 1, 2);
        int cycles = readInt(scanner, "Enter number of decision cycles: ", 1, 100);

        if (mode == 1) {
            List<Lane> lanes = readManualIntersection(scanner);
            runManualSimulation(cycles, lanes, scanner);
        } else {
            Random random = readRandomGenerator(scanner);
            runRandomSimulation(cycles, random, randomSeedMessage(random));
        }

        scanner.close();
    }
}
