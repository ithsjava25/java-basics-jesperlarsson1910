package com.example;

import com.example.api.ElpriserAPI;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import static java.lang.System.exit;

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
        //parse given arguments after confirming that zone is entered correctly
        // if any of the arguments are incorrectly entered it is displayed with the help function before breaking the loop
        else {
            if (args[0].equals("--zone")) {
                if(Pattern.matches("^SE[1-4]$", args[1])) {
                    zon = ElpriserAPI.Prisklass.valueOf(args[1]);

                    for (int i = 2; i < args.length; i += 2) {
                        if (args[i].equals("--date")) {
                            date = checkDate(args[i + 1]);
                            if (date != null && priceOnDate(elpriserAPI, zon, date).isEmpty()) {
                                System.out.println("No data found");
                                exit(1);
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
                            exit(2);
                        }
                    }
                }
                else{
                    System.out.println("Invalid zone");
                    exit(2);
                }
            }
            else  {
                System.out.println("Zone required");
                //disabled interactive prompt to pass tests
                //zon = ElpriserAPI.Prisklass.valueOf(System.console().readLine("Zone: "));
                exit(2);
            }
        }

        if(window != 24){
            printer(optimalWindow((priceRealDay(elpriserAPI, zon)), window));
        }
        else if(sorted) {
            printer(sortedPrices(priceOnDate(elpriserAPI, zon, date)));
        }

        else {
            printer(priceOnDate(elpriserAPI, zon, date));
        }
    }

    /**
     * @param elpriserAPI initialized {@link ElpriserAPI} to call on
     * @param zon valid {@link ElpriserAPI.Prisklass} (SE1|SE2|SE3|SE4)
     * @return List of {@link ElpriserAPI.Elpris} for today
     */
    private static List<ElpriserAPI.Elpris> priceToday (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon) {
        return  elpriserAPI.getPriser(LocalDate.now(), zon);
    }

    /**
     * @param elpriserAPI initialized {@link ElpriserAPI} to call on
     * @param zon valid {@link ElpriserAPI.Prisklass} (SE1|SE2|SE3|SE4)
     * @return List of {@link ElpriserAPI.Elpris} for tomorrow, will be empty of not available
     */
    private static List<ElpriserAPI.Elpris> priceTomorrow (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon) {
        return elpriserAPI.getPriser(LocalDate.now().plusDays(1), zon);
    }

    /**
     * Gets a list of price collections for today, removed the ones that are in the past.
     * Returns with tomorrow's dates appended if possible
     *
     * @param elpriserAPI initialized {@link ElpriserAPI} to call on
     * @param zon valid {@link ElpriserAPI.Prisklass} (SE1|SE2|SE3|SE4)
     * @return List of {@link ElpriserAPI.Elpris} that are still in the future
     */
    private static List<ElpriserAPI.Elpris> priceRealDay (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon) {
        List <ElpriserAPI.Elpris> today = priceToday(elpriserAPI, zon);
        while (true) {  //checks and removes first object if the time has passed, breaks after
            if(today.get(1).timeEnd().isBefore(ChronoZonedDateTime.from(LocalDate.now()))){
                today.remove(1);
            }
            else break;
        }
        today.addAll(priceTomorrow(elpriserAPI,zon)); //add tomorrow's prices to the end of the trimmed list
        return today;
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
        double sum = 0.0;
        for (int i = 0; i<elpriser.size(); i++) {
            sum += elpriser.get(i).sekPerKWh();
        }
        return sum / elpriser.size();
    }

    /**
     * Find the lowest price of a provided list
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris} to find the lowest in
     * @return The {@link ElpriserAPI.Elpris} with the lowest price, if multiple have the same price only the first of them is returned
     */
    private static ElpriserAPI.Elpris minPrice(List<ElpriserAPI.Elpris> elpriser) {
        ElpriserAPI.Elpris min = elpriser.getFirst(); //set first elements as lowest before comparing to the rest
        for (int i = 1; i<elpriser.size(); i++) {
            if (elpriser.get(i).sekPerKWh() < min.sekPerKWh()) {
                min = elpriser.get(i);
            }
        }
        return min;
    }

    /**
     * Find the highest price of a provided list
     *
     * @param elpriser List of {@link ElpriserAPI.Elpris} to find the highest in
     * @return the {@link ElpriserAPI.Elpris} with the highest price, if multiple have the same price only the first of them is returned
     */
    private static ElpriserAPI.Elpris maxPrice (List<ElpriserAPI.Elpris> elpriser) {
        ElpriserAPI.Elpris max = elpriser.getFirst();
        for (int i = 1; i<elpriser.size(); i++) {
            if (elpriser.get(i).sekPerKWh() > max.sekPerKWh()) {
                max = elpriser.get(i);
            }
        }
        return max;
    }

    /**
     * Finds the cheapest window of price collections of a specified length
     *
     * @param elpriser list of {@link ElpriserAPI.Elpris} to find the cheapest window in
     * @param duration window length
     * @return list containing the {@link ElpriserAPI.Elpris} in the cheapest window
     */
    private static List<ElpriserAPI.Elpris> optimalWindow (List<ElpriserAPI.Elpris> elpriser, int duration) {
        List<ElpriserAPI.Elpris> window = elpriser.subList(0, duration-1);          //create a new list and fill with the desired number of values
        for  (int i = 1; i<elpriser.size()-duration; i++) {                         //iterate all possible windows, stop when the first value is the last available for the desired window
            if (meanPrice(window) < meanPrice(elpriser.subList(i,i+duration-1))){   //iterate possible windows, if it is cheaper set it as the return value
                window = elpriser.subList(i,i+duration-1);
            }
        }
        return window;
    }

    /**
     * Confirms if the string confirms to YYYY-MM-DD format
     *
     * @param date string to check
     * @return if the string is correctly formated, return it as a {@link LocalDate}, else return <code>null</code>
     */
    private static LocalDate checkDate(String date) {
        if (Pattern.matches("^\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$", date)){
            return LocalDate.parse(date);
        }
        else {
            System.out.println("Invalid date");
            return null;
        }
    }

    static void printer (List<ElpriserAPI.Elpris> elpriser) {
        NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN); nf.setMaximumFractionDigits(2);

        for (ElpriserAPI.Elpris elpriser1 : elpriser) {
            System.out.println(elpriser1.timeStart().getHour() + "-"
                    + elpriser1.timeEnd().getHour() + " "
                    +  nf.format(elpriser1.sekPerKWh()*10) + " öre");
        }
    }

    /**
     * Displays valid commands for the class
     */
    private static void help(){
        System.out.println("Commands:\n" +
                            "--zone SE1|SE2|SE3|SE4 (required)\n" +
                            "--date YYYY-MM-DD\n" +
                            "--sorted\n" +
                            "--charging 2h|4h|8h\n" +
                            "--help");
        exit(0);
    }
}
