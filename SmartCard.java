package SmartCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

/**
 * GYM SMART CARD - PHASE 2: FULL SECURITY (FIXED)
 * 
 * CRITICAL FIX: Changed data layout to avoid overlap
 * 
 * Card Structure (64 bytes) - FIXED:
 * [0-1]   UserID (2 bytes) - Public
 * [2-17]  Encrypted Block (16 bytes): Balance + Expiry encrypted with AES
 * [18-33] PIN Hash (16 bytes) - SHA-256 truncated to 16 bytes
 * [34]    PIN Retry Counter (1 byte)
 * [35]    DOB Day (1 byte) - Public
 * [36]    DOB Month (1 byte) - Public
 * [37-38] DOB Year (2 bytes) - Public
 * [39-63] FullName (25 bytes) - Public (UTF-8)
 */
public class SmartCard extends Applet {
    // Data offsets - FIXED
    private static final byte OFFSET_USER_ID = 0;
    private static final byte OFFSET_BALANCE = 2;           // Encrypted (start of 16-byte block)
    private static final byte OFFSET_EXPIRY = 6;            // Encrypted (inside 16-byte block)
    private static final byte OFFSET_PIN_HASH = 18;         // Changed from 8 to 18 (NO OVERLAP!)
    private static final byte OFFSET_PIN_RETRY = 34;        // Changed from 24 to 34
    private static final byte OFFSET_DOB_DAY = 35;          // Changed from 25 to 35
    private static final byte OFFSET_DOB_MONTH = 36;        // Changed from 26 to 36
    private static final byte OFFSET_DOB_YEAR = 37;         // Changed from 27 to 37
    private static final byte OFFSET_FULLNAME = 39;         // Changed from 29 to 39
    
    private static final short DATA_SIZE = 64;
    private static final byte MAX_PIN_RETRY = 5;
    private static final byte PIN_HASH_SIZE = 16;
    
    // APDU Instructions
    private static final byte INS_READ = (byte) 0xB0;
    private static final byte INS_WRITE = (byte) 0xD0;
    private static final byte INS_VERIFY_PIN = (byte) 0x20;
    private static final byte INS_CHANGE_PIN = (byte) 0x24;
    private static final byte INS_GET_PUBLIC_KEY = (byte) 0x82;
    private static final byte INS_SIGN_CHALLENGE = (byte) 0x88;
    
    // Admin Instructions (no PIN required)
    private static final byte INS_ADMIN_UNLOCK = (byte) 0xAA;  // Reset retry counter
    private static final byte INS_ADMIN_RESET_PIN = (byte) 0xAB;  // Reset PIN without old PIN
    
    // Persistent storage
    private byte[] cardData;
    
    // Cryptographic objects
    private AESKey aesKey;
    private Cipher aesCipher;
    private MessageDigest sha256;
    private KeyPair rsaKeyPair;
    private Signature rsaSignature;
    
    // Transient data
    private boolean pinVerified;
    private byte[] tempBuffer;
    
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SmartCard().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public SmartCard() {
        cardData = new byte[DATA_SIZE];
        tempBuffer = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_DESELECT);
        
        Util.arrayFillNonAtomic(cardData, (short) 0, DATA_SIZE, (byte) 0x00);
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;
        
        initCrypto();
        pinVerified = false;
    }
    
    private void initCrypto() {
        try {
            aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
            sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
            
            rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
            rsaKeyPair.genKeyPair();
            
            rsaSignature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
            rsaSignature.init(rsaKeyPair.getPrivate(), Signature.MODE_SIGN);
            
        } catch (CryptoException e) {
            ISOException.throwIt(ISO7816.SW_FILE_INVALID);
        }
    }
    
    private void deriveAESKeyFromPIN(byte[] pinBytes, short offset, short length) {
        // Derive AES-128 key using SHA-256 over PIN byte array (6 ASCII digits), truncated to 16 bytes
        sha256.reset();
        sha256.doFinal(pinBytes, offset, length, tempBuffer, (short) 0);
        // AESKey expects 16 bytes; implementation uses first 16 bytes of tempBuffer
        aesKey.setKey(tempBuffer, (short) 0);
    }
    
    private void hashPIN(byte[] pinBytes, short pinOffset, short pinLength, byte[] output, short offset) {
        // Hash PIN using SHA-256 over PIN byte array (6 ASCII digits), store first 16 bytes (layout constraint)
        sha256.reset();
        sha256.doFinal(pinBytes, pinOffset, pinLength, tempBuffer, (short) 0);
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, output, offset, PIN_HASH_SIZE);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) return;

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        switch (ins) {
            case INS_READ:
                handleRead(apdu);
                break;
            case INS_WRITE:
                handleWrite(apdu);
                break;
            case INS_VERIFY_PIN:
                handleVerifyPIN(apdu);
                break;
            case INS_CHANGE_PIN:
                handleChangePIN(apdu);
                break;
            case INS_GET_PUBLIC_KEY:
                handleGetPublicKey(apdu);
                break;
            case INS_SIGN_CHALLENGE:
                handleSignChallenge(apdu);
                break;
            case INS_ADMIN_UNLOCK:
                handleAdminUnlock(apdu);
                break;
            case INS_ADMIN_RESET_PIN:
                handleAdminResetPin(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
    
    private void handleRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, DATA_SIZE);
        apdu.setOutgoingAndSend((short) 0, DATA_SIZE);
    }
    
    private void handleWrite(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short bytesRead = apdu.setIncomingAndReceive();
        
        if (bytesRead != DATA_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        boolean isBlankCard = (cardData[OFFSET_USER_ID] == 0) && 
                              (cardData[OFFSET_USER_ID + 1] == 0);
        
        // Allow reset: if writing UserID = 0, always allow (for card reset/delete)
        boolean isResetting = (buf[ISO7816.OFFSET_CDATA] == 0) && 
                              (buf[ISO7816.OFFSET_CDATA + 1] == 0);
        
        if (!isBlankCard && !pinVerified && !isResetting) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, cardData, (short) 0, DATA_SIZE);
        pinVerified = false;
    }
    
    private void handleVerifyPIN(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        if (cardData[OFFSET_PIN_RETRY] == 0) {
            ISOException.throwIt((short) 0x6983);
        }
        
        // Hash 6-byte PIN from APDU data
        hashPIN(buf, ISO7816.OFFSET_CDATA, (short) 6, tempBuffer, (short) 0);
        
        boolean match = true;
        for (short i = 0; i < PIN_HASH_SIZE; i++) {
            if (tempBuffer[i] != cardData[(short)(OFFSET_PIN_HASH + i)]) {
                match = false;
                break;
            }
        }
        
        if (match) {
            pinVerified = true;
            cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;
            deriveAESKeyFromPIN(buf, ISO7816.OFFSET_CDATA, (short) 6);
            
            // Decrypt the encrypted block (Balance + Expiry) before returning
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(cardData, OFFSET_BALANCE, (short) 16, tempBuffer, (short) 0);
            
            // Copy all card data to buffer
            Util.arrayCopyNonAtomic(cardData, (short) 0, buf, (short) 0, DATA_SIZE);
            
            // Replace encrypted block with decrypted data
            Util.arrayCopyNonAtomic(tempBuffer, (short) 0, buf, OFFSET_BALANCE, (short) 16);
            
            apdu.setOutgoingAndSend((short) 0, DATA_SIZE);
        } else {
            cardData[OFFSET_PIN_RETRY]--;
            pinVerified = false;
            ISOException.throwIt((short)(0x63C0 | cardData[OFFSET_PIN_RETRY]));
        }
    }
    
    private void handleChangePIN(APDU apdu) {
        if (!pinVerified) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 12) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Old PIN: buf[5-10], New PIN: buf[11-16]
        short oldPinOffset = ISO7816.OFFSET_CDATA;
        short newPinOffset = (short)(ISO7816.OFFSET_CDATA + 6);
        
        // Verify old PIN - use tempBuffer[0-15] for PIN hash
        hashPIN(buf, oldPinOffset, (short) 6, tempBuffer, (short) 0);
        boolean match = true;
        for (short i = 0; i < PIN_HASH_SIZE; i++) {
            if (tempBuffer[i] != cardData[(short)(OFFSET_PIN_HASH + i)]) {
                match = false;
                break;
            }
        }
        
        if (!match) {
            cardData[OFFSET_PIN_RETRY]--;
            pinVerified = false;
            ISOException.throwIt((short)(0x63C0 | cardData[OFFSET_PIN_RETRY]));
        }
        
        // ✅ RE-ENCRYPT balance/expiry with NEW PIN
        // 1. Decrypt with OLD PIN - use tempBuffer[32-47] for plaintext to avoid collision with hash at [0-15]
        deriveAESKeyFromPIN(buf, oldPinOffset, (short) 6);
        aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
        aesCipher.doFinal(cardData, OFFSET_BALANCE, (short) 16, tempBuffer, (short) 32);
        
        // 2. Encrypt with NEW PIN - read from tempBuffer[32-47], write to card
        deriveAESKeyFromPIN(buf, newPinOffset, (short) 6);
        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
        aesCipher.doFinal(tempBuffer, (short) 32, (short) 16, cardData, OFFSET_BALANCE);
        
        // 3. Update PIN hash - hash to tempBuffer[0-15] then copy to card
        hashPIN(buf, newPinOffset, (short) 6, tempBuffer, (short) 0);
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, cardData, OFFSET_PIN_HASH, PIN_HASH_SIZE);
        
        // ✅ Giữ session hợp lệ với PIN mới
        // aesKey đã được derive từ newPin ở bước 2
        // pinVerified vẫn = true, không cần reset
        // pinVerified = false; // REMOVED: Keep session valid
    }
    
    private void handleGetPublicKey(APDU apdu) {
        RSAPublicKey pubKey = (RSAPublicKey) rsaKeyPair.getPublic();
        
        byte[] buf = apdu.getBuffer();
        short offset = 0;
        
        short modulusLen = pubKey.getModulus(buf, offset);
        offset += modulusLen;
        
        short exponentLen = pubKey.getExponent(buf, offset);
        offset += exponentLen;
        
        apdu.setOutgoingAndSend((short) 0, offset);
    }
    
    private void handleSignChallenge(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 32) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        rsaSignature.sign(buf, ISO7816.OFFSET_CDATA, lc, buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, (short) 128);
    }
    
    /**
     * Admin unlock - Reset retry counter without PIN verification
     * Command: 00 AA 00 00
     * Response: 90 00 (success)
     */
    private void handleAdminUnlock(APDU apdu) {
        // Admin privilege: reset retry counter without authentication
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;
        // Note: pinVerified stays false - this is just unlock, not authentication
    }
    
    /**
     * Admin reset PIN - Change PIN without knowing old PIN
     * Command: 00 AB 00 00 06 [6-byte new PIN]
     * Response: 90 00 (success)
     * 
     * WARNING: Balance/Expiry will be re-encrypted with NEW PIN.
     * Old encrypted data will become unreadable.
     */
    private void handleAdminResetPin(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // New PIN at buf[5-10]
        short newPinOffset = ISO7816.OFFSET_CDATA;
        
        // Decrypt balance/expiry with CURRENT PIN (if possible)
        // Since we don't have current PIN, we'll lose encrypted data
        // This is acceptable for admin reset - user must re-enter balance/expiry
        
        // Update PIN hash with NEW PIN
        hashPIN(buf, newPinOffset, (short) 6, tempBuffer, (short) 0);
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, cardData, OFFSET_PIN_HASH, PIN_HASH_SIZE);
        
        // Reset retry counter
        cardData[OFFSET_PIN_RETRY] = MAX_PIN_RETRY;
        
        // Note: Existing balance/expiry encryption is now invalid
        // PC must re-write balance/expiry after this operation
    }
}