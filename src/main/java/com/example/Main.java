package com.example;

import com.example.api.ElpriserAPI;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class Main {

    private static ElpriserAPI elpriserAPI;

    private static ElpriserAPI.Prisklass zon;

    private static LocalDate date;
    private static int window;
    private static boolean  sorted;

    public static void main(String[] args) {

        elpriserAPI = new ElpriserAPI();

        //set default values
        date =  LocalDate.now();
        window = 24;
        sorted = false;

        //show usage if no arguments provided
        if (args.length == 0) {
            System.out.println("Usage: ");
            help();
        }
        //show help if prompted as only argument
        else if (args.length == 1 && args[0].equals("--help")) {
            help();
        }
        //parse given args and provide requested data if valid and available
        else {
            if(parseArgs(args)) { //parses the args and returns true if everything was valid
                outputResult();
            }
        }
    }

    /**
     * Get list of price collections for {@link #date}
     *
     * @return List of {@link ElpriserAPI.Elpris} for specified {@link LocalDate}, will be empty if not available
     */
    private static List<ElpriserAPI.Elpris> priceOnDate () {
            return elpriserAPI.getPriser(date, zon);
    }

    /**
     * Get list of price collections for next day after {@link #date}
     *
     * @return List of {@link ElpriserAPI.Elpris} for specified {@link LocalDate}, will be empty if not available
     */
    private static List<ElpriserAPI.Elpris> priceOnNextDate () {
        return elpriserAPI.getPriser(date.plusDays(1), zon);
    }

    /**
     * Gets a list of price collections for date, removed the ones that are in the past of the list is for today.
     * Returns with next days dates appended if possible
     *
     * @return List of {@link ElpriserAPI.Elpris} spanning over two days
     */
    private static List<ElpriserAPI.Elpris> priceRealDay () {
        List <ElpriserAPI.Elpris> today = priceOnDate();
        while (!today.isEmpty()&&date.equals(LocalDate.now())) {  //checks and removes first object if the time has passed, breaks after
            if(today.getFirst().timeEnd().isBefore(ZonedDateTime.now())) {
                today.removeFirst();
            }
            else break;
        }
        //add future prices or empty list to the end of the trimmed list
        List<ElpriserAPI.Elpris> tomorrow = priceOnNextDate();
        today.addAll(tomorrow);
        return  today;
    }

    /**
     * Combines a list with several {@link ElpriserAPI.Elpris} per hour into one with max 24 hours
     *
     * @param prices list of {@link ElpriserAPI.Elpris} to be combined
     * @return combined list
     */
    private static List<ElpriserAPI.Elpris> combineSameHour(List<ElpriserAPI.Elpris> prices) {
        if (prices.isEmpty()) return prices; //if the list is empty return it

        else {
            List<ElpriserAPI.Elpris> combined = new ArrayList<>();
            List<ElpriserAPI.Elpris> temp = new ArrayList<>();

            while(!prices.isEmpty()) { //iterate through the entire list
                //if there are no elements in the temp list or th temp list represents the same day add the element to it
                if(temp.isEmpty() || prices.getFirst().timeStart().getHour() == temp.getFirst().timeStart().getHour()){
                    temp.add(prices.getFirst());
                }
                //if first statement is false we combine the temp list to a single element and add it to the list before starting on the next day
                else{
                    combined.add(combine(temp));
                    temp.clear();
                    temp.add(prices.getFirst());
                }
                if(prices.size() == 1){ //if there is only a single element left we need to add temp and it before the loop ends
                    combined.add(combine(temp));
                    combined.add(prices.getFirst());
                }
                prices.removeFirst(); //removes element after we have handled it
            }
            return combined;
        }
    }

    /**
     * Combines a list of {@link ElpriserAPI.Elpris} into a single element
     *
     * @param temp list to combine
     * @return a single element with the price averaged from the list and the same start/end as the first object
     */
    private static ElpriserAPI.Elpris combine(List<ElpriserAPI.Elpris> temp) {
        return new ElpriserAPI.Elpris(
                meanPrice(temp),
                meanPriceEur(temp),
                temp.getFirst().exr(),
                temp.getFirst().timeStart(),
                temp.getFirst().timeEnd()
        );
    }

    /**
     * Sorts provided list by price or time
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris}
     * @param key what the list should be sorted by {@code PRICE} or {@code TIME}
     * @return sorted list, if incorrect key was provided list will be returned unsorted
     */
    private static List<ElpriserAPI.Elpris> sortedPrices(List<ElpriserAPI.Elpris> elpriser, String key ) {
        switch (key.toUpperCase()) {
            case "PRICE" -> elpriser.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh).reversed());
            case "TIME" -> elpriser.sort(Comparator.comparing(ElpriserAPI.Elpris::timeStart));

            default -> System.out.println("Invalid key");
        }
        return elpriser;
    }

    /**
     * Calculates the mean price for a list of {@link ElpriserAPI.Elpris} in SEK
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
     * Calculates the mean price for a list of {@link ElpriserAPI.Elpris} in EUR
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris}
     * @return The mean price in EUR for the provided list
     */
    private static double meanPriceEur(List<ElpriserAPI.Elpris> elpriser) {
        if(!elpriser.isEmpty()) {
            double sum = 0.0;
            for (int i = 0; i < elpriser.size(); i++) {
                sum += elpriser.get(i).eurPerKWh();
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
     * Finds the cheapest window {@link ElpriserAPI.Elpris} of a specified length
     *
     * @param elpriser list of {@link ElpriserAPI.Elpris} to find the cheapest window in
     * @param duration window length
     * @return list containing the {@link ElpriserAPI.Elpris} in the cheapest window
     */
    private static List<ElpriserAPI.Elpris> optimalWindow (List<ElpriserAPI.Elpris> elpriser, int duration) {
        if(!elpriser.isEmpty() && elpriser.size() > duration) {
            List<ElpriserAPI.Elpris> window = elpriser.subList(0, duration);          //create a new list and fill with the desired number of values
            for (int i = 1; i <= elpriser.size() - duration; i++) {                         //iterate all possible windows, stop when the first value is the last available for the desired window
                if (meanPrice(window) > meanPrice(elpriser.subList(i, i + duration))) {   //iterate possible windows, if it is cheaper set it as the return value
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
     * Formats a double to a string 100x the value with the pattern "0.00"
     *
     * @param price {@link Double} value to be formatted
     * @return formatted value as a {@link String}
     */
    private static String formatPrice(double price) {
        DecimalFormatSymbols dfs = new DecimalFormatSymbols(new Locale("sv", "SE"));
        DecimalFormat df = new DecimalFormat("0.00", dfs);

        return df.format(price*100);
    }

    /**
     * Formats a ZoneDateTime to a string with pattern "HH"
     *
     * @param time {@link ZonedDateTime} to be formatted
     * @return formatted value as a {@link String}
     */
    private static String formatTime(ZonedDateTime time) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH");

        return dtf.format(time);
    }

    /**
     * Prints a provided list of {@link ElpriserAPI.Elpris} formatted to "HH-HH" "0,00 öre"
     *
     * @param elpriser list to be printed
     */
    static void printList (List<ElpriserAPI.Elpris> elpriser) {


        for (ElpriserAPI.Elpris elpris : elpriser) {
            System.out.println(formatTime(elpris.timeStart()) + "-"
                    + formatTime(elpris.timeEnd()) + " "
                    +  formatPrice(elpris.sekPerKWh()) + " öre");
        }
    }

    /**
     * Prints a provided list of {@link ElpriserAPI.Elpris} formatted to "Medelpris: 0.00 öre"
     *
     * @param elpriser list to print mean from
     * @param chargingWindow {@code TRUE} if the list is representing a charging window, changing the formatting to "Medelpris för fönster: 0.00 öre"
     */
    static void printMean (List<ElpriserAPI.Elpris> elpriser, boolean chargingWindow) {

        if (chargingWindow) {
            System.out.println("Medelpris för fönster: " + formatPrice(meanPrice(elpriser)) + " öre");
        }
        else {
            System.out.println("Medelpris: " + formatPrice(meanPrice(elpriser)) + " öre");
        }
    }


    /**
     * Prints stats, time and cost for a provided list of {@link ElpriserAPI.Elpris}
     *
     * @param elpriser list of {@link ElpriserAPI.Elpris} to print from
     */
    static void printStats (List<ElpriserAPI.Elpris> elpriser) {

        printList(elpriser);

        ElpriserAPI.Elpris output = minPrice(elpriser); //call once instead of checking for every output
        System.out.println("\nLägsta pris: " + formatTime(output.timeStart()) + "-"
                + formatTime(output.timeEnd()) + " "
                +  formatPrice(output.sekPerKWh()) + " öre");

        output = maxPrice(elpriser);
        System.out.println("Högsta pris: " + formatTime(output.timeStart()) + "-"
                + formatTime(output.timeEnd()) + " "
                +  formatPrice(output.sekPerKWh()) + " öre");

        printMean(elpriser, false);
    }

    /**
     * Similar to {@link #printStats(List)} but for lists of combined prices {@link #combineSameHour(List)}
     *
     * @param elpriser list of {@link ElpriserAPI.Elpris} to print from
     */
    static void printCombined (List<ElpriserAPI.Elpris> elpriser) {
        printStats(elpriser); //print stats for all elements first
        System.out.println();//for formatting

        elpriser = combineSameHour(elpriser); //combine the list before printing stats
        printStats(elpriser);

        printMean((elpriser), false);

    }

    /**
     * Prints info about a list {@link ElpriserAPI.Elpris} as a charging window
     *
     * @param elpriser charging window as a list
     */
    private static void printChargeStat (List<ElpriserAPI.Elpris> elpriser) {

        if(!elpriser.isEmpty()) {
            //find earliest if the list is sorted by price
            ElpriserAPI.Elpris earliest = sortedPrices(elpriser, "time").getFirst();


            System.out.println("Påbörja laddning: kl " + formatTime(earliest.timeStart()) + ":00");
            printMean(elpriser, true);
            System.out.println();//For formatting
            printList(elpriser);
        }
    }

    /**
     * parses input arguments and sets flags to provide desired output with {@link #outputResult()}
     *
     * @param args array with argumemts per {@link #help()}
     * @return {@code TRUE} if arguments were parsed correctly, {@code FALSE} if something failed
     */
    private static boolean parseArgs(String[] args) {
        if (args[0].equals("--zone")) { //First argument should always be a valid zone

            if(Pattern.matches("^SE[1-4]$", args[1])) {
                zon = ElpriserAPI.Prisklass.valueOf(args[1]);
                for (int i = 2; i < args.length; i += 2) { //iterate other arg, allows for them to be any order

                    if (args[i].equals("--date")) {
                        date = checkDate(args[i + 1]); //set date to provided string if correctly formatted
                        if (priceOnDate().isEmpty()) { //if data for the desired date is unavailable we fall back to today
                            date = LocalDate.now();
                            System.out.println("No data found for " + args[i+1] + " defaulting to " + date);
                        }
                    }

                    else if (args[i].equals("--charging") && Pattern.matches("^[248]h$", args[i + 1])) {
                        window = Integer.parseInt(String.valueOf(args[i + 1].charAt(0)));
                    }

                    else if (args[i].equals("--sorted")) {
                        sorted = true;
                        i--; //since args isn't a pair and the loop iterates by two
                    }

                    else {
                        System.out.println("Invalid argument" + args[i]);
                        help();
                        return false;
                    }
                }
            }
            else{
                System.out.println("Invalid zone");
                return false;
            }
        }
        else  {
            System.out.println("Zone required");
            //disabled interactive prompt to pass tests
            //zon = ElpriserAPI.Prisklass.valueOf(System.console().readLine("Zone: "));
            return false;
        }
        return true;
    }

    /**
     * Prints stats based on flags set with {@link #parseArgs(String[])}
     */
    private static void outputResult (){
        if(priceOnDate().isEmpty()) { //ensure there is data
            System.out.println("No data");
        }
        else {
            if (window != 24) { //if a valid charging window was requested
                if (sorted) {
                    printChargeStat(sortedPrices(optimalWindow(priceRealDay(), window), "price")); //sorted by price
                } else { printChargeStat(optimalWindow(priceRealDay(), window)); //default sorting
                }

            }

            else { //if no window was requested print stats for the whole day
                if (sorted) { //sorted by price
                    if (priceOnDate().size() <= 24) {
                        printStats(sortedPrices(priceRealDay(), "price"));
                    } else printCombined(sortedPrices(priceRealDay(), "price"));
                } else { //default sorting
                    if (priceOnDate().size() <= 24) {
                        printStats(priceRealDay());
                    } else {
                        printCombined(priceRealDay());
                    }
                }
            }
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
