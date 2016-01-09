package com.scurab.android.zumpareader.utils;

import com.scurab.android.zumpareader.ZR;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FotoDiskProvider {

    public interface OnProgressChange {
        void onUploadedDataProgressChange(int uploaded);
    }

    public static final String GET = "http://www.fotodisk.cz/";
    public static final String POST = GET + "upload.php";


    public static final String[] names = {"alt[]",     "private[0]", "album_id[]", "submit", "new_height[]", "new_width[]"};
    public static final String[] values = {"", "1",          "3",          "Nahrát"};

    public static final String FILES = "files[]";
    public static final String FILE_NAME = "fileName[]";

    public static String uploadPicture(String file, OnProgressChange listener)
            throws IOException {
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;

        String pathToOurFile = file;
        String urlServer = POST;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 256 * 1024;

        String result = null;

        FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile));

        URL url = new URL(urlServer);
        connection = (HttpURLConnection) url.openConnection();

        // Allow Inputs & Outputs
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);

        // Enable POST method
        connection.setRequestMethod("POST");

        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("User-Agent", ZR.Constants.USER_AGENT);
        connection.setRequestProperty("Referer", "http://www.fotodisk.cz/olduploader.php");
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        //needed here
        connection.setRequestProperty("Cookie", getSessionCookies());
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        String separator = twoHyphens + boundary + lineEnd;
        outputStream = new DataOutputStream(connection.getOutputStream());



        File f = new File(file);
        String[] x = file.split("\\.");
        String extension = x[x.length - 1];
        if (extension.equals("jpg")) {
            extension = "jpeg";
        }

        final String fileName = f.getName();

        outputStream.writeBytes(separator);
        outputStream.writeBytes(String.format(
                "Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", FILE_NAME, fileName));

        for (int i = 0; i < names.length; i++){
            String name = names[i];
            String value = i < values.length ? values[i] : "";

            outputStream.writeBytes(separator);
            outputStream.writeBytes(String.format(
                    "Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", name, value));
        }

        outputStream.writeBytes(separator);



        // outputStream.writeBytes("Content-Disposition: form-data; name=\"thefile0\";filename=\""
        // + f.getName() + "\"" + lineEnd + "Content-Type: image/" + extension +
        // "\r\n\r\n");
        outputStream.writeBytes(
                String.format("Content-Disposition: form-data; name=\"%s\";filename=\"%s\"\r\nContent-Type: image/%s\r\n\r\n", FILES, f.getName(), extension));

        bytesAvailable = fileInputStream.available();
        bufferSize = Math.min(bytesAvailable, maxBufferSize);
        buffer = new byte[bufferSize];

        // Read file
        bytesRead = fileInputStream.read(buffer, 0, bufferSize);

        int uploaded = 0;
        while (bytesRead > 0) {

            outputStream.write(buffer, 0, bufferSize);
            outputStream.flush();
            uploaded += bufferSize;
            if (listener != null) {
                listener.onUploadedDataProgressChange(uploaded);
            }

            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        }

        outputStream.writeBytes(lineEnd);
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

        outputStream.flush();
        outputStream.close();

        // Responses from the server (code and message)
        int serverResponseCode = connection.getResponseCode();
        String serverResponseMessage = connection.getResponseMessage();

        fileInputStream.close();
        InputStream is = connection.getInputStream();

        String s = getTextContentFromStream(is, null);

        if (serverResponseCode == 200 && serverResponseMessage.equals("OK")) {
            result = parseResponse(s);
        }

        is.close();
        connection.disconnect();

        return result;
    }

    public static String getSessionCookies() throws IOException {
        URL url = new URL(GET);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Allow Inputs & Outputs
        connection.setDoInput(true);
        String s = getTextContentFromStream(connection.getInputStream(), null);
        if (connection.getResponseCode() == 200) {
            return connection.getHeaderField("Set-Cookie");
        } else {
            return "PHPSESSID=h360canrv6qgkosphr5ctp6b01; Captcha=2516a304c6f0aa490d489c3de7b8bcc7.1384871204; __atuvc=2";
        }
    }

    public static String parseResponse(String html){
        String regexp = "<input type=\"text\" id=\"codedirect\" value=\"([^\"]*)\"";
        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String uploadPictureTest(String file, OnProgressChange listener) throws IOException {
        try {
            int waiter = 10;
            File f = new File(file);
            long len = f.length();
            int sub = (int) (len / 10);
            int uploaded = 0;
            for (int i = 0; i < waiter; i++) {
                uploaded += sub;
                if (listener != null) {
                    listener.onUploadedDataProgressChange(uploaded);
                }

                Thread.sleep(1000);
            }

            return "http://www.q3.cz/images/1801.png";
        } catch (Exception e) {

        }

        return "http://www.q3.cz/images/1801.png";
    }

    public static String getTextContentFromStream(InputStream is,
                                                  String codepage) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br;
        if (codepage != null) {
            br = new BufferedReader(new InputStreamReader(is, codepage));
        } else {
            br = new BufferedReader(new InputStreamReader(is));
        }

        String line = null;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        return sb.toString();
    }
}
