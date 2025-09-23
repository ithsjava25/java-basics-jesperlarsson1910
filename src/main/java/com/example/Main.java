package com.example;

import com.example.api.ElpriserAPI;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {

        ElpriserAPI elpriserAPI = new ElpriserAPI();
        ElpriserAPI.Prisklass zon = null;

        //set some default values
        LocalDate date = LocalDate.now();
        int window = 24;
        boolean  sorted = false;

        //show usage if no arguments provided
        if (args.length == 0) {
            System.out.println("Usage: ");
            help();
        }
        //show help if prompted as only argument
        else if (args.length == 1 && args[0].equals("--help")) {
            help();
        }
        //parse given arguments after confirming that zone is entered correctly, then printing
        // if any of the arguments are incorrectly entered it is displayed with the help function before breaking the loop
        else {
            if (args[0].equals("--zone")) {
                if(Pattern.matches("^SE[1-4]$", args[1])) {
                    zon = ElpriserAPI.Prisklass.valueOf(args[1]);
                    boolean validArgs = true;

                    for (int i = 2; i < args.length; i += 2) {
                        if (args[i].equals("--date")) {
                            date = checkDate(args[i + 1]);
                            if (date != null && priceOnDate(elpriserAPI, zon, date).isEmpty()) {
                                System.out.println("No data found for " + args[i+1] + " defaulting to " + date);
                            }
                        }
                        else if (args[i].equals("--charging") && Pattern.matches("^[248]h$", args[i + 1])) {
                            window = Integer.parseInt(String.valueOf(args[i + 1].charAt(0)));
                        }
                        else if (args[i].equals("--sorted")) {
                            sorted = true;
                        }
                        else {
                            System.out.println("Invalid argument" + args[i]);
                            help();
                            validArgs = false;
                            break;
                        }
                    }

                    if(!priceOnDate(elpriserAPI, zon, date).isEmpty()) { //Ensure we have data
                        if (validArgs) { //ensure all args were valid

                            if (window != 24) { //if a valid charging window was requested print it
                                if (sorted) {
                                    printChargeStat(sortedPrices(optimalWindow(priceRealDay(elpriserAPI, zon, date), window)));
                                } else {
                                    printChargeStat(optimalWindow(priceRealDay(elpriserAPI, zon, date), window));
                                }

                            } else if (sorted) {
                                printStats(sortedPrices(priceOnDate(elpriserAPI, zon, date)));

                            } else {
                                printStats(priceOnDate(elpriserAPI, zon, date));
                            }
                        }
                    }
                    else {
                        System.out.println("No data");
                    }
                }
                else{
                    System.out.println("Invalid zone");

                }
            }
            else  {
                System.out.println("Zone required");
                //disabled interactive prompt to pass tests
                //zon = ElpriserAPI.Prisklass.valueOf(System.console().readLine("Zone: "));
            }
        }
    }

    /**
     * Get list of price collections for specified date
     *
     * @param elpriserAPI initialized {@link ElpriserAPI} to call on
     * @param zon valid {@link ElpriserAPI.Prisklass} (SE1|SE2|SE3|SE4)
     * @param date {@link LocalDate} we want prices for
     * @return List of {@link ElpriserAPI.Elpris} for specified {@link LocalDate}, will be empty if not available
     */
    private static List<ElpriserAPI.Elpris> priceOnDate (ElpriserAPI elpriserAPI,ElpriserAPI.Prisklass zon, LocalDate date) {
        return elpriserAPI.getPriser(date, zon);
    }

    /**
     * Gets a list of price collections for today, removed the ones that are in the past.
     * Returns with tomorrow's dates appended if possible
     *
     * @param elpriserAPI initialized {@link ElpriserAPI} to call on
     * @param zon valid {@link ElpriserAPI.Prisklass} (SE1|SE2|SE3|SE4)
     * @return List of {@link ElpriserAPI.Elpris} that are still in the future
     */
    private static List<ElpriserAPI.Elpris> priceRealDay (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon, LocalDate date) {
        List <ElpriserAPI.Elpris> today = priceOnDate(elpriserAPI, zon, date);
        while (!today.isEmpty()&&date.equals(LocalDate.now())) {  //checks and removes first object if the time has passed, breaks after
            if(today.getFirst().timeEnd().isBefore(ZonedDateTime.now())) {
                today.removeFirst();
            }
            else break;
        }
        //add future prices or empty list to the end of the trimmed list
        List<ElpriserAPI.Elpris> tomorrow = priceOnDate(elpriserAPI, zon, (date.plusDays(1)));
        today.addAll(tomorrow);
        return  today;
    }
    

    /**
     * Sorts provided list based on price in SEK
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris}
     */
    private static List<ElpriserAPI.Elpris> sortedPrices(List<ElpriserAPI.Elpris> elpriser){
        elpriser.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh));
        return elpriser;
    }

    /**
     * Calculates the mean price for a list of prices
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris}
     * @return The mean price in SEK for the provided list
     */
    private static double meanPrice(List<ElpriserAPI.Elpris> elpriser) {
        if(!elpriser.isEmpty()) {
            double sum = 0.0;
            for (int i = 0; i < elpriser.size(); i++) {
                sum += elpriser.get(i).sekPerKWh();
            }
            return sum / elpriser.size();
        }
        return 0.0;
    }

    /**
     * Find the lowest price of a provided list
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris} to find the lowest in
     * @return The {@link ElpriserAPI.Elpris} with the lowest price, if multiple have the same price only the first of them is returned
     */
    private static ElpriserAPI.Elpris minPrice(List<ElpriserAPI.Elpris> elpriser) {
        if(!elpriser.isEmpty()) {
            ElpriserAPI.Elpris min = elpriser.getFirst(); //set first elements as lowest before comparing to the rest
            for (int i = 1; i < elpriser.size(); i++) {
                if (elpriser.get(i).sekPerKWh() < min.sekPerKWh()) {
                    min = elpriser.get(i);
                }
            }
            return min;
        }
        return null;
    }

    /**
     * Find the highest price of a provided list
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris} to find the highest in
     * @return the {@link ElpriserAPI.Elpris} with the highest price, if multiple have the same price only the first of them is returned
     */
    private static ElpriserAPI.Elpris maxPrice (List<ElpriserAPI.Elpris> elpriser) {
        if(!elpriser.isEmpty()) {
            ElpriserAPI.Elpris max = elpriser.getFirst();
            for (int i = 1; i < elpriser.size(); i++) {
                if (elpriser.get(i).sekPerKWh() > max.sekPerKWh()) {
                    max = elpriser.get(i);
                }
            }
            return max;
        }
        return null;
    }

    /**
     * Finds the cheapest window of price collections of a specified length
     *
     * @param elpriser list of {@link ElpriserAPI.Elpris} to find the cheapest window in
     * @param duration window length
     * @return list containing the {@link ElpriserAPI.Elpris} in the cheapest window
     */
    private static List<ElpriserAPI.Elpris> optimalWindow (List<ElpriserAPI.Elpris> elpriser, int duration) {
        if(!elpriser.isEmpty() && elpriser.size() > duration) {
            List<ElpriserAPI.Elpris> window = elpriser.subList(0, duration);          //create a new list and fill with the desired number of values
            for (int i = 1; i < elpriser.size() - duration; i++) {                         //iterate all possible windows, stop when the first value is the last available for the desired window
                if (meanPrice(window) < meanPrice(elpriser.subList(i, i + duration))) {   //iterate possible windows, if it is cheaper set it as the return value
                    window = elpriser.subList(i, i + duration);
                }
            }
            return window;
        }
        else  {
            return elpriser;
        }
    }

    /**
     * Confirms if the string confirms to YYYY-MM-DD format
     *
     * @param date string to check
     * @return if the string is correctly formated, return it as a {@link LocalDate}, else return {@link LocalDate#now()}
     */
    private static LocalDate checkDate(String date) {
        if (Pattern.matches("^\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$", date)){
            return LocalDate.parse(date);
        }
        else {
            System.out.println("Invalid date");
            return LocalDate.now();
        }
    }

    /**
     * Prints a provided list of {@link ElpriserAPI.Elpris} formatted to "HH-HH"   "0,00 öre"
     * @param elpriser list to be printed
     */
    static void printList (List<ElpriserAPI.Elpris> elpriser) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH");
        NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN); nf.setMinimumFractionDigits(2); nf.setMaximumFractionDigits(2);

        for (ElpriserAPI.Elpris elpris : elpriser) {
            System.out.println(dtf.format(elpris.timeStart()) + "-"
                    + dtf.format(elpris.timeEnd()) + " "
                    +  nf.format(elpris.sekPerKWh()*100) + " öre");
        }
    }

    static void printMean (List<ElpriserAPI.Elpris> elpriser) {
        NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN); nf.setMaximumFractionDigits(2);
        System.out.println("Medelpris: " + nf.format(meanPrice(elpriser)*100) + " öre");
    }


    /**
     * Prints stats, time and cost for a provided {@link ElpriserAPI.Elpris} formatted to "HH-HH"   "0,00 öre"
     * @param elpriser list of objects to print from
     */
    static void printStats (List<ElpriserAPI.Elpris> elpriser) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH");
        NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN); nf.setMaximumFractionDigits(2);

        printList(elpriser);

        ElpriserAPI.Elpris output = minPrice(elpriser); //call once instead of checking for every output
        System.out.println("\nLägsta pris: " + dtf.format(output.timeStart()) + "-"
                + dtf.format(output.timeEnd()) + " "
                +  nf.format(output.sekPerKWh()*100) + " öre");

        output = maxPrice(elpriser);
        System.out.println("Högsta pris: " + dtf.format(output.timeStart()) + "-"
                + dtf.format(output.timeEnd()) + " "
                +  nf.format(output.sekPerKWh()*100) + " öre");

        printMean(elpriser);
    }

    /**
     * Prints info about a list {@link ElpriserAPI.Elpris} as a charging window
     * @param elpriser charging window as a list
     */
    static void printChargeStat (List<ElpriserAPI.Elpris> elpriser) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
        NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN); nf.setMaximumFractionDigits(2);

        if(!elpriser.isEmpty()) {
            ElpriserAPI.Elpris earliest = elpriser.getFirst();

            //the list might be sorted so we need to find the first chronological time to print
            for (int i = 1; i < elpriser.size(); i++) {
                if (earliest.timeStart().isBefore(elpriser.get(i).timeStart())) {
                    earliest = elpriser.get(i);
                }
            }


            System.out.println("Påbörja laddning: " + dtf.format(earliest.timeStart()));
            printMean(elpriser);
            System.out.println();//For formatting
            printList(elpriser);
        }
    }


    /**
     * Displays valid arguments
     */
    private static void help(){
        System.out.println("Commands:\n" +
                            "--zone SE1|SE2|SE3|SE4 (required)\n" +
                            "--date YYYY-MM-DD\n" +
                            "--sorted\n" +
                            "--charging 2h|4h|8h\n" +
                            "--help");
    }
}
