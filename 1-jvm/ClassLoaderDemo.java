import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;

public class ClassLoaderDemo {
//    private static PluginTask unused;

    public static void main(String[] args) throws Exception {
        URLClassLoader pluginLoader = new URLClassLoader(
            new URL[]{new File("./plugins").toURI().toURL()},
                ClassLoaderDemo.class.getClassLoader()          );
        Class<?> clazz = Class.forName("PluginTask", true, pluginLoader);
        Object instance = clazz.getDeclaredConstructor().newInstance();

        try {
            PluginTask task = (PluginTask) instance;
            task.run();
        } catch (ClassCastException e) {
            System.out.println("Class collision detected: " + e.getMessage());
        }

        Thread.sleep(Long.MAX_VALUE);
    }
}
