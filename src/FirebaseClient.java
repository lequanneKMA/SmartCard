import java.io.*;
import java.net.*;
import java.util.Properties;

/**
 * Simple Firebase Realtime Database REST API client
 * No external dependencies - uses built-in Java HTTP
 */
public class FirebaseClient {
    private String databaseUrl;
    private String apiKey;
    
    public FirebaseClient() {
        loadConfig();
    }
    
    private void loadConfig() {
        Properties props = new Properties();
        File configFile = new File("firebase-config.properties");
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                databaseUrl = props.getProperty("firebase.url", "");
                apiKey = props.getProperty("firebase.api.key", "");
            } catch (Exception e) {
                System.err.println("⚠️ Không đọc được firebase-config.properties: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get card data from Firebase Realtime Database
     * @param userId User ID to query
     * @return CardData with balance and expiry from Firebase
     */
    public CardFirebaseData getCardData(int userId) throws Exception {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            throw new Exception("⚠️ Chưa cấu hình Firebase URL!\n\nVui lòng mở file: firebase-config.properties\nvà điền Firebase Database URL");
        }
        
        // Build URL: https://your-project.firebaseio.com/cards/{userId}.json
        String url = databaseUrl + "/cards/" + userId + ".json";
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            url += "?auth=" + apiKey;
        }
        
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Firebase Error: HTTP " + responseCode);
        }
        
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        // Parse JSON manually (no library needed for simple structure)
        return parseCardJson(response.toString());
    }
    
    /**
     * Simple JSON parser for card data
     * Expected format: {"balance":500000,"expiryDays":30,"fullName":"...","publicKey":"..."}
     */
    private CardFirebaseData parseCardJson(String json) throws Exception {
        if (json == null || json.trim().equals("null") || json.trim().isEmpty()) {
            throw new Exception("❌ Không tìm thấy dữ liệu thẻ trong Firebase");
        }
        
        CardFirebaseData data = new CardFirebaseData();
        
        // Extract balance
        String balanceKey = "\"balance\":";
        int balanceIdx = json.indexOf(balanceKey);
        if (balanceIdx >= 0) {
            int start = balanceIdx + balanceKey.length();
            int end = json.indexOf(",", start);
            if (end < 0) end = json.indexOf("}", start);
            data.balance = Integer.parseInt(json.substring(start, end).trim());
        }
        
        // Extract expiryDays
        String expiryKey = "\"expiryDays\":";
        int expiryIdx = json.indexOf(expiryKey);
        if (expiryIdx >= 0) {
            int start = expiryIdx + expiryKey.length();
            int end = json.indexOf(",", start);
            if (end < 0) end = json.indexOf("}", start);
            data.expiryDays = (short) Integer.parseInt(json.substring(start, end).trim());
        }
        
        // Extract fullName (optional)
        String nameKey = "\"fullName\":\"";
        int nameIdx = json.indexOf(nameKey);
        if (nameIdx >= 0) {
            int start = nameIdx + nameKey.length();
            int end = json.indexOf("\"", start);
            data.fullName = json.substring(start, end);
        }
        
        return data;
    }
    
    /**
     * Push card data to Firebase (create or update)
     * @param card CardData to push (PIN will NOT be pushed)
     * @return true if successful
     */
    public boolean pushCardData(CardData card) {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            System.err.println("⚠️ Chưa cấu hình Firebase URL!");
            return false;
        }
        
        try {
            // Build URL: https://your-project.firebaseio.com/cards/{userId}.json
            String url = databaseUrl + "/cards/" + card.userId + ".json";
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                url += "?auth=" + apiKey;
            }
            
            // Build JSON manually (no library needed)
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"userId\":").append(card.userId).append(",");
            json.append("\"fullName\":\"").append(escapeJson(card.fullName)).append("\",");
            json.append("\"balance\":").append(card.balance).append(",");
            json.append("\"expiryDays\":").append(card.expiryDays).append(",");
            json.append("\"dobDay\":").append(card.dobDay).append(",");
            json.append("\"dobMonth\":").append(card.dobMonth).append(",");
            json.append("\"dobYear\":").append(card.dobYear).append(",");
            json.append("\"lastUpdated\":").append(System.currentTimeMillis());
            json.append("}");
            
            // Send PUT request
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            // Write JSON body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes("UTF-8"));
            }
            
            int responseCode = conn.getResponseCode();
            return responseCode == 200 || responseCode == 201;
            
        } catch (Exception e) {
            System.err.println("⚠️ Firebase push failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete card data from Firebase by userId
     * @param userId ID of the card/user to delete
     * @return true if deletion succeeded
     */
    public boolean deleteCardData(int userId) {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            System.err.println("⚠️ Chưa cấu hình Firebase URL!");
            return false;
        }
        try {
            String url = databaseUrl + "/cards/" + userId + ".json";
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                url += "?auth=" + apiKey;
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            // Firebase returns 200 on successful deletion
            return responseCode == 200;
        } catch (Exception e) {
            System.err.println("⚠️ Firebase delete failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Escape special characters in JSON string
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Test Firebase connection and show config dialog if needed
     */
    public boolean testConnection() {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            return false;
        }
        
        try {
            URL url = new URL(databaseUrl + "/.json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            int code = conn.getResponseCode();
            return code == 200 || code == 401; // 401 = needs auth but URL valid
        } catch (Exception e) {
            return false;
        }
    }
    
    public String getDatabaseUrl() {
        return databaseUrl;
    }
    
    public void setDatabaseUrl(String url) {
        this.databaseUrl = url;
        saveConfig();
    }
    
    public void setApiKey(String key) {
        this.apiKey = key;
        saveConfig();
    }
    
    private void saveConfig() {
        Properties props = new Properties();
        props.setProperty("firebase.url", databaseUrl != null ? databaseUrl : "");
        props.setProperty("firebase.api.key", apiKey != null ? apiKey : "");
        
        try (FileOutputStream fos = new FileOutputStream("firebase-config.properties")) {
            props.store(fos, "Firebase Realtime Database Configuration");
        } catch (Exception e) {
            System.err.println("⚠️ Không lưu được config: " + e.getMessage());
        }
    }
}

/**
 * Card data from Firebase
 */
class CardFirebaseData {
    public int balance = 0;
    public short expiryDays = 0;
    public String fullName = "";
}

/**
 * Optional helper to delete card data from Firebase
 */
class FirebaseDeleteHelper {
    // Intentionally empty
}
