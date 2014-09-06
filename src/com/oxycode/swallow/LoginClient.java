package com.oxycode.swallow;

import android.util.Log;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class LoginClient {
    private static final String TAG = "SWAL";
    private static final String LOGIN_PAGE_URL = "http://192.255.255.94/";

    private LoginClient() {

    }

    public static boolean isLoggedIn() throws IOException {
        // TODO: Complete
        return false;
    }

    public static LoginResult login(String username, String password) throws IOException {
        HttpURLConnection urlConnection = createConnection("POST");
        String encryptedPassword = encryptPassword(password);
        Map<String, String> params = getPostData(username, encryptedPassword);
        byte[] data = convertPostParams(params);
        Map<String, String> headers = getPostHeaders(data);

        // Set headers
        setPostHeaders(urlConnection, headers);

        // Set body
        urlConnection.setDoOutput(true);
        urlConnection.getOutputStream().write(data);

        // Read response to see if login succeeded
        // Note that the page is encoded in GB2312
        InputStream inputStream = urlConnection.getInputStream();
        InputStreamReader inputReader;
        try {
            inputReader = new InputStreamReader(inputStream, "GB2312");
        } catch (UnsupportedEncodingException e) {
            Log.wtf(TAG, "Unsupported encoding: GB2312", e);
            return LoginResult.UNKNOWN_ERROR;
        }
        BufferedReader bufferedReader = new BufferedReader(inputReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            // TODO: Check success flag
            Log.d(TAG, line);
        }

        return LoginResult.SUCCESS;
    }

    public static void logout() throws IOException {
        // TODO: Complete
    }

    private static Map<String, String> getPostHeaders(byte[] content) {
        // Just to make the request look less automated, in case
        // they start banning auto-login clients.
        // Headers are from Internet Explorer 11.
        Map<String, String> headers = new LinkedHashMap<String, String>(7);
        headers.put("Pragma", "no-cache");
        headers.put("Accept", "text/html, application/xhtml+xml, */*");
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Trident/7.0; rv:11.0) like Gecko");
        headers.put("Referer", "http://192.255.255.94/");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Content-Length", String.valueOf(content.length));
        return headers;
    }

    private static Map<String, String> getPostData(String username, String encryptedPassword) {
        // Of these 6 parameters, only 2 are useful. WHY?!
        // Use LinkedHashMap to maintain the order of parameters,
        // just in case they take that into account as well when
        // validating logins (though they really shouldn't).
        Map<String, String> params = new LinkedHashMap<String, String>(6);
        params.put("DDDDD", username);
        params.put("upass", encryptedPassword);
        params.put("R1", "0");
        params.put("R2", "1");
        params.put("para", "00");
        params.put("0MKKey", "123456");
        return params;
    }

    private static String encryptPassword(String plainTextPassword) {
        // At least it's a minor step over storing passwords in plaintext...
        // It's just too bad they don't use SSL... FAIL!
        // http://security.stackexchange.com/questions/66475
        return md5("1" + plainTextPassword + "12345678") + "123456781";
    }

    private static HttpURLConnection createConnection(String method) throws IOException {
        URL url;
        try {
            url = new URL(LOGIN_PAGE_URL);
        } catch (MalformedURLException e) {
            Log.wtf(TAG, "Login page URL is malformed", e);
            return null;
        }

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        try {
            connection.setRequestMethod(method);
        } catch (ProtocolException e) {
            Log.wtf(TAG, "Incorrect request method: " + method, e);
        }

        return connection;
    }

    private static byte[] convertPostParams(Map<String, String> params) {
        try {
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(param.getValue(), "UTF-8"));
            }
            return postData.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.wtf(TAG, "Unsupported encoding: UTF-8", e);
            return null;
        }
    }

    private static void setPostHeaders(HttpURLConnection connection, Map<String, String> headers) {
        for (Map.Entry<String, String> param : headers.entrySet()) {
            connection.addRequestProperty(param.getKey(), param.getValue());
        }
    }

    private static String md5(String str) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Unsupported algorithm: MD5", e);
            return null;
        }

        byte[] hash;
        try {
            hash = digest.digest(str.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Log.wtf(TAG, "Unsupported encoding: UTF-8", e);
            return null;
        }

        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash){
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
