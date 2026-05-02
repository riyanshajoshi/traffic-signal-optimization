# DAA Project: Dynamic Traffic Signal Optimization In Java

This project demonstrates the synopsis topic:

**Intelligent Dynamic Traffic Signal Optimization Using Greedy Algorithms and Priority Queues**

The program models each traffic lane as a task competing for one shared resource: the green signal. At every decision cycle, it calculates a priority score for every lane and greedily selects the lane with the highest score using a max priority queue.

## Priority Formula

```text
Priority = (Vehicle_Count * Waiting_Time) + Emergency_Bonus + Fairness_Factor
```

- `Vehicle_Count * Waiting_Time` gives preference to congested lanes that have waited longer.
- `Emergency_Bonus` gives immediate priority to lanes with an ambulance/fire/police vehicle.
- `Fairness_Factor` increases for lanes skipped in previous rounds to avoid starvation.

## Files

- `DynamicTrafficSignalOptimization.java` - Java implementation of the algorithm and simulation.
- `presentation_notes.md` - Short explanation for viva/demo.

## How To Run

Open PowerShell in this folder and run:

```powershell
javac DynamicTrafficSignalOptimization.java
java DynamicTrafficSignalOptimization 10
```

You can change `10` to any number of decision cycles:

```powershell
java DynamicTrafficSignalOptimization 15
```

## What The Demo Shows

1. Current traffic state of all lanes.
2. Computed priority score for every lane.
3. The greedy choice made by the max priority queue.
4. Green signal duration and number of vehicles served.
5. Emergency vehicle prioritization.
6. Starvation prevention through fairness score.
7. Final summary metrics.

## Complexity

For `n` lanes:

- Heap construction in this simulation: `O(n)` per decision cycle.
- Extracting the highest priority lane: `O(log n)`.
- Space complexity: `O(n)`.

In a real event-driven system, when only one lane's value changes at a time, priority updates can be handled in `O(log n)` using an indexed heap or balanced tree.
