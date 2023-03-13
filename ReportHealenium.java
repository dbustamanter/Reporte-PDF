package reportPDF;

import constants.Navegador;
import driverConfig.DriverContext;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.print.PrintOptions;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ReportHealenium {
    private static boolean htmlBodyEnd = false;
    private static boolean nodosDiv = false;

    private static final String HOST_REPORTE = "localhost";
    private static List<String> divNodes = new ArrayList<>();

    public static void main(String[] args) {
        getRequest();
    }

    private static void getRequest() {
        BufferedWriter archivo;
        try {
            URL url = new URL(extractURL());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            File path = new File("./results/reportHealenium.html");
            archivo = new BufferedWriter(new FileWriter(path));
            connection.setRequestMethod("GET");
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String json;
            while ((json = rd.readLine()) != null) {
                if (json.contains("</html>")) {
                    archivo.write(json + "\n");
                    htmlBodyEnd = false;
                } else if (json.contains("</script>")) {
                    htmlBodyEnd = false;
                } else if(json.contains("</style>")){
                    archivo.write("@page{size:letter portrait;}\n</style>");
                }
                else if (!htmlBodyEnd) {
                    if (json.contains("<div class=\"elements-table-row content-hidden\">")) {
                        archivo.write("<div class=\"elements-table-row\">\n");
                    } else if (json.contains("<div class=\"plea\">") || json.contains("<span class=\"confirmation-column\">")) {
                        System.out.println("linea ignorada");
                    } else if (nodosDiv) {
                        if (json.contains("<div")) {
                            if (json.contains("</div>")) {
                                divNodes.add("<div>");
                                divNodes.add("</div>");
                            } else {
                                divNodes.add("<div>");
                            }
                        } else if (json.contains("</div>")) {
                            if (divNodes.size() >= 2) {
                                if (divNodes.size() % 2 == 0 && divNodes.get(0).equals(divNodes.get(1))) {
                                    System.out.println(divNodes);
                                    nodosDiv = false;
                                    divNodes.clear();
                                } else if (divNodes.size() == 2 && !divNodes.get(0).equals(divNodes.get(1))) {
                                    System.out.println(divNodes);
                                    nodosDiv = false;
                                    divNodes.clear();
                                } else {
                                    divNodes.add("</div>");
                                }
                            }
                        } else {
                            System.out.println("linea ignorada");
                        }
                    } else if (json.contains("<div class=\"confirmation-column\">")) {
                        nodosDiv = true;
                        divNodes.add("<div>");
                    } else if (json.contains("<img id=\"myImg\"")) {
                        String urlScreenshot = "http://"+HOST_REPORTE+":7878/" + json.substring(json.indexOf("screenshot"), json.indexOf(".png\"")) + ".png";
                        String fileScreenshot = json.substring(json.indexOf("screenshot_"), json.indexOf(".png\"")) + ".jpeg";
                        File pathScreenshot = new File("./results/screenshots");
                        if (!pathScreenshot.exists()) {
                            pathScreenshot.mkdirs();
                        }
                        try {
                            ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(urlScreenshot).openStream());
                            FileOutputStream fileOutputStream = new FileOutputStream(pathScreenshot + "\\" + fileScreenshot);
                            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                            byte[] fileContent = FileUtils.readFileToByteArray(new File(pathScreenshot + "\\" + fileScreenshot));
                            String encodedString = Base64.getEncoder().encodeToString(fileContent);
                            archivo.write(json.substring(0, json.indexOf("\"/")) + "\"data:image/png;base64," + encodedString + "\" " + json.substring(json.indexOf("style")));
                        } catch (Exception channelError) {
                            System.out.println("error al descargar archivo");
                        }
                    } else if (json.contains("<script")) {
                        htmlBodyEnd = true;
                    } else {
                        archivo.write(json + "\n");
                    }
                }
            }
            rd.close();
            archivo.close();
            downloadReport(path.getCanonicalPath());

        } catch (IOException ioException) {
            System.err.println("Error: " + ioException);
        } catch (Exception e) {
            System.err.println("Error genérico: " + e);
        }

    }

    private static String extractURL() {
        String urlReport="";
        try {
            File file = new File("./launch.txt");
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null){
                if(line.contains("Report available at")){
                    urlReport = line.substring(20,27) + HOST_REPORTE + line.substring(36);
                    System.out.println(urlReport);
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error al encontrar archivo: " + e);
        } catch (IOException e) {
            System.err.println("Error durante extracción de url: " + e);
        }
        return urlReport;
    }
    private static void downloadReport(String pathToHtml){
        try{
            File file= new File(pathToHtml);
            if(file.exists()){
                DriverContext.setUp(Navegador.Chrome);
                DriverContext.getDriver().get("file:///"+pathToHtml);
                Thread.sleep(2000);
                Pdf pdf = ((PrintsPage)DriverContext.getDelegate()).print(new PrintOptions());
                Files.write(Paths.get("./results/reportHealenium.pdf"), OutputType.BYTES.convertFromBase64Png(pdf.getContent()));
                DriverContext.getDriver().quit();
            }
        } catch (InterruptedException | IOException e) {
            System.err.println("Error al descargar pdf: " + e);
        }
    }

}
