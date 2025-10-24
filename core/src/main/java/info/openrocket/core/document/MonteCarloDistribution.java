package info.openrocket.core.document;

import info.openrocket.core.util.MathUtil;

//Math Imports
import org.apache.commons.math4.legacy.distribution.AbstractIntegerDistribution;
import org.apache.commons.math4.legacy.distribution.EmpiricalDistribution;
//import java.lang.Math;
import org.apache.commons.math4.legacy.distribution.AbstractIntegerDistribution;
import org.apache.commons.math4.legacy.distribution.EmpiricalDistribution;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.*;

//File imports
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;



/**
 * Utalizes the https://commons.apache.org/math/download_math.cgi
 * And apache math: https://dlcdn.apache.org/commons/statistics/binaries/
 * Apache Numbers: https://commons.apache.org/numbers/download_numbers.cgi
 * Apache Rng: https://commons.apache.org/rng/download_rng.cgi
 * Apache numbers: https://commons.apache.org/numbers/download_numbers.cgi
 * And these to project structure
 */

/**
 * Analyzed publicly available weather data patterns from FAR. for the months of march from 2023- 2014 (march
 * since thats the mont of the HAD launch date). This
 */


public class MonteCarloDistribution {

    private final String COMMA_DELIMITER = ",";

    private double [] pressureIndexMap;

    private  EmpiricalDistribution FARMarchWindDirection;
    private EmpiricalDistribution FARMarchWindSpeed;


    public MonteCarloDistribution(){}

    public double[] uniformDistribution(double upperBound, double lowerBound, int numSamples){
        double [] samples = new double[numSamples];
        for (int i = 0; i < numSamples; i++){
            samples[i] = Math.random()*(upperBound-lowerBound)+lowerBound;

        }
        return samples;
    }



    /**
     * Using Apache commons built in empirical distribution library, create an empirical distribution
     * for Far wind speeds and distribution based on publically available wind data
     * @param filePath
     * @param numSamples
     */
    public double [] generateEmpiricalDistribution(String filePath, int numSamples, int numBins ) {

        double [] windData = readCSV(filePath);

        EmpiricalDistribution empiricalDist = EmpiricalDistribution.from(numBins, windData);

        RandomSource randomSource = RandomSource.MT; // Create a random source
        ContinuousDistribution.Sampler sampler = empiricalDist.createSampler(randomSource.create()); // Create a sampler

        //System.out.println("empirical distribution mean is "+ empiricalDist.getMean());

        return empiricalDist.sample(numSamples, sampler); // Sample 10 values

    }

    //Used in wind vs altitude, takes in a 2d csv file and generates samples  at each pressure value.
    //TODO, assumes that wind speeds are UNRELATED at altitudes. This, is rather questionable, would likely even out final results
    public double [][] generateParallelEmpiricalDistribution(String filePath, int numSamples, int numBins ) {

        double[][] windData = readParallelCSV(filePath);
        windData = transposeMatrix(windData);

        //This will contain all the samples for each altitude, -1 since first column represents pressures
        double windSamples[][] = new double[windData.length][numSamples];

        RandomSource randomSource = RandomSource.MT; // Create a random source
        for (int i = 0; i < windData.length; i++){
            EmpiricalDistribution empiricalDist = EmpiricalDistribution.from(numBins, windData[i]);
            ContinuousDistribution.Sampler sampler = empiricalDist.createSampler(randomSource.create()); // Create a sampler
            windSamples[i] = empiricalDist.sample(numSamples, sampler);
        }


        return windSamples;

    }

    /**
     * Used to read in wind CSV data
     * @param filePath
     */
    public double [] readCSV (String filePath) {
        ArrayList<Double> data = new ArrayList<>();

        //Reads in data from csv as double
        try (Scanner scanner = new Scanner(new File(filePath))) {
          while (scanner.hasNextLine()) {
                data.add(scanner.nextDouble());
            }
        }catch (FileNotFoundException er) {
            System.out.println("ERROR in Monte Carlo Reading CSV File " + er.toString());
        }
        return data.stream().mapToDouble(Double::doubleValue).toArray(); //returns data as double array
    }

    /**
     * Used for reading CSV's for the 2D csv files representing Wind vs altitude.
     * @param filePath
     * @return
     */
    public double [][] readParallelCSV (String filePath) {
        List<List<String>> records = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(filePath))) {
            while (scanner.hasNextLine()) {
                records.add(getRecordFromLine(scanner.nextLine()));
            }
        } catch (FileNotFoundException er) {
            System.out.println("ERROR in Monte Carlo Reading CSV File " + er.toString());
        }


        double [][] numeric_records = new double[records.size()][records.get(0).size()];
        int count = 0;
        pressureIndexMap = records.get(0).stream().mapToDouble(Double::parseDouble).toArray(); //Creates the pressure index map used by the simualtion engine
        records.remove(0); //removes pressure variable;

        for (List<String> record : records) {
            //converts line of record to double and then to an array.
            double [] numeric_record = record.stream().mapToDouble(Double::parseDouble).toArray();
            numeric_records[count] = numeric_record;
            count++;
        }
        return numeric_records;
    }

    /**
     * Helper method used to get single line for the readParrallelCSV method
     * @param line
     * @return
     */
    private List<String> getRecordFromLine(String line) {
        List<String> values = new ArrayList<String>();
        try (Scanner rowScanner = new Scanner(line)) {
            rowScanner.useDelimiter(COMMA_DELIMITER);
            while (rowScanner.hasNext()) {
                values.add(rowScanner.next());
            }
        }
        return values;
    }

    /**
     * USed to transpose the 2D wind matrix to make empirical distributions easier
     * @param matrix
     * @return
     */
    public double[][] transposeMatrix(double[][] matrix){
        int m = matrix.length;
        int n = matrix[0].length;

        double[][] transposedMatrix = new double[n][m];

        for(int x = 0; x < n; x++) {
            for(int y = 0; y < m; y++) {
                transposedMatrix[x][y] = matrix[y][x];
            }
        }

        return transposedMatrix;
    }

    /**
     * Generates a reference map for Pressure VS. Index, used by simulation Engine Analysis
     */

    public double[] getPressureIndexMap(){
        return pressureIndexMap;
    }

    /**
         * Custom test to determine mean and variance of samples (although samples is noticiablly different,
         * I believe because EmpiricalDistribution uses a different formula to determine variance
         * @param samples
         */
    public void testDistributions(double[] samples){

        double total = 0;
        for (int i =0; i < samples.length; i ++){
            total += samples[i];
        }
        double mean = total/samples.length;
        double variance = 0;

        for (int i =0; i < samples.length; i ++){
            variance += Math.pow(samples[i] - 4.5, 2);
        }
        variance = variance/samples.length;

//        System.out.println("mean is " + mean);
//        System.out.println("variance is " + variance);
    }




}



