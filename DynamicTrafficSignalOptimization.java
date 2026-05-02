import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

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

    private static List<Lane> createIntersection(Random random) {
        List<Lane> lanes = new ArrayList<>();
        lanes.add(new Lane("North", 3.8, 8.0, random));
        lanes.add(new Lane("East", 2.2, 5.5, random));
        lanes.add(new Lane("South", 3.0, 6.8, random));
        lanes.add(new Lane("West", 1.8, 4.5, random));
        return lanes;
    }

    private static void simulateSensorReadings(List<Lane> lanes, int cycle, Random random) {
        System.out.println("Live sensor update before decision:");

        for (Lane lane : lanes) {
            boolean peakTraffic = isPeakCycle(cycle);
            boolean burst = random.nextDouble() < BURST_PROBABILITY;
            double mean = peakTraffic ? lane.peakArrivalMean : lane.normalArrivalMean;

            if (burst) {
                mean *= 1.9;
            }

            TrafficUpdate update = readLaneSensor(lane, mean, random);
            update.burstDetected = burst;
            applyIncomingTraffic(lane, update);

            System.out.printf("- %-5s: +%2d vehicles%s%s%n",
                    lane.name,
                    update.arrivals,
                    update.burstDetected ? " | sudden traffic burst" : "",
                    update.emergencyDetected ? " | emergency detected" : "");
        }
    }

    private static boolean isPeakCycle(int cycle) {
        int position = cycle % 12;
        return position >= 4 && position <= 8;
    }

    private static TrafficUpdate readLaneSensor(Lane lane, double meanArrivals, Random random) {
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

    private static void printLaneTable(List<Lane> lanes) {
        System.out.printf("%-10s%10s%12s%12s%12s%14s%n",
                "Lane", "Vehicles", "Wait(s)", "Skipped", "Emergency", "Priority");
        printSeparator(70);

        for (Lane lane : lanes) {
            System.out.printf("%-10s%10d%12d%12d%12s%14d%n",
                    lane.name,
                    lane.vehicles,
                    lane.waitingTime,
                    lane.skippedRounds,
                    lane.emergency ? "YES" : "NO",
                    calculatePriority(lane));
        }
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

                if (served > 0) {
                    lane.emergency = false;
                }
            } else {
                lane.waitingTime += greenDuration;
                lane.skippedRounds++;
            }
        }
    }

    private static void runSimulation(int cycles, Random random, String seedMessage) {
        List<Lane> lanes = createIntersection(random);

        System.out.println("Dynamic Traffic Signal Optimization");
        System.out.println("Greedy Algorithm + Max Priority Queue");
        System.out.println("Priority = vehicles * waiting_time + emergency_bonus + fairness_factor");
        System.out.println(seedMessage);

        for (int cycle = 1; cycle <= cycles; cycle++) {
            System.out.println("\n==================== Decision Cycle " + cycle + " ====================");
            simulateSensorReadings(lanes, cycle, random);
            System.out.println();
            printLaneTable(lanes);

            PriorityQueue<Candidate> maxHeap = buildPriorityQueue(lanes);

            if (maxHeap.isEmpty()) {
                System.out.println("No active traffic at the intersection.");
                continue;
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

        printFinalSummary(lanes);
    }

    private static void printFinalSummary(List<Lane> lanes) {
        int totalServed = 0;
        int totalArrivals = 0;
        int totalGreen = 0;
        long totalDelay = 0;

        System.out.println("\n==================== Final Summary ====================");
        System.out.printf("%-10s%12s%12s%12s%14s%16s%n",
                "Lane", "Arrivals", "Served", "Remaining", "Green(s)", "Delay Score");
        printSeparator(76);

        for (Lane lane : lanes) {
            totalServed += lane.vehiclesServed;
            totalArrivals += lane.totalArrivals;
            totalGreen += lane.totalGreenTime;
            totalDelay += lane.accumulatedVehicleDelay;

            System.out.printf("%-10s%12d%12d%12d%14d%16d%n",
                    lane.name,
                    lane.totalArrivals,
                    lane.vehiclesServed,
                    lane.vehicles,
                    lane.totalGreenTime,
                    lane.accumulatedVehicleDelay);
        }

        System.out.println("\nTotal vehicles detected by sensors: " + totalArrivals);
        System.out.println("Total vehicles served: " + totalServed);
        System.out.println("Total green time allocated: " + totalGreen + " seconds");
        System.out.println("Total vehicle-delay score: " + totalDelay);
        System.out.println("\nComplexity note:");
        System.out.println("- Priority is computed for n lanes in each cycle.");
        System.out.println("- Building the max priority queue is O(n).");
        System.out.println("- Extracting the highest-priority lane is O(log n).");
        System.out.println("- Space complexity is O(n).");
    }

    private static void printSeparator(int length) {
        for (int i = 0; i < length; i++) {
            System.out.print("-");
        }
        System.out.println();
    }

    private static int parseCycles(String[] args) {
        if (args.length == 0) {
            return 10;
        }

        try {
            int cycles = Integer.parseInt(args[0]);
            if (cycles > 0) {
                return cycles;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default.
        }

        System.out.println("Invalid cycle count. Using default 10 cycles.");
        return 10;
    }

    private static Random createRandom(String[] args) {
        if (args.length >= 2) {
            try {
                long seed = Long.parseLong(args[1]);
                return new Random(seed);
            } catch (NumberFormatException ignored) {
                System.out.println("Invalid seed. Using system time instead.");
            }
        }

        return new Random();
    }

    private static String seedMessage(String[] args) {
        if (args.length >= 2) {
            try {
                Long.parseLong(args[1]);
                return "Random seed: " + args[1] + " (repeatable run)";
            } catch (NumberFormatException ignored) {
                return "Random seed: system time (non-repeatable run)";
            }
        }

        return "Random seed: system time (non-repeatable run)";
    }

    public static void main(String[] args) {
        int cycles = parseCycles(args);
        Random random = createRandom(args);
        runSimulation(cycles, random, seedMessage(args));
    }
}
