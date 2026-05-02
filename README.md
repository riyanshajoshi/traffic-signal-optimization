# DAA Project: Dynamic Traffic Signal Optimization In Java

This project demonstrates:

**Intelligent Dynamic Traffic Signal Optimization Using Greedy Algorithms and Priority Queues**

The program models every road lane as a task competing for one shared resource: the green signal. At each decision cycle, simulated sensor readings generate real-time traffic data. The algorithm computes the priority of every lane and greedily selects the lane with maximum priority using Java's `PriorityQueue`.

## How it works

- Initial traffic density is generated at runtime.
- New vehicle arrivals are random, like live sensor readings.
- Some cycles simulate peak traffic.
- Sudden traffic bursts can occur.
- Emergency vehicles are detected probabilistically instead of being hard-coded.
- Every run can produce different output.

## Priority Formula

```text
Priority = (Vehicle_Count * Waiting_Time) + Emergency_Bonus + Fairness_Factor
```

- `Vehicle_Count * Waiting_Time` gives preference to congested lanes that have waited longer.
- `Emergency_Bonus` gives immediate priority to lanes with an ambulance, fire truck, or police vehicle.
- `Fairness_Factor` increases for skipped lanes to avoid starvation.


## How To Run

Open PowerShell in this folder and run:

```powershell
javac DynamicTrafficSignalOptimization.java
java DynamicTrafficSignalOptimization 10
```

The first argument is the number of signal decision cycles:

```powershell
java DynamicTrafficSignalOptimization 15
```

By default, the output changes each run. If you want a repeatable run for practice, pass a seed as the second argument:

```powershell
java DynamicTrafficSignalOptimization 10 42
```

## What The Demo Shows

1. Runtime sensor updates for each lane.
2. Random traffic arrivals and sudden bursts.
3. Random emergency vehicle detection.
4. Current lane table with priority score.
5. Greedy selection of the highest-priority lane.
6. Dynamic green signal duration.
7. Final metrics: arrivals, served vehicles, remaining vehicles, green time, and delay score.

## Complexity

For `n` lanes:

- Priority calculation for all lanes: `O(n)`.
- Building the max priority queue: `O(n)`.
- Extracting the highest-priority lane: `O(log n)`.
- Space complexity: `O(n)`.

In an actual traffic system, the sensor data would come from cameras, loop detectors, or IoT devices. Here, random generation is used to demonstrate the same real-time behavior without external hardware.
