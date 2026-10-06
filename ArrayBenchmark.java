import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * Array-based version of SandBenchmarkComplete.
 *
 * Same instances, same random sequence and same scheduling decisions (stable sorts on the
 * previous slot's order, first fit, pods of size 5), so Cost_Baseline and Cost_Temporal match
 * benchmark_final_sand_2026.csv row by row. Only the data structures change: node state lives in
 * primitive arrays instead of HashMaps, which is what the per-slot decision times measure.
 */
public class ArrayBenchmark {

    static final int POD_SIZE = 5;
    static final int WARMUP_ROUNDS = 2;

    public static void main(String[] args) throws IOException {
        String filename = args.length > 0 ? args[0] : "benchmark_array.csv";

        double[] ratios = {1.0, 10.0, 100.0};
        int[] podSizes = {50, 100, 200, 500, 1000, 5000, 10000};
        int[] nodeSizes = {10, 20, 50, 100, 200};
        int numExecutions = 10;
        int timeSlots = 20;

        // JIT warm-up: run the whole grid without writing, then measure.
        for (int w = 0; w < WARMUP_ROUNDS; w++) {
            run(ratios, podSizes, nodeSizes, numExecutions, timeSlots, null);
        }
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("Ratio_R;TamanhoPod;TamanhoNode;Execucao;Slot;Pods;Cost_Baseline;Cost_Temporal;Time_Baseline_ms;Time_Temporal_ms\n");
            run(ratios, podSizes, nodeSizes, numExecutions, timeSlots, writer);
        }
        System.out.println("Done: " + filename);
    }

    static void run(double[] ratios, int[] podSizes, int[] nodeSizes, int numExecutions, int timeSlots,
                    FileWriter writer) throws IOException {
        Random rand = new Random(42);
        for (double R : ratios) {
            for (int pSize : podSizes) {
                for (int nSize : nodeSizes) {
                    double[] alpha = new double[nSize];
                    double[] delta = new double[nSize];
                    double[] theta = new double[nSize];
                    int[] cap = new int[nSize];

                    for (int e = 1; e <= numExecutions; e++) {
                        for (int i = 0; i < nSize; i++) {
                            alpha[i] = (i < nSize / 2) ? 20.0 : 250.0;
                            delta[i] = (i < nSize / 2) ? (alpha[i] * R * 5) : 10.0;
                            cap[i] = Math.max(100, (pSize * 10) / nSize);
                            theta[i] = delta[i] * 0.1;
                        }

                        // Each scheduler keeps its node order between slots (the original sorts a List in place).
                        int[] orderB = identity(nSize);
                        int[] orderT = identity(nSize);
                        int[] usedB = new int[nSize], usedT = new int[nSize];
                        int[] podsB = new int[nSize], podsT = new int[nSize];
                        boolean[] prevB = new boolean[nSize], prevT = new boolean[nSize];
                        double[] key = new double[nSize];

                        for (int s = 1; s <= timeSlots; s++) {
                            double variacao = 0.7 + (rand.nextDouble() * 0.6);
                            int target = (int) (pSize * variacao);

                            // Reactive baseline: sort by alpha only.
                            long startB = System.nanoTime();
                            java.util.Arrays.fill(usedB, 0);
                            java.util.Arrays.fill(podsB, 0);
                            for (int i = 0; i < nSize; i++) key[i] = alpha[i];
                            stableSort(orderB, key);
                            firstFit(orderB, usedB, podsB, cap, target);
                            double timeB = (System.nanoTime() - startB) / 1_000_000.0;

                            double costB = cost(podsB, prevB, alpha, delta, theta);
                            for (int i = 0; i < nSize; i++) prevB[i] = podsB[i] > 0;

                            // TGCH: perceived cost = alpha, plus delta if the node was off in the previous slot.
                            long startT = System.nanoTime();
                            java.util.Arrays.fill(usedT, 0);
                            java.util.Arrays.fill(podsT, 0);
                            for (int i = 0; i < nSize; i++) key[i] = alpha[i] + (prevT[i] ? 0 : delta[i]);
                            stableSort(orderT, key);
                            firstFit(orderT, usedT, podsT, cap, target);
                            double timeT = (System.nanoTime() - startT) / 1_000_000.0;

                            double costT = cost(podsT, prevT, alpha, delta, theta);
                            for (int i = 0; i < nSize; i++) prevT[i] = podsT[i] > 0;

                            if (writer != null) {
                                writer.write(R + ";" + pSize + ";" + nSize + ";" + e + ";" + s + ";" + target + ";"
                                        + costB + ";" + costT + ";" + timeB + ";" + timeT + "\n");
                            }
                        }
                    }
                }
            }
        }
    }

    static int[] identity(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    /** Stable insertion sort of node ids by key; the order is almost sorted from the previous slot. */
    static void stableSort(int[] order, double[] key) {
        for (int i = 1; i < order.length; i++) {
            int id = order[i];
            double k = key[id];
            int j = i - 1;
            while (j >= 0 && key[order[j]] > k) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = id;
        }
    }

    /** One pod at a time, first node in the given order with room for it (Algorithm 1, lines 10-18). */
    static void firstFit(int[] order, int[] used, int[] pods, int[] cap, int target) {
        // All pods have the same size, so a node that cannot take one pod never takes another:
        // first fit can resume from the first node that still had room.
        int start = 0;
        for (int p = 0; p < target; p++) {
            for (int idx = start; idx < order.length; idx++) {
                int n = order[idx];
                if (used[n] + POD_SIZE <= cap[n]) {
                    used[n] += POD_SIZE;
                    pods[n]++;
                    break;
                }
                start = idx + 1;
            }
        }
    }

    /** Same cost as SandBenchmarkComplete.calculate. */
    static double cost(int[] pods, boolean[] prev, double[] alpha, double[] delta, double[] theta) {
        double total = 0;
        for (int i = 0; i < pods.length; i++) {
            boolean active = pods[i] > 0;
            if (active) {
                total += alpha[i];
                if (!prev[i]) total += delta[i];
                total += pods[i] * 2.5;
            } else if (prev[i]) {
                total += theta[i];
            }
        }
        return total;
    }
}
