package info.openrocket.core.simulation;

import java.util.*;

import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors

import org.apache.commons.math4.legacy.analysis.BivariateFunction;
import org.apache.commons.math4.legacy.analysis.interpolation.BicubicInterpolator;
import org.apache.commons.math4.legacy.analysis.interpolation.BivariateGridInterpolator;


// reads data from RASAero data csv and creates methods to access that data.
public class RASAero {

    // TODO: Configure path to RASAero CSV file
    public static File file = new File("core/src/main/resources/Rasaero/Eureka3_Machnum_RASAero2_sim.CSV");
    //figure out a better data structure for this

    public static HashMap<Double, HashMap<Double, List<Double>> > alphaMap = new HashMap<>();
    public static HashMap<Double, List<Double>> machMap = new HashMap<>();
    public static HashMap<Double, HashMap<Double, Double>> CDMap = new HashMap<>();

    public static BivariateFunction ContinuousCD;

    public class RasaeroData {
        double CD; // Drag Coefficient
        double CN; // Normal Force Coefficient
        double CL; // Lift Coefficient
        double CNAlpha; // dCN/dAlpha, has Dynamic Stability applications
        double CP; // Center of Pressure from rocket tip, gives specific values for each alpha
        double CPGeneral; // Center of Pressure, gives conservative values for each mach, DS applications
    };

    // TODO: implement CD, CN, CL, CP into flight sims
    // TODO: Bending script
    // TODO: Dynamic Stability Script
    // TODO: New Wind Data

    public static void readfile(){
        //List<Double> data = new ArrayList<>();

        //replace w csv file
        //flag if aoa is greater than 5
        //filter aoa and machnum

        //continuously read lines and input into machMap
        try {

            // System.out.println("Attempting to read RASAero file: " + file.getAbsolutePath());
            // System.out.println("File exists: " + file.exists());

            Scanner scan = new Scanner(file);
            scan.nextLine(); // skip first line

            while (scan.hasNextLine()) {

                String rawData = scan.nextLine();
                String[] tempData = rawData.split(",");

                List<Double> data = new ArrayList<>();
                for (String tempDataPT : tempData) {
                    data.add(Double.parseDouble(tempDataPT));
                }

                double mach = data.get(0);
                double alpha = data.get(1);
                double cd = data.get(2);

                // TODO: probably insufficient, just sabotages the sim without giving a proper warning
                if (alpha >= 5) {
                    // System.out.println("AOA greater than 5 degrees");
                    break;
                }

                // Creates row unless it exists so that data doesn't get overridden
                if (!CDMap.containsKey(alpha)) {
                    CDMap.put(alpha, new HashMap<>());
                }
                CDMap.get(alpha).put(mach, cd);

            }

            scan.close();

            // FUNCTION GENERATION FOR BIVARIATE INTERPOLATOR
            // Assume all inner maps have the same mach/AOA keys
            double[] alphaVals = CDMap.keySet().stream()
                    .sorted()
                    .mapToDouble(Double::doubleValue)
                    .toArray();

            Set<Double> machSet = CDMap.values().iterator().next().keySet();
            double[] machVals = machSet.stream()
                    .sorted()
                    .mapToDouble(Double::doubleValue)
                    .toArray();

            double[][] CDVals = new double[machVals.length][alphaVals.length];
            for (int j = 0; j < alphaVals.length; j++) {
                double alpha = alphaVals[j];
                for (int i = 0; i < machVals.length; i++) {
                    double mach = machVals[i];
                //for (int j = 0; j < alphaVals.length; j++) {
                  //  double alpha = alphaVals[j];
                    //CDVals[i][j] = CDMap.get(alpha).get(mach);
                    Double CDValue =  CDMap.get(alpha).get(mach);
                    if (CDValue == null) {
                        // System.out.println("Warning: No CD data for Mach=" + mach + ", Alpha=" + alpha + ", using 0.5 as default");
                        CDVals[i][j] = !Double.isNaN(CDVals[i-1][j]) ? CDVals[i-1][j] : 0.5;
                    } else {
                        CDVals[i][j] = CDValue;
                    }

                    // PROPOSED FIX (commented out):
                    // Double cdValue = CDMap.get(alpha).get(mach);
                    // if (cdValue == null) {
                    //     System.out.println("Warning: No CD data for Mach=" + mach + ", Alpha=" + alpha + ", using 0.5 as default");
                    //     CDVals[i][j] = 0.5; // reasonable default CD value
                    // } else {
                    //     CDVals[i][j] = cdValue;
                    // }
                }
            }

            // Generate Interpolator Function
            BivariateGridInterpolator interpolator = new BicubicInterpolator();
            ContinuousCD = interpolator.interpolate(machVals, alphaVals, CDVals);

        } catch (FileNotFoundException e) {
            // System.out.println("File not found.");
            e.printStackTrace();
        }

    }

    //reads specific file input filename TODO: add to path st file can be located auto
    public static void readFile(String fileName){
        file  = new File(fileName); // make it so this adds filename to path (talk to eric)
        readfile();
    }

    //returns greater data structure (make hashmap)
    public static HashMap<Double, List<Double>> getData(){
        if (machMap.isEmpty()){
            readfile();
        }
        return machMap;
    }

    public static List<Double> getDatMach(double MachNum){
        // System.out.println("getDatMach called, machMap.isEmpty(): " + machMap.isEmpty());
        if (machMap.isEmpty()){
            // System.out.println("machMap is empty, calling readfile()");
            readfile();
            // System.out.println("After readfile(), machMap size: " + machMap.size());
        }
        // System.out.println("Looking for Mach: " + MachNum + " in machMap with keys: " + machMap.keySet());
        return machMap.get(MachNum);
    }

    public static double getCD(double MACH_NUM, double AOA){
        // System.out.println("getCD called, machMap.isEmpty(): " + CDMap.isEmpty());
        if (CDMap.isEmpty()){
            // System.out.println("machMap is empty, calling readfile()");
            readfile();
            // System.out.println("After readfile(), machMap size: " + CDMap.size());
        }
        // System.out.println("Looking for Mach: " + MACH_NUM + " in machMap with keys: " + CDMap.keySet());

        // If AOA is NaN, don't override CD - return NaN to signal caller to skip override
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN;
        }
        //TODO: ALSO DO NOT OVERRIDE IF AOA > 4

        // Clamp Mach and AOA to valid interpolation range
        // RASAero data ranges from Mach 0.01-25, Alpha 0-4 degrees
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        //double clampedAOA = Math.max(0.0, Math.min(Math.abs(AOA), 4.0));

        // if (clampedMach != MACH_NUM || clampedAOA != Math.abs(AOA)) {
        //     System.out.println("Warning: Clamped Mach=" + MACH_NUM + " to " + clampedMach + ", AOA=" + AOA + " to " + clampedAOA);
        // }

        return ContinuousCD.value(clampedMach, AOA);
    }



}
