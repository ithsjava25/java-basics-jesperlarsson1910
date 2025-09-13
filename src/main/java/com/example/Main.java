package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();
    }

    //get the list of prices for today in specified area
    private static List<ElpriserAPI.Elpris> priceToday (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon) {
        return  elpriserAPI.getPriser(LocalDate.now(), zon);
    }

    //get the list of prices for tomorrow in specified area
    private static List<ElpriserAPI.Elpris> priceTomorrow (ElpriserAPI elpriserAPI, ElpriserAPI.Prisklass zon) {
        return elpriserAPI.getPriser(LocalDate.now().plusDays(1), zon);
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
        for  (int i = 1; i<elpriser.size()-duration; i++) {                         //iterate all possible windows, stop when the first value is the last avilble for the desired window
            if (meanPrice(window) < meanPrice(elpriser.subList(i,i+duration-1))){   //itarate possible windows, if it is cheaper set it as the return value
                window = elpriser.subList(i,i+duration-1);
            }
        }
        return window;
    }
}
