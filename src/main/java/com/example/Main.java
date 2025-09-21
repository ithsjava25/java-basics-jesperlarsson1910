package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {

        ElpriserAPI elpriserAPI = new ElpriserAPI();
        ElpriserAPI.Prisklass zon = null;
        //set some default values
        LocalDate date = LocalDate.now();
        boolean sorted = false;
        int window = 24;

        //prompt for zone if no arguments were entered
        if (args.length < 2) {
            zon = ElpriserAPI.Prisklass.valueOf(System.console().readLine("Zon: "));
        }
        //parse given arguments. if any of the arguments are incorrectly entered it is displayed with the help function before breaking the loop
        else {
            for (int i = 0; i < args.length; i+=2) {
                if (args[i].equals("--zone") && Pattern.matches("^SE[1-4]$", args[i+1])) {
                    zon = ElpriserAPI.Prisklass.valueOf(args[i+1]);
                }
                else if (args[i].equals("--date") && Pattern.matches("^\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$", args[i+1])) {
                    date = LocalDate.parse(args[i+1]);
                }
                else if (args[i].equals("--charging") && Pattern.matches("^[248]h$", args[i+1])) {
                    window = Integer.parseInt(String.valueOf(args[i+1].charAt(0)));
                }
                else if (args[i].equals("--sorted")) {
                    sorted = true;
                }
                else if (args[0].equals("--help")) {
                    help();
                }
                else{
                    System.out.println("Invalid argument" + args[i]);
                    help();
                    break;
                }
            }
        }
    }

    //get the list of prices for today in specified area
    private static List<ElpriserAPI.Elpris> priceToday (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon) {
        return  elpriserAPI.getPriser(LocalDate.now(), zon);
    }

    //get the list of prices for tomorrow in specified area
    private static List<ElpriserAPI.Elpris> priceTomorrow (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon) {
        return elpriserAPI.getPriser(LocalDate.now().plusDays(1), zon);
    }

    //if possible create list of prices that are still upcoming
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

    private static List<ElpriserAPI.Elpris> priceOnDate (ElpriserAPI elpriserAPI,ElpriserAPI.Prisklass zon, LocalDate date) {
        return elpriserAPI.getPriser(date, zon);
    }

    //calculate and return the mean price for the provided list
    private static double meanPrice(List<ElpriserAPI.Elpris> elpriser) {
        double sum = 0.0;
        for (int i = 0; i<elpriser.size(); i++) {
            sum += elpriser.get(i).sekPerKWh();
        }
        return sum / elpriser.size();
    }

    //find and return element with lowest price in provided list
    //sets lowest price to the first element in the list and then compares the rest
    //if multiple elements have the lowest price
    private static ElpriserAPI.Elpris minPrice(List<ElpriserAPI.Elpris> elpriser) {
        ElpriserAPI.Elpris min = elpriser.getFirst();
        for (int i = 1; i<elpriser.size(); i++) {
            if (elpriser.get(i).sekPerKWh() < min.sekPerKWh()) {
                min = elpriser.get(i);
            }
        }
        return min;
    }

    //find and return element with highest price in provided list
    //sets highest price to the first element in the list and then compares the rest
    //if multiple elements have the highest price return the first of them
    private static ElpriserAPI.Elpris maxPrice (List<ElpriserAPI.Elpris> elpriser) {
        ElpriserAPI.Elpris max = elpriser.getFirst();
        for (int i = 1; i<elpriser.size(); i++) {
            if (elpriser.get(i).sekPerKWh() > max.sekPerKWh()) {
                max = elpriser.get(i);
            }
        }
        return max;
    }

    //find the cheapest window of the provided prices and duration
    private static List<ElpriserAPI.Elpris> optimalWindow (List<ElpriserAPI.Elpris> elpriser, int duration) {
        List<ElpriserAPI.Elpris> window = elpriser.subList(0, duration-1);          //create a new list and fill with the desired number of values
        for  (int i = 1; i<elpriser.size()-duration; i++) {                         //iterate all possible windows, stop when the first value is the last available for the desired window
            if (meanPrice(window) < meanPrice(elpriser.subList(i,i+duration-1))){   //iterate possible windows, if it is cheaper set it as the return value
                window = elpriser.subList(i,i+duration-1);
            }
        }
        return window;
    }

    private static void help(){
        System.out.println("Commands:\n" +
                            "--zone SE1|SE2|SE3|SE4 (required)\n" +
                            "--date YYYY-MM-DD\n" +
                            "--sorted\n" +
                            "--charging 2h|4h|8h\n" +
                            "--help");
    }
}
