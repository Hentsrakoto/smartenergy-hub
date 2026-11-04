import java.net.HttpURLConnection;
import java.net.URI;

/** Sonde HTTP sans curl ni dépendance ajoutée à l'image Java. */
public final class ContainerHealthcheck {
    public static void main(String[] args) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(args[0]).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int status = connection.getResponseCode();
            connection.disconnect();
            System.exit(status == 200 ? 0 : 1);
        } catch (Exception exception) {
            System.exit(1);
        }
    }
}
