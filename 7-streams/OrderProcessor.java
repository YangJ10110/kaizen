import java.util.*;
import java.util.function.Consumer;
import java.util.stream.*;

public class OrderProcessor {
    record Order(String id, double amount, boolean isRush) {}

    public static void main(String[] args) {
        List<Order> orders = List.of(
            new Order("O-1001", 250.00, true),
            new Order("O-1002", 40.00, false),
            new Order("O-1003", 999.99, true),
            new Order("O-1004", 15.50, false),
            new Order("O-1005", 500.00, true)
        );

        int[] processedCount = {0};
//        System.out.println(processedCount[0]);
        Consumer<Order> processOrder = o -> {
            System.out.println("Processing rush order: " + o.id());
            processedCount[0]++;
        };
        // Builds a pipeline over the rush orders... or does it?
//        List<Order> rushOrders =
        orders.stream().filter(o -> o.isRush()).forEach(processOrder);


//        System.out.println(rushOrders);


//              .filter(o -> o.isRush())
//              .peek(o -> {
//                  System.out.println("Processing rush order: " + o.id());
//                  processedCount[0]++;
//              })
//              .map(o -> o.amount());

        System.out.println("TOTAL_RUSH_PROCESSED=" + processedCount[0]);
    }
}
