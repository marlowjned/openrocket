package info.openrocket.core.document;


import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.models.wind.PinkNoiseWindModel;
import info.openrocket.core.motor.Motor;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.motor.Manufacturer;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.Parachute;
import info.openrocket.core.rocketcomponent.RecoveryDevice;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.simulation.*;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.util.WorldCoordinate;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


//This file will generate an entire simulation based on inputted data.
public class DispersionAnalysis {


    //Notes:
    //How calls flow: Simulator -> simulationEngine interface, specifically BasicEventSimulationEngine,
    //-> simulationConditions ->

    /**CHANGES MADE to the following classes:
     * Simulation
     * BasicEventSimulationEngine
     * SimulationEngine
     * Recovery Device,
     * OpenRocketComponentLoader - this was to fix the parachute database not appearing mysteriously
     */

    //Fundamental variables: DO NOT CHANGE
    private SimulationConditions conditions;
    private final Class<? extends SimulationEngine> simulationEngineClass = BasicEventSimulationEngineDispersionAnalysis.class;
    private SimulationEngine simulator;


    //DATA variables
    private Double[][] data;

    private Double[][] locData; //specific only to location data vs time

    private int index;


    //Changable Variables (set values for simulation condition)
    private FlightData flightSummary;
    private double launchRodLength; //in meters LMAO

    /**
     * Launch rod angle >= 0, radians from vertical
     */
    private double launchRodAngle;

    /**
     * Launch rod direction, 0 = north
     */
    double launchRodDirection;

    // Launch site location (lat, lon, alt)
    private WorldCoordinate launchSite = new WorldCoordinate(28.6, -80.6, 627); //EDIT CORDS HERE


    //Wind Speed
    private double averageWindSpeed;
    private double windSpeedDeviation;
    private double windDirection;
    /**
     * 0 = north, pi/2 = east, pi = south, 3pi/2 = west
     */

    private double windTurbulence;
    private double[] altToWindMap;
    private double[] altToWindDirectionMap;

    //Path to CSV file with far wind speeds, file path depends on current directory, can check using System.getProperty("user.dir")
    private final String FARWINDSPEEDPATH = "core/src/main/java/info/openrocket/core/document/FARWeather/Far March Wind Speed.csv";
    private final String FARWINDDIRPATH = "core/src/main/java/info/openrocket/core/document/FARWeather/FAR March Wind Direction .csv";

    private final String FARWINDDIRCONTPATH = "core/src/main/java/info/openrocket/core/document/FARWeather/Far March Wind Direction Continuous";

    //Used for Variable Wind vs Altitude, collected from HERBIE python library using RAP model.
    private final String FAR_EAST_WIND = "core/src/main/java/info/openrocket/core/document/FARWeather/final_combined_east.csv";
    private final String FAR_NORTH_WIND = "core/src/main/java/info/openrocket/core/document/FARWeather/final_combined_north.csv";


    private PinkNoiseWindModel windModel;


    /* Whether to calculate additional data or only primary simulation figures */
    private int randomSeed;

    //ROCKET VARS

    private ArrayList<RecoveryDevice> recoveryDevices;
    private double deploymentTimeDrogue;



    //Monte Carlo Sim variables
    private MonteCarloDistribution distribution;

    int iterations = 5;
    int iterNumber = 0; //the current sim iteration number, from 0 - iterations
    private double[] windSpeedSamples;
    private double[] windDirectionSamples;

    //Used for variable wind

    private double [][] windAltEastSamples;
    private double [][] windAltNorthSamples;

    private double timeToApogee = 69;
//    private double mainDeployTime  = 272;
    private double [] drogueDeploymentTimes;
    private double drogueLowerBound = timeToApogee - 2;
    private double drogueUpperBound = timeToApogee +5 ;

    private double[] mainDeploymentAltitudes;
//    private double mainLowerBound = mainDeployTime - 3;
//    private double mainUpperBound = mainDeployTime + 3 ;


    private double burnTime = 24;

    /**
     * Constructor, sets up the simulationEngineClass, simulatorEngine (which actually runs the sim),
     * All SimulationCondition values, and some user set values for simulationConditions like wind speed
     */
    public DispersionAnalysis(SimulationConditions presetConditions) {

        //FUNDAMENTAL vars
        try {
            simulator = simulationEngineClass.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("Cannot instantiate simulator.", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access simulator instance?! BUG!", e);
        } catch (InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        conditions = presetConditions.clone();


        //DATA, values can change depending on how many iterations occur
        data = new Double[1000][15 + 1]; //plus 1 adds an extra row for index, 11 params, 9 output 6 input
        locData = new Double[3000][3];  //time, position north, position south

        //CHANGEABLE vars:

        //BASIC vars
        randomSeed = new Random().nextInt();

        //LAUNCH Vars
        launchRodLength = 1219;
        launchRodAngle = 0;
        launchRodDirection = 0;

        //WIND Vars
        averageWindSpeed = 0;
        windSpeedDeviation = 0;
        windDirection = 0;
        windTurbulence = 0;



        //initialize wind model, and set values
        windModel = new PinkNoiseWindModel(randomSeed);
        windModel.setAverage(averageWindSpeed);
        windModel.setStandardDeviation(windSpeedDeviation);
        windModel.setDirection(windDirection);
        windModel.setTurbulenceIntensity(windTurbulence);

        //ROCKET VARS
        deploymentTimeDrogue = 0;


        recoveryDevices = new ArrayList<>();
        // finds the recover devices using recursive search
        findRecoveryDevices(conditions.getSimulation().getRocket());

        //Monte carlo
        distribution = new MonteCarloDistribution();

    }

    //This determines the parameters to be looped over
    public void loopSim() throws SimulationException {
        determineValues(); //generates all samples that will be used later

        for (int i = 0; i <iterations; i++) {
            iterNumber = i; //keeps track of the iteration number

            windDirection = Math.toRadians(windDirectionSamples[i]); //sets new value for wind direction
            averageWindSpeed = windSpeedSamples[i];
            deploymentTimeDrogue = drogueDeploymentTimes[i];
//            mainDeployTime = mainDeploymentAltitudes[i];

            //assigns these new values into simulation conditions
            configSimConditions();
//            setWindModel(); //re-active for normal wind models
            //runs simulation with new conditions
            runSimulation();

            randomSeed = new Random().nextInt();

        }

        exportData(); //after data is collected, it is exported
    }

    /**
     * Using the monte carlo class, a list of samples equal to the iteration length is created
     * for wind speed, wind direction.
     */
    public void determineValues(){
        //Used for simple wind model
        windSpeedSamples = distribution.generateEmpiricalDistribution(FARWINDSPEEDPATH, iterations, 100);
        windDirectionSamples = distribution.generateEmpiricalDistribution(FARWINDDIRCONTPATH, iterations, 100);
        drogueDeploymentTimes = distribution.uniformDistribution(drogueLowerBound, drogueUpperBound, iterations);
//        mainDeploymentAltitudes =  distribution.uniformDistribution(mainLowerBound, mainUpperBound, iterations);

        //Used for variable wind vs. altitude model
        windAltEastSamples = distribution.generateParallelEmpiricalDistribution(FAR_EAST_WIND, iterations, 100);
        windAltNorthSamples = distribution.generateParallelEmpiricalDistribution(FAR_NORTH_WIND, iterations, 100);


    }

    //configures simulation conditions for single sim
    //TODO: Use debug to find values for variables in SimulationOptions.java
    //TODO: NOTE, USE Application.getpreferences() to retrieve the user's input!!
    public void configSimConditions() throws SimulationException {

        //Configures rocket
        configRocket();
        //ITERABLE PARAMETERS
        conditions.setRandomSeed(randomSeed);

        boolean variableWind = true; // boolean determines if variable wind model is used. Default is avg wind model
        if (variableWind) {
            setVariableWindModel(); //defines instance variable int[] wind
            //casts simulator to basic event simulator, and calls method to update wind model in engine to variable wind model
            ((BasicEventSimulationEngineDispersionAnalysis) simulator).setAltToWind(altToWindMap, altToWindDirectionMap,  distribution.getPressureIndexMap());
        } else {
            conditions.setWindModel(windModel); //assuming normal wind, just set wind model directly
            /**
             * Note, this is allowed because per simulation, wind speed is constant. Therefore, the conditions object doesn't
             need to change during a single simulation. However, for variable wind speed, the condition's average wind speed
             needs to change over the simulation, which requires editing it directly in the Basic Simulation engine, hence
             the different notations for doing variable wind vs. constant wind
             */
        }

    }

    //CONFIGURES ROCKET. Most important determines delay time of recovery devices
    public void configRocket() {
        //Gets  object and its type (Parachute, streamer, something)
        // Gets simulation -> rocket -> Sustainer -> Body (multiple, must pick correct) -> inner tube
        //NOTE: this sequence of .getChild(int n) is arbitrary, depends on what the rocket tree looks like (can figure this out
        //just by looking at the tree on the GUI, or using debug in intellij and looking down the inheritance.

        FlightConfigurationId fcid = conditions.getFlightConfigurationID();

        //one parachute, assume main
        if (recoveryDevices.size() ==1) {
//            recoveryDevices.get(0).getDeploymentConfigurations().get(fcid).setDeployDelay(mainDeployTime);

        }
        //Two parachutes, one main one drouge, need to figure out which is which
        else {
            //the main will be bigger. if .get(0) is bigger, then .get(0) is main
            if (((Parachute) recoveryDevices.get(0)).getDiameter() > ((Parachute) recoveryDevices.get(1)).getDiameter()) {
//                (recoveryDevices.get(0)).getDeploymentConfigurations().get(fcid).setDeployDelay(mainDeployTime);
                (recoveryDevices.get(1)).getDeploymentConfigurations().get(fcid).setDeployDelay(deploymentTimeDrogue);

            } else {
                (recoveryDevices.get(0)).getDeploymentConfigurations().get(fcid).setDeployDelay(deploymentTimeDrogue);
//                (recoveryDevices.get(1)).getDeploymentConfigurations().get(fcid).setDeployDelay(mainDeployTime);

            }
        }



    }

    public void findRecoveryDevices(RocketComponent r){
        //base case 1: it is a recovery device
        if (r instanceof RecoveryDevice){
            recoveryDevices.add((RecoveryDevice) r);
            return;
        }
        //base case 2: it is null
        if (r == null){
            return;
        }
        for (int i =0; i < r.getChildCount(); i++){
            findRecoveryDevices(r.getChild(i));
        }

    }

    //Runs one single simulation
    public void runSimulation() throws SimulationException {
        simulator.simulate(conditions); //outputs flight vars, but not the important ones, also changes simulator
        flightSummary = simulator.getFlightData();
        saveData();
    }

    //saves data into dataTable
    public void saveData() {
        System.out.println(index); //user can see iteration number on console

        data[index][0] = index / 1.0;  //saves index, /1.0 casts to double
        data[index][1] = averageWindSpeed;
        data[index][2] = windSpeedDeviation;
        data[index][3] = Math.toDegrees(windDirection);
        data[index][4] = windTurbulence;
        data[index][5] = deploymentTimeDrogue;
//        data[index][6] = mainDeployTime;

        //Full flight data contains all positions, velocities, accelerations, etc. during a flight
        ArrayList<SimulationStatus> allflightData = ((BasicEventSimulationEngineDispersionAnalysis) simulator).getAllStatus();
        //We only care about final position, so get only position in last entry of flight data
        data[index][7] = flightSummary.getFlightTime();
        data[index][8] = allflightData.get(allflightData.size() - 1).getRocketPosition().x;
        data[index][9] = allflightData.get(allflightData.size() - 1).getRocketPosition().y;
        data[index][10] = allflightData.get(allflightData.size() - 1).getRocketWorldPosition().getAltitude();
        data[index][11] = allflightData.get(allflightData.size() - 1).getRocketWorldPosition().getLongitudeDeg();
        data[index][12] = allflightData.get(allflightData.size() - 1).getRocketWorldPosition().getLatitudeDeg();
        data[index][13] = flightSummary.getMaxAltitude();
        data[index][14] = flightSummary.getDeploymentVelocity();
        data[index][15] = flightSummary.getGroundHitVelocity();
        index++;


    }

    public void exportData() {
        Object[] columnNames = {"number", "avg Wind speed", "windSpeed standard dev", "wind direction", "wind Turbulence",
                "deploymentTime Drogue", "deployment Main Time","sim time", "positionEast of Launch (m)", "position North of Launch (m)",
                "altitude(m)", "Longtitude",  "Latitude", "Max altitude", "deployment Velocity", "groundSpeedVelocity"};

        //suspicious, could cause problem cuz wrapping in double, not primitive type double
        JTable tableData = new JTable(data, columnNames);
        tableData.setBounds(30, 40, 200, 300);

        //Sets up table details
        JFrame frame = new JFrame();

        // Frame Title
        frame.setTitle("Sim data");

        // adding it to JScrollPane
        JScrollPane sp = new JScrollPane(tableData);
        frame.add(sp);
        // Frame Size
        frame.setSize(500, 200);
        // Frame Visible = true
        frame.setVisible(true);
    }


    //Streamlines setting up the wind model, avoids a lot of needless lines of code doing
    //windModel.setAverage(windModel.getAverage+=.5);
    public void setWindModel() {
        windModel.setAverage(averageWindSpeed);
        windModel.setStandardDeviation(windSpeedDeviation);
        windModel.setDirection(windDirection);
        windModel.setTurbulenceIntensity(windTurbulence);
    }

    //makes the array which represents the map from altitude to wind speed, used if variable wind speed mode is desired
    //[1000,975,950,925,900,875,850,825,800,775,750,725,700,675,650,625,600,575,550,525,500,475,450,425,400,375,350,325,300,275,250,225,200,175,150,125,100]
    //map from index to corresponding pressure
    public void setVariableWindModel() {
        int numPressureReadings = windAltEastSamples.length;

        altToWindMap = new double [numPressureReadings]; //one snapshot of the samples, used for one single run, shows wind speed vs. pressure
        altToWindDirectionMap = new double [numPressureReadings];

        for (int i = 0; i < numPressureReadings; i++) {
            double x = windAltEastSamples[i][iterNumber];
            double y = windAltNorthSamples[i][iterNumber];

            altToWindMap[i] = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2)) ; //pythagorean theorem

            if (x<0){
                double conventional_angle = Math.atan(y/x)+Math.PI;
                altToWindDirectionMap[i] = ((Math.PI/2-conventional_angle)+Math.PI)% (2*Math.PI);
            } else {
                double conventional_angle = Math.atan(y/x);
                altToWindDirectionMap[i] = ((Math.PI/2-conventional_angle)+Math.PI)% (2*Math.PI); //converting to open rocket convention
            }
            //First, change angle relative to north clockwise. Then, swap to go other direction since wind in open rocket
            //is using the FROM convention, and the data is in the TO convention
        }
//        System.out.println("east is " + Arrays.toString(windAltEastSamples[0]));
//        System.out.println("North is is " + Arrays.toString(windAltNorthSamples[0]));
        System.out.println("magnitude is " + Arrays.toString(altToWindMap));
//        System.out.println("Direction is " + Arrays.toString(altToWindDirectionMap));



    }

    //NEW CODE FOR HEAVYBULB2 THRUST CURVE exploration
    public void thrustCurveIteration() throws SimulationException, IOException {
//
//        //Input parameters
        double totalImpulse = 200000;


        List<Double> thrust = new ArrayList<>(Arrays.asList(totalImpulse/burnTime,totalImpulse/burnTime));
        List<Double> time = new ArrayList<>(Arrays.asList(0.0, burnTime));

        // Note: RASPMotorLoader.createRASPMotor is now private, so we use the Builder directly
        // Note: The motor API has changed - setPropellantWeight/setTotalWeight no longer exist
        // Using setInitialMass instead
        double [] delay = {Double.MAX_VALUE};
        ThrustCurveMotor.Builder builder = new ThrustCurveMotor.Builder()
                .setManufacturer(Manufacturer.getManufacturer("seb"))
                .setDesignation("")
                .setDescription("")
                .setMotorType(info.openrocket.core.motor.Motor.Type.UNKNOWN)
                .setDiameter(4.0)
                .setLength(0.2)
                .setStandardDelays(delay)
                .setInitialMass(115.000001)
                .setTimePoints(time.stream().mapToDouble(Double::doubleValue).toArray())
                .setThrustPoints(thrust.stream().mapToDouble(Double::doubleValue).toArray());

        ThrustCurveMotor newMotor = builder.build();

        MotorConfiguration selectedMotor = conditions.getRocket().getSelectedConfiguration().getActiveMotors().iterator().next();
        selectedMotor.setMotor(newMotor);

        loopSim();


    }


}
