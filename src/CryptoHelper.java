import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.math.BigInteger;

/**
 * Cryptographic utilities - FIXED LAYOUT (NEW CARD)
 * 
 * Card Structure (64 bytes) - FIXED LAYOUT:
 * [0-1]   UserID (2 bytes)
 * [2-17]  Encrypted Block (16 bytes): Balance (4 bytes) + Expiry (2 bytes) - FULL 16-byte AES block
 * [18-33] PIN Hash (16 bytes) - NO MORE OVERLAP!
 * [34]    PIN Retry Counter (1 byte)
 * [35]    DOB Day (1 byte)
 * [36]    DOB Month (1 byte)
 * [37-38] DOB Year (2 bytes)
 * [39-63] Full Name (25 bytes, UTF-8)
 * 
 * FIXED: No more overlap! Encrypted block and PIN hash are separate.
 */
public class CryptoHelper {
    
    /**
     * Derive AES-128 key from PIN using SHA-256 (truncate to 16 bytes)
     * PIN is hashed as 6-byte ASCII string (e.g., "123456")
     */
    public static SecretKeySpec deriveAESKeyFromPIN(String pin) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] pinBytes = pin.getBytes("ASCII");
        byte[] hash = sha256.digest(pinBytes); // 32 bytes
        
        byte[] aesKeyBytes = new byte[16];
        System.arraycopy(hash, 0, aesKeyBytes, 0, 16);
        
        SecretKeySpec key = new SecretKeySpec(aesKeyBytes, "AES");
        printHex("🔑 PC AES KEY (pin=" + pin + ")", aesKeyBytes);
        return key;
    }
    
    /**
     * Hash PIN with SHA-256 (truncated to 16 bytes to fit layout)
     * PIN is hashed as 6-byte ASCII string (e.g., "123456")
     */
    public static byte[] hashPIN(String pin) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] pinBytes = pin.getBytes("ASCII");
        byte[] fullHash = sha256.digest(pinBytes); // 32 bytes
        
        byte[] truncatedHash = new byte[16];
        System.arraycopy(fullHash, 0, truncatedHash, 0, 16);
        
        return truncatedHash;
    }
    
    /**
     * Encrypt Balance (4 bytes) + Expiry (2 bytes)
     * Returns full 16-byte encrypted block
     */
    public static void printHex(String label, byte[] data) {
    StringBuilder sb = new StringBuilder();
    for (byte b : data) {
        sb.append(String.format("%02X ", b & 0xFF));
    }
    System.out.println(label + ": " + sb.toString());
}
    public static byte[] encryptSensitiveData(int balance, short expiry, String pin) throws Exception {
        SecretKeySpec aesKey = deriveAESKeyFromPIN(pin);
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        
        byte[] plaintext = new byte[16];
        
        // Balance: 4 bytes [0-3]
        plaintext[0] = (byte) ((balance >> 24) & 0xFF);
        plaintext[1] = (byte) ((balance >> 16) & 0xFF);
        plaintext[2] = (byte) ((balance >> 8) & 0xFF);
        plaintext[3] = (byte) (balance & 0xFF);
        
        // Expiry: 2 bytes [4-5]
        plaintext[4] = (byte) ((expiry >> 8) & 0xFF);
        plaintext[5] = (byte) (expiry & 0xFF);
        
        // Rest is padding (zeros)
        
        byte[] encrypted = cipher.doFinal(plaintext);
        
        // Return full 16-byte encrypted block
        
        return encrypted;
        
    }
    
    /**
     * Decrypt Balance + Expiry from 16-byte encrypted block
     */
    public static int[] decryptSensitiveData(byte[] encryptedBytes, String pin) throws Exception {
        if (encryptedBytes.length < 16) {
            throw new IllegalArgumentException("Encrypted data must be 16 bytes");
        }
        
        SecretKeySpec aesKey = deriveAESKeyFromPIN(pin);
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        
        // Decrypt full 16-byte block
        byte[] decrypted = cipher.doFinal(encryptedBytes);
        
        int balance = ((decrypted[0] & 0xFF) << 24) |
                     ((decrypted[1] & 0xFF) << 16) |
                     ((decrypted[2] & 0xFF) << 8) |
                     (decrypted[3] & 0xFF);
        
        int expiry = ((decrypted[4] & 0xFF) << 8) |
                    (decrypted[5] & 0xFF);
        

        return new int[]{balance, expiry};
    }
    
    /**
     * Parse RSA public key
     */
    public static PublicKey parseRSAPublicKey(byte[] keyData) throws Exception {
        if (keyData.length < 131) {
            throw new IllegalArgumentException("Key data must be at least 131 bytes");
        }
        
        byte[] modulusBytes = new byte[128];
        System.arraycopy(keyData, 0, modulusBytes, 0, 128);
        BigInteger modulus = new BigInteger(1, modulusBytes);
        
        byte[] exponentBytes = new byte[3];
        System.arraycopy(keyData, 128, exponentBytes, 0, 3);
        BigInteger exponent = new BigInteger(1, exponentBytes);
        
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
        return keyFactory.generatePublic(keySpec);
    }
    
    /**
     * Verify RSA signature
     */
    public static boolean verifySignature(byte[] challenge, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initVerify(publicKey);
        sig.update(challenge);
        return sig.verify(signature);
    }
    
    /**
     * Generate random challenge (32 bytes)
     */
    public static byte[] generateChallenge() {
        byte[] challenge = new byte[32];
        new java.security.SecureRandom().nextBytes(challenge);
        return challenge;
    }
    
    /**
     * Build card data for WRITE - OLD LAYOUT
     */
    public static byte[] buildCardData(int userId, int balance, short expiry, 
                                       String pin, byte pinRetry,
                                       byte dobDay, byte dobMonth, short dobYear,
                                       String fullName) throws Exception {
        byte[] cardData = new byte[64];
        
        // [0-1] UserID
        cardData[0] = (byte) ((userId >> 8) & 0xFF);
        cardData[1] = (byte) (userId & 0xFF);
        
        // [2-17] Encrypted data (16 bytes) - FULL BLOCK
        byte[] encrypted = encryptSensitiveData(balance, expiry, pin);
        System.arraycopy(encrypted, 0, cardData, 2, 16);
        
        // [18-33] PIN Hash (NO OVERLAP!)
        byte[] pinHash = hashPIN(pin);
        System.arraycopy(pinHash, 0, cardData, 18, 16);
        
        // [34] PIN Retry Counter
        cardData[34] = pinRetry;
        
        // [35-38] Date of Birth
        cardData[35] = dobDay;
        cardData[36] = dobMonth;
        cardData[37] = (byte) ((dobYear >> 8) & 0xFF);
        cardData[38] = (byte) (dobYear & 0xFF);
        
        // [39-63] Full Name (25 bytes)
        byte[] nameBytes = fullName.getBytes("UTF-8");
        int nameLen = Math.min(nameBytes.length, 25);
        System.arraycopy(nameBytes, 0, cardData, 39, nameLen);
        
        return cardData;
    }
    
    /**
     * ✅ Parse ENCRYPTED card data (from READ command)
     * Requires PIN to decrypt balance/expiry
     * 
     * @param data 64-byte encrypted card data
     * @param pin PIN for decryption (6-digit string)
     */
    public static CardData parseEncryptedCardData(byte[] data, String pin) throws Exception {
        if (data.length < 64) {
            throw new IllegalArgumentException("Card data must be 64 bytes");
        }
        if (pin == null || pin.isEmpty()) {
            throw new IllegalArgumentException("PIN required to decrypt data");
        }
        
        System.out.println("=== DEBUG parseEncryptedCardData ===");
        System.out.print("Encrypted data[2-7]: ");
        for (int i = 2; i <= 7; i++) {
            System.out.printf("%02X ", data[i]);
        }
        System.out.println();
        
        CardData card = new CardData();
        
        // [0-1] UserID
        card.userId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        
        // [2-17] Encrypted block - DECRYPT with PIN
        try {
            byte[] encryptedBlock = new byte[16];
            System.arraycopy(data, 2, encryptedBlock, 0, 16);
            
            SecretKeySpec aesKey = deriveAESKeyFromPIN(pin);
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            
            byte[] decrypted = cipher.doFinal(encryptedBlock);
            
            // Parse balance (4 bytes) and expiry (2 bytes)
            card.balance = ((decrypted[0] & 0xFF) << 24) |
                          ((decrypted[1] & 0xFF) << 16) |
                          ((decrypted[2] & 0xFF) << 8) |
                          (decrypted[3] & 0xFF);
            
            card.expiryDays = (short) (((decrypted[4] & 0xFF) << 8) |
                                       (decrypted[5] & 0xFF));
            System.out.printf("DECRYPT BLOCK: %s\n", Arrays.toString(decrypted));
            System.out.printf("Decrypted: balance=%d, expiryDays=%d\n", card.balance, card.expiryDays);
        } catch (Exception e) {
            throw new Exception("Failed to decrypt card data: " + e.getMessage(), e);
        }
        
        // [34] PIN Retry
        card.pinRetry = data[34];
        
        // [35-38] DOB
        card.dobDay = data[35];
        card.dobMonth = data[36];
        card.dobYear = (short) (((data[37] & 0xFF) << 8) | (data[38] & 0xFF));
        
        // [39-63] Name (25 bytes)
        byte[] nameBytes = new byte[25];
        System.arraycopy(data, 39, nameBytes, 0, 25);
        card.fullName = new String(nameBytes, "UTF-8").trim();
        
        return card;
    }
    
    /**
     * ✅ Parse DECRYPTED card data (from VERIFY_PIN response)
     * Data [2-17] is already plaintext, no decryption needed
     * 
     * @param data 64-byte decrypted card data
     * @param pin PIN value (for storing in CardData.pin field)
     */
    public static CardData parseDecryptedCardData(byte[] data, String pin) throws Exception {
        if (data.length < 64) {
            throw new IllegalArgumentException("Card data must be 64 bytes");
        }
        
        System.out.println("=== DEBUG parseDecryptedCardData ===");
        System.out.print("Plaintext data[2-7]: ");
        for (int i = 2; i <= 7; i++) {
            System.out.printf("%02X ", data[i]);
        }
        System.out.println();
        
        CardData card = new CardData();
        
        // [0-1] UserID
        card.userId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        
        // [2-17] Already decrypted by card - read balance/expiry directly
        card.balance = ((data[2] & 0xFF) << 24) |
                      ((data[3] & 0xFF) << 16) |
                      ((data[4] & 0xFF) << 8) |
                      (data[5] & 0xFF);
        
        card.expiryDays = (short) (((data[6] & 0xFF) << 8) |
                                   (data[7] & 0xFF));
        
        System.out.printf("Parsed: balance=%d, expiryDays=%d\n", card.balance, card.expiryDays);
        
        // [34] PIN Retry
        card.pinRetry = data[34];
        
        // [35-38] DOB
        card.dobDay = data[35];
        card.dobMonth = data[36];
        card.dobYear = (short) (((data[37] & 0xFF) << 8) | (data[38] & 0xFF));
        
        // [39-63] Name (25 bytes)
        byte[] nameBytes = new byte[25];
        System.arraycopy(data, 39, nameBytes, 0, 25);
        card.fullName = new String(nameBytes, "UTF-8").trim();
        
        return card;
    }
}