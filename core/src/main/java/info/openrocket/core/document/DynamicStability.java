package info.openrocket.core.document;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.SimulationConditions;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.simulation.RASAero;

import java.util.*;

public class DynamicStability {

    public static class StabilityData {
        public double time;
        public double machNum;
        public double alpha;
        public double altitude;
        public double velocity;

        public double CP;
        public double CG;
        public double staticMargin;  // (CP - CG) / refLength
        public double q;

        public double CNalpha;
        public double Cmalpha;
        public double Cmq;
        public double naturalFrequency;  // wn in rad/s
        public double dampingRatio;
        public double Iyy;
    }

    public static class DynamicStabilityResults {
        public List<StabilityData> allStabilityData = new ArrayList<>();
        public StabilityData maxDampingRatio;
        public StabilityData minDampingRatio;
        public StabilityData maxNaturalFrequency;
        public double referenceLength;
        public double referenceArea;

        public void addStabilityData(StabilityData data) {
            allStabilityData.add(data);

            // Track max damping ratio
            if (maxDampingRatio == null || data.dampingRatio > maxDampingRatio.dampingRatio) {
                maxDampingRatio = data;
            }

            // Track min damping ratio
            if (minDampingRatio == null || data.dampingRatio < minDampingRatio.dampingRatio) {
                minDampingRatio = data;
            }

            // Track max natural frequency
            if (maxNaturalFrequency == null || data.naturalFrequency > maxNaturalFrequency.naturalFrequency) {
                maxNaturalFrequency = data;
            }
        }

        public void exportToCSV(String filename) throws java.io.IOException {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename))) {
                // Metadata
                writer.printf("Reference Length (m),%.6f%n", referenceLength);
                writer.printf("Reference Area (m^2),%.6f%n", referenceArea);

                // Header
                writer.println("Time (s),Mach,AOA (deg),Altitude (m),Velocity (m/s),CP (m),CG (m),Static Margin,q (Pa),CNalpha (1/rad),Cmalpha (1/rad),Cmq (1/rad),Natural Freq (rad/s),Damping Ratio,Iyy (kg⋅m^2),Notes");

                for (StabilityData data : allStabilityData) {
                    String notes = "";
                    if (data == maxDampingRatio) notes += "MAX_DAMPING ";
                    if (data == minDampingRatio) notes += "MIN_DAMPING ";
                    if (data == maxNaturalFrequency) notes += "MAX_FREQ ";

                    writer.printf("%.3f,%.4f,%.4f,%.2f,%.2f,%.4f,%.4f,%.4f,%.2f,%.6f,%.6f,%.6f,%.4f,%.6f,%.4f,%s%n",
                        data.time,
                        data.machNum,
                        data.alpha,
                        data.altitude,
                        data.velocity,
                        data.CP,
                        data.CG,
                        data.staticMargin,
                        data.q,
                        data.CNalpha,
                        data.Cmalpha,
                        data.Cmq,
                        data.naturalFrequency,
                        data.dampingRatio,
                        data.Iyy,
                        notes.trim()
                    );
                }
            }
        }
    }

    private static DynamicStabilityResults currentResults = null;

    public static void getDynamicStabilityData(
            SimulationStatus status,
            double Mach,
            double Alpha,
            SimulationConditions simCond,
            AerodynamicCalculator calculator) {

        double refLength = status.getConfiguration().getReferenceLength();
        double refArea = status.getConfiguration().getReferenceArea();
        double rocketIyy = status.getFlightDataBranch().getLast(FlightDataType.TYPE_LONGITUDINAL_INERTIA);

        StabilityData data = new StabilityData();
        data.time = status.getSimulationTime();
        data.machNum = Mach;
        data.alpha = Alpha;
        data.altitude = status.getRocketPosition().z;

        // Get velocity and dynamic pressure
        double velocity = status.getRocketVelocity().length();
        data.velocity = velocity;
        double airDensity = status.getFlightDataBranch().getLast(FlightDataType.TYPE_AIR_DENSITY);
        data.q = 0.5 * airDensity * velocity * velocity;

        // Get CP and CG
        data.CP = RASAero.getCPGeneral(Mach, Alpha) * 0.0254;  // Convert inches to meters
        data.CG = status.getFlightDataBranch().getLast(FlightDataType.TYPE_CG_LOCATION);
        double delta = data.CP - data.CG;
        data.staticMargin = delta / refLength;

        // Get aerodynamic derivatives
        data.CNalpha = RASAero.getCNAlpha(Mach, Alpha);
        data.Cmalpha = getCmalpha(data.CNalpha, delta, refLength);
        data.Cmq = getCmq(data.CNalpha, delta, refLength);

        // Calculate dynamic stability parameters
        data.naturalFrequency = getWN(data.Cmalpha, data.q, refArea, refLength, rocketIyy);
        data.dampingRatio = getDampingRatio(data.Cmq, data.q, refArea, refLength, rocketIyy, velocity, data.naturalFrequency);
        data.Iyy = rocketIyy;

        // Store result
        if (currentResults == null) {
            currentResults = new DynamicStabilityResults();
            currentResults.referenceLength = refLength;
            currentResults.referenceArea = refArea;
        }
        currentResults.addStabilityData(data);
    }

    private static double getCmalpha(double CNa, double Delta, double RefLength) {
        return -CNa * Delta / RefLength;
    }

    private static double getCmq(double CNa, double Delta, double RefLength) {
        return -2 * CNa * Delta * Delta / RefLength / RefLength;
    }

    private static double getWN(double Cma, double Q, double RefArea, double RefLength, double Iyy) {
        return Math.sqrt(-Cma * Q * RefArea * RefLength / Iyy);
    }

    private static double getDampingRatio(double Cmq, double Q, double RefArea, double RefLength, double Iyy, double V, double WN) {
        return -Cmq * Q * RefArea * RefLength * RefLength / (2 * Iyy * V * WN);
    }

    public static boolean hasResults() {
        return currentResults != null && !currentResults.allStabilityData.isEmpty();
    }

    public static DynamicStabilityResults getCurrentResults() {
        return currentResults;
    }

    public static void reset() {
        currentResults = null;
    }
}
