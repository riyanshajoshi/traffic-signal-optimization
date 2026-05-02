import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class DynamicTrafficSignalOptimization {
    private static final long EMERGENCY_BONUS = 10_000;
    private static final long FAIRNESS_WEIGHT = 60;
    private static final int MIN_GREEN_SECONDS = 10;
    private static final int MAX_GREEN_SECONDS = 35;
    private static final int SECONDS_PER_VEHICLE = 2;

    static class Lane {
        String name;
        int vehicles;
        int waitingTime;
        boolean emergency;
        int skippedRounds;
        int arrivalRate;
        int totalGreenTime;
        int vehiclesServed;
        long accumulatedVehicleDelay;

        Lane(String name, int vehicles, int waitingTime, boolean emergency, int arrivalRate) {
            this.name = name;
            this.vehicles = vehicles;
            this.waitingTime = waitingTime;
            this.emergency = emergency;
            this.arrivalRate = arrivalRate;
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

    private static void addScheduledEmergency(List<Lane> lanes, int cycle) {
        if (cycle == 4) {
            lanes.get(3).emergency = true;
            System.out.println("Event: Emergency vehicle detected in West lane.");
        } else if (cycle == 7) {
            lanes.get(2).emergency = true;
            System.out.println("Event: Emergency vehicle detected in South lane.");
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
                lane.waitingTime = 0;
                lane.skippedRounds = 0;
                lane.emergency = false;
            } else {
                lane.waitingTime += greenDuration;
                lane.skippedRounds++;
            }

            lane.vehicles += lane.arrivalRate;
        }
    }

    private static List<Lane> createDefaultIntersection() {
        List<Lane> lanes = new ArrayList<>();
        lanes.add(new Lane("North", 18, 40, false, 3));
        lanes.add(new Lane("East", 7, 70, false, 1));
        lanes.add(new Lane("South", 12, 25, false, 2));
        lanes.add(new Lane("West", 4, 90, false, 1));
        return lanes;
    }

    private static void runSimulation(int cycles) {
        List<Lane> lanes = createDefaultIntersection();

        System.out.println("Dynamic Traffic Signal Optimization");
        System.out.println("Greedy Algorithm + Max Priority Queue");
        System.out.println("Priority = vehicles * waiting_time + emergency_bonus + fairness_factor");

        for (int cycle = 1; cycle <= cycles; cycle++) {
            System.out.println("\n==================== Decision Cycle " + cycle + " ====================");
            addScheduledEmergency(lanes, cycle);
            printLaneTable(lanes);

            PriorityQueue<Candidate> maxHeap = buildPriorityQueue(lanes);

            if (maxHeap.isEmpty()) {
                System.out.println("No active traffic at the intersection.");
                break;
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
        int totalGreen = 0;
        long totalDelay = 0;

        System.out.println("\n==================== Final Summary ====================");
        System.out.printf("%-10s%14s%16s%16s%18s%n",
                "Lane", "Served", "Remaining", "Green(s)", "Delay Score");
        printSeparator(74);

        for (Lane lane : lanes) {
            totalServed += lane.vehiclesServed;
            totalGreen += lane.totalGreenTime;
            totalDelay += lane.accumulatedVehicleDelay;

            System.out.printf("%-10s%14d%16d%16d%18d%n",
                    lane.name,
                    lane.vehiclesServed,
                    lane.vehicles,
                    lane.totalGreenTime,
                    lane.accumulatedVehicleDelay);
        }

        System.out.println("\nTotal vehicles served: " + totalServed);
        System.out.println("Total green time allocated: " + totalGreen + " seconds");
        System.out.println("Total vehicle-delay score: " + totalDelay);
        System.out.println("\nComplexity note:");
        System.out.println("- Max lane retrieval uses a priority queue.");
        System.out.println("- Heap construction from n lanes is O(n) in this simulation.");
        System.out.println("- Extracting the selected lane is O(log n).");
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

    public static void main(String[] args) {
        int cycles = parseCycles(args);
        runSimulation(cycles);
    }
}
