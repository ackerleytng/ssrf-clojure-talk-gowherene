import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

public class Main {
    public static void main(String[] args) {
        String urlInput = args[0];
        int linesToShow = -1;
        if (args.length > 1) {
            linesToShow = Integer.parseInt(args[1]);
        }
        boolean force = false;
        if (args.length > 2 && args[2].equals("force")) {
            force = true;
        }

        URL url;
        try {
            url = new URL(urlInput);
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL!");
            return;
        }

        System.out.println("scheme=" + url.getProtocol());
        System.out.println("host=" + url.getHost());
        System.out.println("port=" + url.getPort());
        System.out.println("-----------------------");

        if (!force && !url.getHost().endsWith("wikipedia.org")) {
            System.out.println("You Shall Not Pass!");
            return;
        }

        try {
            BufferedReader in =
                new BufferedReader(new InputStreamReader(url.openStream()));

            int i = 0;
            String inputLine = in.readLine();
            while (inputLine != null && (linesToShow < 0 || i < linesToShow)) {
                System.out.println(inputLine);
                inputLine = in.readLine();

                i++;
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
