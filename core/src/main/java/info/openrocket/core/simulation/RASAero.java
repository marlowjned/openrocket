package info.openrocket.core.simulation;

import com.opencsv.CSVReader;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.wind.PinkNoiseWindModel;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.motor.MotorConfigurationId;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.simulation.exception.SimulationCalculationException;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.simulation.listeners.SimulationListenerHelper;
import info.openrocket.core.simulation.listeners.system.OptimumCoastListener;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors


// reads data from RASAero data csv and creates methods to access that data.
public class RASAero {

    // TODO: Configure path to RASAero CSV file
    public static File file = null;
    //figure out a better data structure for this

    public static HashMap<Double, HashMap<Double, List<Double>> > alhpaMap = new HashMap<>();
    public static HashMap<Double, List<Double>> dataMap = new HashMap<>();

    public static void readfile(){
        List<Double> data = new ArrayList<>();

        //replace w csv file
        //flag if aoa is greater than 5
        //filter aoa and machnum

        //continuously read lines and input into dataMap
        try {
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
                //input into dataMap

                if (alphaStore >= 5){
                    System.out.println("AOA greater than 5 degrees");
                    break;
                }

                alhpaMap.put(alphaStore, dataMap);
                alhpaMap.get(alphaStore).put(machStore, data);
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
        if (dataMap.isEmpty()){
            readfile();
        }
        return dataMap;
    }

    public static List<Double> getDatMach(double MachNum){
        if (dataMap.isEmpty()){
            readfile();
        }
        return dataMap.get(MachNum);
    }

}
