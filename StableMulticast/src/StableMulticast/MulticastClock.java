package StableMulticast;

import java.io.Serializable;
import java.util.Arrays;

public class MulticastClock implements Serializable {
    private static final long serialVersionUID = 1L;
    private int[][] mc;
    private int numberOfProcesses;

    public MulticastClock(int numberOfProcesses) {
        this.numberOfProcesses = numberOfProcesses;
        this.mc = new int[numberOfProcesses][numberOfProcesses];
        for (int i = 0; i < numberOfProcesses; i++) {
            Arrays.fill(mc[i], 0);
        }
    }

    public synchronized int[][] getMc() {
        return mc;
    }

    public synchronized void setMc(int[][] mc) {
        if (mc.length != numberOfProcesses || (mc.length > 0 && mc[0].length != numberOfProcesses)) {
            throw new IllegalArgumentException("Invalid MC dimensions.");
        }
        for (int i = 0; i < numberOfProcesses; i++) {
            System.arraycopy(mc[i], 0, this.mc[i], 0, numberOfProcesses);
        }
    }

    // --- ADICIONE ESTE MÉTODO ---
    public synchronized int getNumberOfProcesses() {
        return numberOfProcesses;
    }
    // --------------------------

    public synchronized void increment(int processId, int vectorIndex) {
        if (processId >= 0 && processId < numberOfProcesses && vectorIndex >= 0 && vectorIndex < numberOfProcesses) {
            mc[processId][vectorIndex]++;
        }
    }

    public synchronized int[] getVector(int processId) {
        if (processId >= 0 && processId < numberOfProcesses) {
            return Arrays.copyOf(mc[processId], numberOfProcesses);
        }
        return null;
    }

    public synchronized void updateVector(int processId, int[] newVector) {
        if (processId >= 0 && processId < numberOfProcesses && newVector.length == numberOfProcesses) {
            System.arraycopy(newVector, 0, mc[processId], 0, numberOfProcesses);
        }
    }

    public synchronized int getValue(int processId, int vectorIndex) {
        if (processId >= 0 && processId < numberOfProcesses && vectorIndex >= 0 && vectorIndex < numberOfProcesses) {
            return mc[processId][vectorIndex];
        }
        return -1; // Or throw an exception
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder("MulticastClock:\n");
        for (int i = 0; i < numberOfProcesses; i++) {
            sb.append("P").append(i).append(": [");
            for (int j = 0; j < numberOfProcesses; j++) {
                sb.append(mc[i][j]);
                if (j < numberOfProcesses - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]\n");
        }
        return sb.toString();
    }
}