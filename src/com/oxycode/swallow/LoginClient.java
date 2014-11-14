package com.oxycode.swallow;

import android.util.Log;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoginClient {
    public enum QueryResult {
        LOGGED_IN,
        LOGGED_OUT,
        EXCEEDED_MAX_RETRIES,
        CANCELLED,
        UNKNOWN
    }

    public enum LoginResult {
        SUCCESS,
        EMPTY_CREDENTIALS,
        INCORRECT_CREDENTIALS,
        ACCOUNT_BANNED,
        EXCEEDED_MAX_RETRIES,
        CANCELLED,
        UNKNOWN
    }

    public enum LogoutResult {
        SUCCESS,
        NOT_LOGGED_IN,
        EXCEEDED_MAX_RETRIES,
        CANCELLED,
        UNKNOWN
    }

    public static interface Handler {
        boolean onException(IOException e, int remainingTrialCount);
    }

    private static final String TAG = LoginClient.class.getName();
    private static final String LOGIN_PAGE_URL = "http://192.255.255.94/";
    private static final String LOGOUT_PAGE_URL = "http://192.255.255.94/F.htm";
    private static final String PAGE_ENCODING = "GB2312";
    private static final int SOCKET_TIMEOUT_MS = 5000;

    // Don't feel like using a full blown HTML parser for this, plus the page is not
    // under our control anyways, so a parser won't really help in the long run.
    private static final Pattern PAGE_TITLE_REGEX = Pattern.compile("^<title>(.*?)</title>$");
    private static final Pattern PAGE_STATUS_CODE_REGEX = Pattern.compile("^Msg=(\\d\\d);.*");

    private LoginClient() {

    }

    public static QueryResult getLoginStatus(int trialCount, Handler handler) {
        Map<String, String> headers = createGetHeaders();
        while (--trialCount >= 0) {
            try {
                // Create GET request to login page
                HttpURLConnection urlConnection = createConnection(LOGIN_PAGE_URL, "GET");

                // Set request headers
                for (Map.Entry<String, String> param : headers.entrySet()) {
                    urlConnection.addRequestProperty(param.getKey(), param.getValue());
                }

                // Read response to determine the page we are on.
                // If we are logged in, the page will contain login status info;
                // otherwise, the page will be the login form.
                InputStream inputStream = urlConnection.getInputStream();
                BufferedReader inputReader = createStreamReader(inputStream);
                try {
                    Matcher titleMatcher = PAGE_TITLE_REGEX.matcher("");
                    String line;
                    while ((line = inputReader.readLine()) != null) {
                        titleMatcher.reset(line);
                        if (titleMatcher.matches()) {
                            String title = titleMatcher.group(1);
                            if (title.startsWith("Drcom上网信息窗")) {
                                Log.d(TAG, "Query login status returned result: logged in");
                                return QueryResult.LOGGED_IN;
                            } else if (title.startsWith("Drcom上网登录窗")) {
                                Log.d(TAG, "Query login status returned result: not logged in");
                                return QueryResult.LOGGED_OUT;
                            } else {
                                Log.w(TAG, "Unknown login status page title: " + title);
                                return QueryResult.UNKNOWN;
                            }
                        }
                    }

                    // Could not find page title, for some reason...
                    Log.w(TAG, "Could not find title on login status page");
                    return QueryResult.UNKNOWN;
                } finally {
                    if (inputReader != null) {
                        inputReader.close();
                    }
                }
            } catch (IOException e) {
                if (handler != null) {
                    if (!handler.onException(e, trialCount)) {
                        Log.d(TAG, "Query cancelled by exception handler");
                        return QueryResult.CANCELLED;
                    }
                }
            }
        }

        Log.d(TAG, "Query login status failed, exceeded max retries");
        return QueryResult.EXCEEDED_MAX_RETRIES;
    }

    public static LoginResult login(String username, String password, int trialCount, Handler handler) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return LoginResult.EMPTY_CREDENTIALS;
        }

        String encryptedPassword = encryptPassword(password);
        Map<String, String> params = createPostData(username, encryptedPassword);
        byte[] data = convertPostParams(params);
        Map<String, String> headers = createPostHeaders(data);
        while (--trialCount >= 0) {
            try {
                // Create POST request to login page
                HttpURLConnection urlConnection = createConnection(LOGIN_PAGE_URL, "POST");

                // Set request headers
                for (Map.Entry<String, String> param : headers.entrySet()) {
                    urlConnection.addRequestProperty(param.getKey(), param.getValue());
                }

                // Write request body
                urlConnection.setDoOutput(true);
                urlConnection.getOutputStream().write(data);

                // Read response to see if login succeeded
                InputStream inputStream = urlConnection.getInputStream();
                BufferedReader inputReader = createStreamReader(inputStream);
                try {
                    Matcher titleMatcher = PAGE_TITLE_REGEX.matcher("");
                    Matcher statusCodeMatcher = null;
                    String line;
                    while ((line = inputReader.readLine()) != null) {
                        if (statusCodeMatcher == null) {
                            // First we need to find the type of the page we're on.
                            // This is done by parsing the title of the page, which
                            // tells us whether the login succeeded or not.
                            titleMatcher.reset(line);
                            if (titleMatcher.matches()) {
                                String title = titleMatcher.group(1);
                                if (title.equals("登录成功窗")) {
                                    // Login succeeded, no status code exists on this page.
                                    Log.d(TAG, "Login returned result: success");
                                    return LoginResult.SUCCESS;
                                } else if (title.equals("信息返回窗")) {
                                    // Login failed, now search for the status code.
                                    statusCodeMatcher = PAGE_STATUS_CODE_REGEX.matcher("");
                                } else {
                                    Log.w(TAG, "Unknown login result page title: " + title);
                                    return LoginResult.UNKNOWN;
                                }
                            }
                        } else {
                            statusCodeMatcher.reset(line);
                            if (statusCodeMatcher.matches()) {
                                String statusCodeStr = statusCodeMatcher.group(1);
                                int statusCode = Integer.parseInt(statusCodeStr);
                                if (statusCode == 1) {
                                    Log.d(TAG, "Login returned result: incorrect credentials");
                                    return LoginResult.INCORRECT_CREDENTIALS;
                                } else if (statusCode == 5) {
                                    Log.d(TAG, "Login returned result: account banned");
                                    return LoginResult.ACCOUNT_BANNED;
                                } else {
                                    Log.w(TAG, "Unknown login result status code: " + statusCode);
                                    return LoginResult.UNKNOWN;
                                }
                            }
                        }
                    }

                    // We could not find the page title and/or status code...
                    // Either an unhandled case that we didn't consider, or
                    // the format of the page has changed.
                    if (statusCodeMatcher == null) {
                        Log.w(TAG, "Could not find title on login result page");
                    } else {
                        Log.w(TAG, "Could not find status code on login result page");
                    }
                    return LoginResult.UNKNOWN;
                } finally {
                    if (inputReader != null) {
                        inputReader.close();
                    }
                }
            } catch (IOException e) {
                if (!handler.onException(e, trialCount)) {
                    Log.d(TAG, "Login cancelled by exception handler");
                    return LoginResult.CANCELLED;
                }
            }
        }

        Log.d(TAG, "Login failed, exceeded max retries");
        return LoginResult.EXCEEDED_MAX_RETRIES;
    }

    public static LogoutResult logout(int trialCount, Handler handler) {
        Map<String, String> headers = createGetHeaders();
        while (--trialCount >= 0) {
            try {
                // Create GET request to logout page
                HttpURLConnection urlConnection = createConnection(LOGOUT_PAGE_URL, "GET");

                // Set request headers
                for (Map.Entry<String, String> param : headers.entrySet()) {
                    urlConnection.addRequestProperty(param.getKey(), param.getValue());
                }

                // Read response to determine the logout result
                InputStream inputStream = urlConnection.getInputStream();
                BufferedReader inputReader = createStreamReader(inputStream);
                try {
                    Matcher statusCodeMatcher = PAGE_STATUS_CODE_REGEX.matcher("");
                    String line;
                    while ((line = inputReader.readLine()) != null) {
                        statusCodeMatcher.reset(line);
                        if (statusCodeMatcher.matches()) {
                            String statusCodeStr = statusCodeMatcher.group(1);
                            int statusCode = Integer.parseInt(statusCodeStr);
                            if (statusCode == 1) {
                                Log.d(TAG, "Logout returned result: not logged in");
                                return LogoutResult.NOT_LOGGED_IN;
                            } else if (statusCode == 14) {
                                Log.d(TAG, "Logout returned result: success");
                                return LogoutResult.SUCCESS;
                            } else {
                                Log.w(TAG, "Unknown logout result status code: " + statusCode);
                                return LogoutResult.UNKNOWN;
                            }
                        }
                    }

                    // Could not find status code, for some reason...
                    Log.w(TAG, "Could not find status code on logout result page");
                    return LogoutResult.UNKNOWN;
                } finally {
                    if (inputReader != null) {
                        inputReader.close();
                    }
                }
            } catch (IOException e) {
                if (!handler.onException(e, trialCount)) {
                    Log.d(TAG, "Logout cancelled by exception handler");
                    return LogoutResult.CANCELLED;
                }
            }
        }

        Log.d(TAG, "Logout failed, exceeded max retries");
        return LogoutResult.EXCEEDED_MAX_RETRIES;
    }

    private static Map<String, String> createGetHeaders() {
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
        return headers;
    }

    private static Map<String, String> createPostHeaders(byte[] content) {
        Map<String, String> headers = createGetHeaders();
        headers.put("Content-Length", String.valueOf(content.length));
        return headers;
    }

    private static Map<String, String> createPostData(String username, String encryptedPassword) {
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
        // I wonder how hard it would be to sniff login packets, hm?
        // http://security.stackexchange.com/questions/66475
        return md5("1" + plainTextPassword + "12345678") + "123456781";
    }

    private static HttpURLConnection createConnection(String urlString, String method) throws IOException {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            Log.wtf(TAG, "Login page URL is malformed: " + urlString, e);
            return null;
        }

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        try {
            connection.setRequestMethod(method);
        } catch (ProtocolException e) {
            Log.wtf(TAG, "Invalid request method: " + method, e);
            return null;
        }

        connection.setConnectTimeout(SOCKET_TIMEOUT_MS);
        connection.setReadTimeout(SOCKET_TIMEOUT_MS);

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
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static BufferedReader createStreamReader(InputStream inputStream) {
        InputStreamReader inputReader;
        try {
            inputReader = new InputStreamReader(inputStream, PAGE_ENCODING);
        } catch (UnsupportedEncodingException e) {
            Log.wtf(TAG, "Unsupported encoding: " + PAGE_ENCODING, e);
            return null;
        }
        return new BufferedReader(inputReader);
    }
}
