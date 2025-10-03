package com.example;

import com.example.api.ElpriserAPI;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        List<ElpriserAPI.Elpris> priceList = elpriserAPI.getPriser(date,zon);
        if (priceList.size() > 24){
            return combineSameHour(priceList);
        }
        else{
            return priceList;
        }
    }

    /**
     * Get list of price collections for next day after {@link #date}
     *
     * @return List of {@link ElpriserAPI.Elpris} for specified {@link LocalDate}, will be empty if not available
     */
    private static List<ElpriserAPI.Elpris> priceOnNextDate () {
        List<ElpriserAPI.Elpris>  priceList = elpriserAPI.getPriser(date.plusDays(1), zon);
        if (priceList.size() > 24){
            return combineSameHour(priceList);
        }
        else {
            return priceList;
        }
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
            Map<Integer, List<ElpriserAPI.Elpris>> groups = new TreeMap<>();
            for (ElpriserAPI.Elpris p : prices) {
                groups.computeIfAbsent(p.timeStart().getHour(), k -> new ArrayList<>()).add(p);
            }
            List<ElpriserAPI.Elpris> combined = new ArrayList<>();
            for (List<ElpriserAPI.Elpris> group : groups.values()) {
                combined.add(combine(group));
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
                temp.getLast().timeEnd()
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
            List<ElpriserAPI.Elpris> window = elpriser.subList(0, duration);//create a new list and fill with the desired number of values

            double minValue = 0; for(int k = 0; k < duration; k++) { minValue += elpriser.get(k).sekPerKWh();} //create and set value of first window to compare
            double sliding = minValue; //value that will change with every window

            for (int i = duration; i < elpriser.size(); i++) { //iterate all possible windows, stop when the first value is the last available for the desired window
                sliding += elpriser.get(i).sekPerKWh() - elpriser.get(i-duration).sekPerKWh();

                if (minValue > sliding) {   //if the checked window is lesser set it as the return value
                    minValue = sliding;
                    window = elpriser.subList(i-duration+1, i+1);
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
        try {
            return LocalDate.parse(date); }

        catch (DateTimeParseException e) {
            System.out.println("Invalid date");
            return LocalDate.now(); }
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

        if(sorted){sortedPrices(elpriser, "PRICE");}

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
     * Prints info about a list {@link ElpriserAPI.Elpris} as a charging window
     *
     * @param elpriser charging window as a list
     */
    private static void printChargeStat (List<ElpriserAPI.Elpris> elpriser) {

        if(!elpriser.isEmpty()) {
            System.out.println("Påbörja laddning: kl " + formatTime(elpriser.getFirst().timeStart()) + ":00");
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
        Map<String, String> argMap = new HashMap<>();

        try {
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "--zone", "--charging", "--date" -> argMap.put(args[i], args[i + 1]);
                    case "--sorted", "--help" -> {
                        argMap.put(args[i], "true");
                        i--;
                    }

                    default -> {
                        throw new IllegalArgumentException("Invalid argument: " + args[i]);
                    }
                }

            }
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return false;
        }

        if (argMap.containsKey("--zone") && Pattern.matches("^SE[1-4]$", argMap.get("--zone"))) {
                zon = ElpriserAPI.Prisklass.valueOf(argMap.get("--zone"));

            for (Map.Entry entry : argMap.entrySet()) {
                switch ((String) entry.getKey()) {
                    case "--date" -> {
                        date = checkDate(argMap.get("--date")); //set date to provided string if correctly formatted
                        if (elpriserAPI.getPriser(date, zon).isEmpty()) { //if data for the desired date is unavailable we fall back to today
                            date = LocalDate.now();
                            System.out.println("No data found for " + argMap.get("--date") + " defaulting to " + date);
                        }
                    }

                    case "--charging" -> {
                        if(Pattern.matches("^[248]h$", argMap.get("--charging"))) {
                            window = Integer.parseInt(String.valueOf(argMap.get("--charging").charAt(0)));
                        }
                        else {
                            System.out.println("Invalid charging window: " + argMap.get("--charging"));
                            return false;
                        }
                    }

                    case "--sorted" -> {
                        sorted = true;
                    }

                    case "--help" -> {
                        help();
                    }
                }
            }
        }
        else {
                System.out.println("Invalid zone");
                return false;
            }

        if (zon == null) {
            System.out.println("Zone required");
            //disabled interactive prompt to pass tests
            //zon = ElpriserAPI.Prisklass.valueOf(System.console().readLine("Zone: "));
            return false;
        }

        else {
            return true;
        }
    }

    /**
     * Prints stats based on flags set with {@link #parseArgs(String[])}
     */
    private static void outputResult (){
        if(elpriserAPI.getPriser(date,zon).isEmpty()) { //ensure there is data
            System.out.println("No data");
        }
        else {
            switch (window) {
                case 24 -> printStats(priceRealDay());
                default -> printChargeStat(optimalWindow(priceRealDay(), window));
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
