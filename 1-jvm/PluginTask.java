public class PluginTask {
    public void run() {
        System.out.println("Task executed by: " + this.getClass().getClassLoader().toString());
    }
}
