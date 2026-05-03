# DAA Project: Dynamic Traffic Signal Optimization In Java

This project demonstrates:

**Intelligent Dynamic Traffic Signal Optimization Using Greedy Algorithms and Priority Queues**

The program models every road lane as a task competing for one shared resource: the green signal. At each decision cycle, simulated sensor readings generate real-time traffic data. The algorithm computes the priority of every lane and greedily selects the lane with maximum priority using Java's `PriorityQueue`.

## Input Modes

When the program starts, it asks you to choose:

```text
1. Manual user input
2. Random sensor simulation
```

Use **Manual user input** for your project demonstration. In this mode, you enter:

- number of lanes
- lane names
- current vehicle count
- current waiting time
- emergency vehicle status
- new vehicles before each signal decision
- new emergency status before each signal decision

Use **Random sensor simulation** when you want the program to behave like a sensor-based traffic system without typing every update.

## What Makes This Version More Realistic

- Initial traffic density is generated at runtime.
- New vehicle arrivals are random, like live sensor readings.
- Some cycles simulate peak traffic.
- Sudden traffic bursts can occur.
- Emergency vehicles are detected probabilistically instead of being hard-coded.
- Manual mode can use real user-entered values instead of predetermined output.

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
java DynamicTrafficSignalOptimization
```

Then choose mode `1` for manual input.

## Sample Manual Input

You can enter values like this during the prompts:

```text
Choose mode: 1
Enter number of decision cycles: 2
Enter number of lanes: 4

Lane 1 details
Lane name: North
Current vehicle count: 18
Current waiting time in seconds: 40
Emergency vehicle present? (y/n): n

Lane 2 details
Lane name: East
Current vehicle count: 7
Current waiting time in seconds: 70
Emergency vehicle present? (y/n): n

Lane 3 details
Lane name: South
Current vehicle count: 12
Current waiting time in seconds: 25
Emergency vehicle present? (y/n): n

Lane 4 details
Lane name: West
Current vehicle count: 4
Current waiting time in seconds: 90
Emergency vehicle present? (y/n): y
```

## What The Demo Shows

1. User-entered or randomly generated sensor updates.
2. Current lane table with priority score.
3. Greedy selection of the highest-priority lane.
4. Dynamic green signal duration.
5. Emergency vehicle priority.
6. Final metrics: arrivals, served vehicles, remaining vehicles, green time, and delay score.

## Complexity

For `n` lanes:

- Priority calculation for all lanes: `O(n)`.
- Building the max priority queue: `O(n)`.
- Extracting the highest-priority lane: `O(log n)`.
- Space complexity: `O(n)`.

In an actual traffic system, the sensor data would come from cameras, loop detectors, or IoT devices. Here, random generation is used to demonstrate the same real-time behavior without external hardware.
