package info.openrocket.core.document;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.SimulationConditions;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.simulation.RASAero;

import java.util.*;

public class BendingAnalysis {

    public static class ComponentBendingData {
        public RocketComponent component;
        public double axialPosCP;
        public double axialPosCG;
        public double normalForce;
        public double bendingMoment;
    }

    public static class FlightBendingData {
        public double time;
        public double mach;
        public double alpha;
        public double dynamicPressure;
        public double netCN;
        public double netNormalForce;
        public List<ComponentBendingData> componentData;
    }

    public static class BendingAnalysisResults {
        public List<FlightBendingData> allBendingData = new ArrayList<>();
        public FlightBendingData maxBendingCase;
        public double referenceArea = 0.0;

        public void addBendingData(FlightBendingData bendingData) {
            allBendingData.add(bendingData);

            if (maxBendingCase == null || bendingData.netNormalForce > maxBendingCase.netNormalForce) {
                maxBendingCase = bendingData;
            }
        }

        public double getMaxBendingMoment() {
            return maxBendingCase != null ? maxBendingCase.netNormalForce : 0.0;
        }

        public double getMaxBendingMomentTime() {
            return maxBendingCase != null ? maxBendingCase.time : 0.0;
        }

        public void exportToCSV(String filename) throws java.io.IOException {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename))) {

                writer.printf("Reference Area (m^2),%.6f%n", referenceArea);
                writer.println("Time (s),Mach,AOA (deg),CN,Dynamic Pressure (Pa),Max Normal Force (N)");
                writer.printf("%.3f,%.4f,%.4f,%.6f,%.2f,%.4f%n",
                        maxBendingCase.time,
                        maxBendingCase.mach,
                        maxBendingCase.alpha,
                        maxBendingCase.netCN,
                        maxBendingCase.dynamicPressure,
                        maxBendingCase.netNormalForce);
                writer.println();
                writer.println("Component Name, Axial Pos CP (m), Axial Pos CG (m), Normal Force (N), Bending Moment (Nm)");
                for (ComponentBendingData comp : maxBendingCase.componentData) {
                    writer.printf("%s,%.4f,%.4f,%.4f,%.6f%n",
                            comp.component.getComponentName(),
                            comp.axialPosCP,
                            comp.axialPosCG,
                            comp.normalForce,
                            comp.bendingMoment);

                }

            }
        }
    }

    private static BendingAnalysisResults currentAnalysisResults = null;

    public static void getBendingFlightData(
            SimulationStatus status,
            double Mach,
            double Alpha,
            SimulationConditions simCond,
            AerodynamicCalculator calculator) {

        // Get RASAero CN coefficient (may be NaN if AOA > 4 or invalid)
        double rasaeroCN = RASAero.getCN(Mach, Alpha);

        FlightBendingData bendingData = new FlightBendingData();
        bendingData.time = status.getSimulationTime();
        bendingData.mach = Mach;
        bendingData.alpha = Alpha;
        bendingData.netCN = rasaeroCN;

        // Calculate dynamic pressure: q = 0.5 * rho * v^2
        double velocity = status.getRocketVelocity().length();
        double airDensity = status.getFlightDataBranch().getLast(FlightDataType.TYPE_AIR_DENSITY);
        bendingData.dynamicPressure = 0.5 * airDensity * velocity * velocity;

        // Create and populate FlightConditions for aerodynamic calculations
        FlightConditions conditions = new FlightConditions(status.getConfiguration());
        conditions.setMach(Mach);
        conditions.setAOA(Math.toRadians(Alpha));
        conditions.setVelocity(velocity);
        conditions.setAtmosphericConditions(simCond.getAtmosphericModel().getConditions(
            status.getRocketPosition().z));

        // Get component-by-component aerodynamic forces
        Map<RocketComponent, AerodynamicForces> forceMap = calculator.getForceAnalysis(
                status.getConfiguration(),
                conditions,
                status.getWarnings()
        );

        // Calculate CN scale factor to match RASAero total (use 1.0 if no RASAero data)
        double cnScaleFactor = Double.isNaN(rasaeroCN) ? 1.0 : getCNScaleFactor(forceMap, rasaeroCN);

        // Reference area for force calculations
        double refArea = status.getConfiguration().getReferenceArea();

        // Build component data list
        List<ComponentBendingData> components = new ArrayList<>();
        for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
            ComponentBendingData cbd = new ComponentBendingData();
            cbd.component = entry.getKey();
            cbd.axialPosCP = entry.getValue().getCP().x;
            cbd.axialPosCG = entry.getKey().getCG().x;

            // Scale component CN and calculate normal force
            double componentCN = entry.getValue().getCN() * cnScaleFactor;
            cbd.normalForce = componentCN * bendingData.dynamicPressure * refArea;

            components.add(cbd);
        }

        // Sort components nose to tail
        components.sort(Comparator.comparingDouble(c -> c.axialPosCG));

        // Calculate bending moments using beam theory
        calculateBendingMoments(components);

        bendingData.componentData = components;

        bendingData.netNormalForce = components.stream()
                .mapToDouble(c -> c.normalForce)
                .sum();

        // Store result
        if (currentAnalysisResults == null) {
            currentAnalysisResults = new BendingAnalysisResults();
            currentAnalysisResults.referenceArea = refArea;
        }
        currentAnalysisResults.addBendingData(bendingData);
    }

    private static double getCNScaleFactor(Map<RocketComponent, AerodynamicForces> forceMap, double rasaeroCN) {
        double openRocketTotalCN = 0.0;
        for (AerodynamicForces forces : forceMap.values()) {
            openRocketTotalCN += forces.getCN();
        }
        return (openRocketTotalCN > 0) ? (rasaeroCN / openRocketTotalCN) : 1.0;
    }

    private static void calculateBendingMoments(List<ComponentBendingData> components) {

        for (ComponentBendingData comp : components) {
            double cumulativeMoment = 0.0;
            double compPosCG = comp.axialPosCG;
            for (ComponentBendingData comp2 : components) {
                double comp2PosCP = comp2.axialPosCP;
                cumulativeMoment += comp2.normalForce * (compPosCG - comp2PosCP);
            }
            comp.bendingMoment = cumulativeMoment;

        }


    }

    public static boolean hasResults() {
        return currentAnalysisResults != null && !currentAnalysisResults.allBendingData.isEmpty();
    }

    public static BendingAnalysisResults getCurrentAnalysisResults() {
        return currentAnalysisResults;
    }

    public static void reset() {
        currentAnalysisResults = null;
    }
}
