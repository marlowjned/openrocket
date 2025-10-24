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

    /*
    public static void readfile(){
        List<Double> data = new ArrayList<>();

        //replace w csv file
        //flag if aoa is greater than 5
        //filter aoa and machnum

        //continuously read lines and input into machMap
        try {

            // really inefficient, store based on AOA, then mach num (3xN vs NxN)
            System.out.println("Attempting to read RASAero file: " + file.getAbsolutePath());
            System.out.println("File exists: " + file.exists());

            Scanner scan = new Scanner(file);
            scan.nextLine(); // skip first line
            while (scan.hasNextLine()) {

                String rawData = scan.nextLine();
                //condition rawData to input into data array
                String[] temp = rawData.split(",");

                //input data read from file into data array
                data = new ArrayList<>();
                for (int i = 0; i < temp.length; i++) {
                    Double tempVals = Double.parseDouble(temp[i]);
                    data.add(tempVals);
                }

                double machStore = data.get(0);
                double alphaStore = data.get(1);
                //input into machMap

                // probably insufficient, just sabotages the sim without giving a proper warning
                if (alphaStore >= 5) {
                    System.out.println("AOA greater than 5 degrees");
                    break;
                }

                alphaMap.put(alphaStore, machMap);
                alphaMap.get(alphaStore).put(machStore, data);
            }
            scan.close();

        } catch (FileNotFoundException e) {
            System.out.println("File not found.");
            e.printStackTrace();
        }

    }
     */

    public static void readfile(){
        //List<Double> data = new ArrayList<>();

        //replace w csv file
        //flag if aoa is greater than 5
        //filter aoa and machnum

        //continuously read lines and input into machMap
        try {

            System.out.println("Attempting to read RASAero file: " + file.getAbsolutePath());
            System.out.println("File exists: " + file.exists());

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
                    System.out.println("AOA greater than 5 degrees");
                    break;
                }

                // Creates row unless it exists so that data doesn't get overridden
                if (!CDMap.containsKey(alpha)) {
                    CDMap.put(alpha, new HashMap<>());
                }
                CDMap.get(alpha).put(mach, cd);

            }
            scan.close();

        } catch (FileNotFoundException e) {
            System.out.println("File not found.");
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
        System.out.println("getDatMach called, machMap.isEmpty(): " + machMap.isEmpty());
        if (machMap.isEmpty()){
            System.out.println("machMap is empty, calling readfile()");
            readfile();
            System.out.println("After readfile(), machMap size: " + machMap.size());
        }
        System.out.println("Looking for Mach: " + MachNum + " in machMap with keys: " + machMap.keySet());
        return machMap.get(MachNum);
    }

    public static double getCD(double MACH_NUM, double AOA){
        System.out.println("getCD called, machMap.isEmpty(): " + CDMap.isEmpty());
        if (CDMap.isEmpty()){
            System.out.println("machMap is empty, calling readfile()");
            readfile();
            System.out.println("After readfile(), machMap size: " + CDMap.size());
        }
        System.out.println("Looking for Mach: " + MACH_NUM + " in machMap with keys: " + CDMap.keySet());

        //return machMap.get(MACH_NUM);
        return 1.0;
    }



}
