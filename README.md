# Temporal Optimization for Container Scheduling in Kubernetes

Code and data for the paper **"Temporal Optimization for Container Scheduling in Kubernetes"**
(Camilla Martins, Pedro Nuno Moura, Sidney Cunha de Lucena, UNIRIO), presented at FTC 2026, Berlin.

The paper models Kubernetes scheduling over a time horizon as a Dynamic Capacitated Facility
Location Problem: nodes are facilities that open and close between time slots, pods are the demand,
and turning a node on or off has a cost (startup δ, shutdown θ). It compares:

- an exact multi-period MILP, solved with Gurobi, and
- **TGCH** (Temporal Greedy Constructive Heuristic), a first-fit scheduler that ranks nodes by a
  *perceived cost* `Φ = α + δ` for nodes that were off in the previous slot and `Φ = α` for nodes
  that were already on, against a reactive greedy baseline that ranks nodes by `α` only.

## Repository contents

| File | What it is |
|---|---|
| `SandBenchmarkComplete.java` | TGCH vs. reactive baseline benchmark (no dependencies). Writes `benchmark_final_sand_2026.csv`. |
| `ArrayBenchmark.java` | Same benchmark with node state in primitive arrays. Produces the same costs as `SandBenchmarkComplete` row by row; use it for the decision times reported in the paper. Writes `benchmark_array.csv`. |
| `TemporalGurobiRunner.java` | Builds and solves the temporal MILP with Gurobi. Writes `resultados_gurobi_sand.csv` and one Gurobi log per run. |
| `benchmark_final_sand_2026.csv` | Output of the benchmark: 21,000 rows, one per (R, pods, nodes, run, slot). Semicolon separated. |
| `resultados_gurobi_sand.csv` | Output of the MILP runner: one row per (N, P, iteration). |
| `analysedata.ipynb` | Reads the benchmark CSV and draws the cost-savings, cost-over-time and decision-time figures. |

## Requirements

- Java 23 (any recent JDK should work)
- For the MILP only: Gurobi 11 or later with a valid license. The runner uses the `com.gurobi.gurobi` Java package.
  The MILP results in the paper were obtained with Gurobi; this repository has the runner but not
  a license. Large instances need a lot of memory: with 16 GB of RAM the run at N = 200, P = 10,000 is killed by the OS.
- For the notebook: Python 3 with `pandas`, `matplotlib` and `seaborn`.

## Running

### Heuristic benchmark

```bash
javac SandBenchmarkComplete.java
java SandBenchmarkComplete
```

For the per-slot decision times, run the array-based version instead (about 10 seconds; it runs the
grid twice to warm up the JIT before measuring):

```bash
javac ArrayBenchmark.java
java ArrayBenchmark            # writes benchmark_array.csv
```

`SandBenchmarkComplete` keeps pods in a `HashMap` and recomputes node usage on every check, so its
`Time_*` columns are much higher; its costs are identical.

Grid: R ∈ {1, 10, 100}, pods ∈ {50, 100, 200, 500, 1000, 5000, 10000},
nodes ∈ {10, 20, 50, 100, 200}, 10 runs each, T = 20 slots, seed 42.

Per slot, the number of pods is `⌊P · ν⌋` with `ν ~ U[0.7, 1.3]`. Half of the nodes are
*trap* nodes (α = 20, δ = 5·α·R) and half are *stable* nodes (α = 250, δ = 10); θ = 0.1·δ for both.

### Exact MILP

```bash
javac -cp "$GUROBI_HOME/lib/gurobi.jar" TemporalGurobiRunner.java
java  -cp ".:$GUROBI_HOME/lib/gurobi.jar" TemporalGurobiRunner
```

Grid: N ∈ {10, 20, 50, 100, 200}, P ∈ {50, …, 10000} (skipping P < N), 10 iterations each,
T = 20, δ = 50, θ = 20, MIP gap 1%. Node and pod parameters (U, α, β, γ, u, e) are drawn with the same
ranges as the static model in Martins et al. (ICUMT 2023).

### Figures

Open `analysedata.ipynb` in the same folder as `benchmark_final_sand_2026.csv` and run all cells.
It writes `fig1.png`, `fig2.png` and `fig_scalability_time.png`.

## CSV columns

`benchmark_final_sand_2026.csv`

| Column | Meaning |
|---|---|
| `Ratio_R` | transition-to-operation ratio R |
| `TamanhoPod` | nominal pods per slot, P |
| `TamanhoNode` | number of nodes, N |
| `Execucao` | run (1 to 10) |
| `Slot` | time slot (1 to 20) |
| `Pods` | pods actually scheduled in this slot |
| `Cost_Baseline`, `Cost_Temporal` | slot cost for the reactive baseline and for TGCH |
| `Time_Baseline_ms`, `Time_Temporal_ms` | decision time for this slot, in milliseconds |

`resultados_gurobi_sand.csv`

| Column | Meaning |
|---|---|
| `N`, `P` | nodes and pods |
| `Iteration` | instance (1 to 10) |
| `Status` | Gurobi status |
| `Time_s` | solver runtime in seconds |
| `Optimal_Cost` | objective value |

## Citation

If you use this code, please cite the paper (BibTeX will be added after publication) and the
static model it extends:

> C. Martins, P. Nuno, C. Vieira, S. Lucena. *Container scheduling in Kubernetes clusters using a mixed
> integer linear programming approach.* ICUMT 2023.

## Authors

Camilla Martins (camilla.martins@edu.unirio.br), Pedro Nuno Moura, Sidney Cunha de Lucena.
Federal University of the State of Rio de Janeiro (UNIRIO), Brazil.
