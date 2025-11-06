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

    public static RASAeroDataMaps RASMaps = null;
    public static BivariateFunction ContinuousCD;
    public static BivariateFunction ContinuousCL;
    public static BivariateFunction ContinuousCN;
    public static BivariateFunction ContinuousCNAlpha;
    public static BivariateFunction ContinuousCP;
    public static BivariateFunction ContinuousCPGeneral;
    public static BivariateFunction ContinuousReynoldsNum;

    public static class RASAeroData {
        double CD;          // Drag Coefficient
        double CL;          // Lift Coefficient
        double CN;          // Normal Force Coefficient
        double CNAlpha;     // dCN/dAlpha, has Dynamic Stability applications
        double CP;          // Center of Pressure from rocket tip, gives specific values for each alpha
        double CPGeneral;   // Center of Pressure, gives conservative values for each mach, DS applications
        double ReynoldsNum; // Maybe useful one day

    };

    public static class RASAeroDataMaps {
        public HashMap<Double, Integer> alphaIndex;
        public HashMap<Double, Integer> machIndex;
        public List<Double> alphaKeys;
        public List<Double> machKeys;

        public List<List<Double>> CDVals;
        public List<List<Double>> CLVals;
        public List<List<Double>> CNVals;
        public List<List<Double>> CNAlphaVals;
        public List<List<Double>> CPVals;
        public List<List<Double>> CPGeneralVals;
        public List<List<Double>> ReynoldsNumVals;

        public RASAeroDataMaps() {

            alphaIndex = new HashMap<>();
            machIndex = new HashMap<>();
            alphaKeys = new ArrayList<>();
            machKeys = new ArrayList<>();

            CDVals = new ArrayList<>();
            CLVals = new ArrayList<>();
            CNVals = new ArrayList<>();
            CNAlphaVals = new ArrayList<>();
            CPVals = new ArrayList<>();
            CPGeneralVals = new ArrayList<>();
            ReynoldsNumVals = new ArrayList<>();

        }

        public void addDataRow(List<Double> DataRow) {
            // Check if mach, alpha vals are already in key lists
            if (!machIndex.containsKey(DataRow.get(0))) {
                machIndex.put(DataRow.get(0), machIndex.size());
                machKeys.add(DataRow.get(0));

                // Resize: add a null row
                List<Double> newMachRow =  new ArrayList<>();
                for (int i = 0; i < alphaKeys.size(); i++) {
                    newMachRow.add(null);
                }

                CDVals.add(new ArrayList<>(newMachRow));
                CLVals.add(new ArrayList<>(newMachRow));
                CNVals.add(new ArrayList<>(newMachRow));
                CNAlphaVals.add(new ArrayList<>(newMachRow));
                CPVals.add(new ArrayList<>(newMachRow));
                CPGeneralVals.add(new ArrayList<>(newMachRow));
                ReynoldsNumVals.add(new ArrayList<>(newMachRow));

            }

            if (!alphaIndex.containsKey(DataRow.get(1))) {
                alphaIndex.put(DataRow.get(1), alphaIndex.size());
                alphaKeys.add(DataRow.get(1));

                // Resize: add null to the end of every row
                for (List<Double> row : CDVals) { row.add(null); }
                for (List<Double> row : CLVals) { row.add(null); }
                for (List<Double> row : CNVals) { row.add(null); }
                for (List<Double> row : CNAlphaVals) { row.add(null); }
                for (List<Double> row : CPVals) { row.add(null); }
                for (List<Double> row : CPGeneralVals) { row.add(null); }
                for (List<Double> row : ReynoldsNumVals) { row.add(null); }

            }

            int currMachIndex = machIndex.get(DataRow.get(0));
            int currAlphaIndex = alphaIndex.get(DataRow.get(1));

            CDVals.get(currMachIndex).set(currAlphaIndex, DataRow.get(2));
            CLVals.get(currMachIndex).set(currAlphaIndex, DataRow.get(7));
            CNVals.get(currMachIndex).set(currAlphaIndex, DataRow.get(8));
            CNAlphaVals.get(currMachIndex).set(currAlphaIndex, DataRow.get(11));
            CPVals.get(currMachIndex).set(currAlphaIndex, DataRow.get(12));
            CPGeneralVals.get(currMachIndex).set(currAlphaIndex, DataRow.get(13));
            ReynoldsNumVals.get(currMachIndex).set(currAlphaIndex, DataRow.get(14));

        }

/*
        public RASAeroData getRASAeroData(double MachNum, double Alpha) {
            RASAeroData rasData = new RASAeroData();
            int currMachIndex = machIndex.get(MachNum);
            int currAlphaIndex = alphaIndex.get(Alpha);

            rasData.CD = CDVals.get(currMachIndex).get(currAlphaIndex);
            rasData.CL = CLVals.get(currMachIndex).get(currAlphaIndex);
            rasData.CN = CNVals.get(currMachIndex).get(currAlphaIndex);
            rasData.CNAlpha = CNAlphaVals.get(currMachIndex).get(currAlphaIndex);
            rasData.CP = CPVals.get(currMachIndex).get(currAlphaIndex);
            rasData.CPGeneral = CPGeneralVals.get(currMachIndex).get(currAlphaIndex);
            rasData.ReynoldsNum = ReynoldsNumVals.get(currMachIndex).get(currAlphaIndex);

            return rasData;

        }
        */

        public void fillInNull() {
            fillInNullList(CDVals);
            fillInNullList(CLVals);
            fillInNullList(CNVals);
            fillInNullList(CNAlphaVals);
            fillInNullList(CPVals);
            fillInNullList(CPGeneralVals);
            fillInNullList(ReynoldsNumVals);

        }

        private void fillInNullList(List<List<Double>> values) {
            for (int i = 0; i < values.size(); i++) {
                for (int j = 0; j < values.get(i).size(); j++) {
                    if (values.get(i).get(j) == null) {
                        // Use previous mach number's value (i-1)
                        if (i > 0 && values.get(i-1).get(j) != null) {
                            values.get(i).set(j, values.get(i-1).get(j));
                        } else {
                            values.get(i).set(j, 0.0); // Temp default if all else fails
                            // TODO: make this better
                        }
                    }
                }
            }
        }


    };

    // TODO: implement CD, CN, CL, CP into flight sims
    // TODO: Bending script
    // TODO: Dynamic Stability Script
    // TODO: New Wind Data

    public static void readFile(){
        try {
            RASMaps = new RASAeroDataMaps();

            Scanner scan = new Scanner(file);
            scan.nextLine(); // Skips first line

            while (scan.hasNextLine()) {
                String rawData = scan.nextLine();
                String[] tempData = rawData.split(",");

                List<Double> data = new ArrayList<>();
                for (String tempDatum : tempData) {
                    data.add(Double.parseDouble(tempDatum));
                }
                RASMaps.addDataRow(data);

            }

            scan.close();

            RASMaps.fillInNull(); // TODO: Add sorter so keys are guaranteed monotonic
            generateInterpolators();

        } catch (FileNotFoundException e) {
            e.printStackTrace();

        }

    }


    private static void generateInterpolators() {
        // Convert Lists to arrays for interpolator
        double[] alphaArray = RASMaps.alphaKeys.stream()
                .mapToDouble(Double::doubleValue).toArray();
        double[] machArray = RASMaps.machKeys.stream()
                .mapToDouble(Double::doubleValue).toArray();

        // Convert 2D Lists to 2D arrays [mach][alpha]
        double[][] cdArray = to2DArray(RASMaps.CDVals);
        double[][] clArray = to2DArray(RASMaps.CLVals);
        double[][] cnArray = to2DArray(RASMaps.CNVals);
        double[][] cnAlphaArray = to2DArray(RASMaps.CNAlphaVals);
        double[][] cpArray = to2DArray(RASMaps.CPVals);
        double[][] cpGeneralArray = to2DArray(RASMaps.CPGeneralVals);
        double[][] reynoldsArray = to2DArray(RASMaps.ReynoldsNumVals);

        // Create interpolators
        BivariateGridInterpolator interpolator = new BicubicInterpolator();
        ContinuousCD = interpolator.interpolate(machArray, alphaArray, cdArray);
        ContinuousCN = interpolator.interpolate(machArray, alphaArray, cnArray);
        ContinuousCL = interpolator.interpolate(machArray, alphaArray, clArray);
        ContinuousCP = interpolator.interpolate(machArray, alphaArray, cpArray);
        ContinuousCNAlpha = interpolator.interpolate(machArray, alphaArray, cnAlphaArray);
        ContinuousCPGeneral = interpolator.interpolate(machArray, alphaArray, cpGeneralArray);
        ContinuousReynoldsNum =  interpolator.interpolate(machArray, alphaArray, reynoldsArray);

    }

    private static double[][] to2DArray(List<List<Double>> listOfLists) {
        // TODO: Just make the lists 2D arrays in the first place
        int rows = listOfLists.size();
        int cols = listOfLists.get(0).size();
        double[][] array = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                Double val = listOfLists.get(i).get(j);
                array[i][j] = (val != null) ? val : 0.0; // Handle nulls
            }
        }
        return array;
    }


    public static RASAeroData getRASAeroData(double MachNum, double Alpha){
        RASAeroData data = new RASAeroData();
        if (RASMaps == null) {
            readFile();
        }

        if (Double.isNaN(Alpha) || Alpha > 4) {
            return data;
        }
        double clampedMach = Double.isNaN(MachNum) ? 0.01 : Math.max(0.01, MachNum);

        data.CD = ContinuousCD.value(clampedMach, Alpha);
        data.CL = ContinuousCL.value(clampedMach, Alpha);
        data.CN = ContinuousCN.value(clampedMach, Alpha);
        data.CNAlpha = ContinuousCNAlpha.value(clampedMach, Alpha);
        data.CP = ContinuousCP.value(clampedMach, Alpha);
        data.CPGeneral = ContinuousCPGeneral.value(clampedMach, Alpha);
        data.ReynoldsNum = ContinuousReynoldsNum.value(clampedMach, Alpha);
        return data;

    }

    public static double getCD(double MACH_NUM, double AOA){
        if (RASMaps == null || RASMaps.CDVals.isEmpty()){
            readFile();
        }
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN; // Signals to skip override
        }
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        return ContinuousCD.value(clampedMach, AOA);

    }

    public static double getCL(double MACH_NUM, double AOA){
        if (RASMaps == null || RASMaps.CLVals.isEmpty()) {
            readFile();
        }
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN;
        }
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        return ContinuousCL.value(clampedMach, AOA);

    }

    public static double getCN(double MACH_NUM, double AOA){
        if (RASMaps == null || RASMaps.CNVals.isEmpty()) {
            readFile();
        }
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN;
        }
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        return ContinuousCN.value(clampedMach, AOA);

    }

    public static double getCNAlpha(double MACH_NUM, double AOA){
        if (RASMaps == null || RASMaps.CNAlphaVals.isEmpty()) {
            readFile();
        }
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN;
        }
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        return ContinuousCNAlpha.value(clampedMach, AOA);

    }

    public static double getCP(double MACH_NUM, double AOA){
        if (RASMaps == null || RASMaps.CPVals.isEmpty()) {
            readFile();
        }
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN;
        }
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        return ContinuousCP.value(clampedMach, AOA);

    }

    public static double getCPGeneral(double MACH_NUM, double AOA){
        if (RASMaps == null || RASMaps.CPGeneralVals.isEmpty()) {
            readFile();
        }
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN;
        }
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        return ContinuousCPGeneral.value(clampedMach, AOA);

    }

    public static double getReynoldsNum(double MACH_NUM, double AOA){
        if (RASMaps == null || RASMaps.ReynoldsNumVals.isEmpty()) {
            readFile();
        }
        if (Double.isNaN(AOA) || AOA > 4) {
            return Double.NaN;
        }
        double clampedMach = Double.isNaN(MACH_NUM) ? 0.01 : Math.max(0.01, MACH_NUM);
        return ContinuousReynoldsNum.value(clampedMach, AOA);

    }

}
