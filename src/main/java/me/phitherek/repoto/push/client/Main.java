package me.phitherek.repoto.push.client;

import com.google.gson.Gson;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.util.HttpAuthorizer;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Hashtable;

public class Main {

    static Pusher pusher;
    static String token;
    static Gson gson;

    public static void main(String[] args) throws IOException, InterruptedException {

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                pusher.disconnect();

                // Logout from auth endpoint
                String authUrls = "https://repotopushauth.deira.phitherek.me/logout";
                String query = null;
                try {
                    query = "token=" + URLEncoder.encode(token, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                URL authUrl = null;
                try {
                    authUrl = new URL(authUrls);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                HttpsURLConnection con = null;
                try {
                    con = (HttpsURLConnection)authUrl.openConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    con.setRequestMethod("POST");
                } catch (ProtocolException e) {
                    e.printStackTrace();
                }
                con.setRequestProperty("Content-Length", String.valueOf(query.length()));
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                con.setRequestProperty("User-Agent", "Repoto Push Service Java Client");
                con.setDoOutput(true);
                con.setDoInput(true);
                DataOutputStream output = null;
                try {
                    output = new DataOutputStream(con.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    output.writeBytes(query);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                BufferedReader input = null;
                try {
                    input = new BufferedReader(new InputStreamReader(con.getInputStream()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String response = null;
                try {
                    response = input.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Hashtable<String, String> logoutResponse = gson.fromJson(response, (new Hashtable<String, String>()).getClass());
                if(logoutResponse.get("success").equals("success")) {
                    System.out.println("Logged out successfully!");
                    System.exit(0);
                } else {
                    System.out.println("Could not log out!");
                    System.exit(1);
                }
            }
        }));
        // Get configuration
        gson = new Gson();
        Hashtable<String, String> config = gson.fromJson(new FileReader("config.json"), (new Hashtable<String, String>()).getClass());

        // Login to auth endpoint
        String authUrls = "https://repotopushauth.deira.phitherek.me/login";
        String query = "username=" + URLEncoder.encode(config.get("auth_username"), "UTF-8") + "&password=" + URLEncoder.encode(config.get("auth_password"), "UTF-8");
        URL authUrl = new URL(authUrls);
        HttpsURLConnection con = (HttpsURLConnection)authUrl.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Length", String.valueOf(query.length()));
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("User-Agent", "Repoto Push Service Java Client");
        con.setDoOutput(true);
        con.setDoInput(true);
        DataOutputStream output = new DataOutputStream(con.getOutputStream());
        output.writeBytes(query);
        output.close();
        BufferedReader input = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String response = input.readLine();
        input.close();
        Hashtable<String, String> tokenResponse = gson.fromJson(response, (new Hashtable<String, String>()).getClass());
        if(tokenResponse.get("error") != null) {
            System.out.println("Authentication error: " + tokenResponse.get("error"));
            System.exit(1);
        }
        System.out.println("Authenticated!");
        token = tokenResponse.get("token");

        // Pusher stuff

        HttpAuthorizer authorizer = new HttpAuthorizer("https://repotopushauth.deira.phitherek.me/endpoint");
        HashMap<String, String> additionalParams = new HashMap<String, String>();
        additionalParams.put("token", token);
        authorizer.setQueryStringParameters(additionalParams);
        PusherOptions options = new PusherOptions();
        options.setAuthorizer(authorizer);
        options.setEncrypted(true);
        pusher = new Pusher(config.get("pusher_key"), options);
        pusher.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange connectionStateChange) {
                System.out.println("Connection state changed to " + connectionStateChange.getCurrentState() + " from " + connectionStateChange.getPreviousState());
            }

            @Override
            public void onError(String s, String s1, Exception e) {
                System.out.println("There was a problem connecting!");
            }
        }, ConnectionState.ALL);

        Channel channel = pusher.subscribePrivate("private-".concat(config.get("auth_username")));

        channel.bind("my_event", new PrivateChannelEventListener() {
                    @Override
                    public void onAuthenticationFailure(String message, Exception e) {
                        System.out.println(String.format("Authentication failure due to [%s], exception was [%s]",
                                message, e));
                    }

                    @Override
                    public void onSubscriptionSucceeded(String s) {
                        System.out.println("Subscription succeeded!");
                    }

                    @Override
                    public void onEvent(String channel, String event, String message) {
                        Hashtable<String, String> memodata = gson.fromJson(message, (new Hashtable<String, String>()).getClass());
                        Runtime r = Runtime.getRuntime();
                        System.out.println("Received new PushMemo for " + config.get("auth_username") + ": " + memodata.get("message"));
                        String cmd = "notify-send \"Received new PushMemo for ".concat(config.get("auth_username")).concat("\" \"").concat(memodata.get("message")).concat("\"");
                        try {
                            Process p = r.exec(new String[]{"bash","-c",cmd});
                            p.waitFor();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });

                while(true) {}
    }
}
