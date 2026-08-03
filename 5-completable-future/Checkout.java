import java.util.concurrent.CompletableFuture;

public class Checkout {

    static CompletableFuture<String> fetchUser(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "User[" + userId + "]";
        });
    }

    static CompletableFuture<Integer> checkStock(String sku) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(80);
            if (sku.equals("SKU-404")) {
                throw new RuntimeException("Inventory check failed: " + sku + " not found");
            }
            return 42;
        });
    }

    public static void main(String[] args) {
        String userId = "U-1001";
        String sku = "SKU-404"; // deterministic failing SKU

        CompletableFuture<String> userFuture = fetchUser(userId);
        CompletableFuture<Integer> stockFuture = checkStock(sku);


        CompletableFuture<String> result = userFuture.thenCombine(stockFuture,
            (user, stock) -> user + " checked out " + sku + " (stock=" + stock + ")").exceptionally(ex -> "FALLBACK: " + ex.getCause().getMessage());

        // BUG: no exception handling anywhere in the composition chain
        String output = result.join();
        System.out.println("Checkout result: " + output);
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
