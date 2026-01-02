const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');

admin.initializeApp();

/**
 * Firebase Cloud Function: Verify card authenticity with RSA challenge-response
 * 
 * Flow:
 * 1. Client requests challenge
 * 2. Server generates random 32-byte challenge
 * 3. Client signs challenge with card's private key
 * 4. Server verifies signature with stored public key
 */

// Step 1: Generate challenge for card
exports.generateChallenge = functions.https.onCall(async (data, context) => {
  const { userId } = data;
  
  // Validate request
  if (!userId) {
    throw new functions.https.HttpsError('invalid-argument', 'userId is required');
  }
  
  // Generate 32-byte random challenge
  const challenge = crypto.randomBytes(32);
  const challengeBase64 = challenge.toString('base64');
  
  // Store challenge in Realtime Database (expires in 30 seconds)
  await admin.database().ref(`challenges/${userId}`).set({
    challenge: challengeBase64,
    timestamp: Date.now(),
    expiresAt: Date.now() + 30000  // 30 seconds
  });
  
  return { challenge: challengeBase64 };
});

// Step 2: Verify signature from card
exports.verifyCardSignature = functions.https.onCall(async (data, context) => {
  const { userId, signature } = data;
  
  // Validate request
  if (!userId || !signature) {
    throw new functions.https.HttpsError('invalid-argument', 'userId and signature are required');
  }
  
  try {
    // 1. Get stored challenge
    const challengeSnapshot = await admin.database().ref(`challenges/${userId}`).once('value');
    const challengeData = challengeSnapshot.val();
    
    if (!challengeData) {
      throw new functions.https.HttpsError('not-found', 'Challenge not found or expired');
    }
    
    // Check if challenge expired
    if (Date.now() > challengeData.expiresAt) {
      await admin.database().ref(`challenges/${userId}`).remove();
      throw new functions.https.HttpsError('deadline-exceeded', 'Challenge expired');
    }
    
    // 2. Get card's public key from database
    const cardSnapshot = await admin.database().ref(`cards/${userId}`).once('value');
    const cardData = cardSnapshot.val();
    
    if (!cardData || !cardData.publicKey) {
      throw new functions.https.HttpsError('not-found', 'Card not found');
    }
    
    // 3. Parse RSA public key
    const publicKeyPem = cardData.publicKey;
    const verifier = crypto.createVerify('RSA-SHA256');
    
    // 4. Verify signature
    const challengeBuffer = Buffer.from(challengeData.challenge, 'base64');
    const signatureBuffer = Buffer.from(signature, 'base64');
    
    verifier.update(challengeBuffer);
    const isValid = verifier.verify(publicKeyPem, signatureBuffer);
    
    // 5. Clean up challenge
    await admin.database().ref(`challenges/${userId}`).remove();
    
    if (isValid) {
      // Update last verified timestamp
      await admin.database().ref(`cards/${userId}/lastVerified`).set(Date.now());
      
      return {
        verified: true,
        message: 'Card is authentic',
        cardData: {
          fullName: cardData.fullName,
          balance: cardData.balance,
          expiryDays: cardData.expiryDays
        }
      };
    } else {
      return {
        verified: false,
        message: 'Invalid signature - card may be fake'
      };
    }
    
  } catch (error) {
    console.error('Verification error:', error);
    throw new functions.https.HttpsError('internal', error.message);
  }
});

// Helper: Store card public key when creating new card
exports.registerCard = functions.https.onCall(async (data, context) => {
  const { userId, fullName, dob, balance, expiryDays, publicKey } = data;
  
  // Validate
  if (!userId || !publicKey) {
    throw new functions.https.HttpsError('invalid-argument', 'userId and publicKey are required');
  }
  
  // Convert raw public key bytes to PEM format
  const publicKeyPem = convertToPEM(publicKey);
  
  // Store in database
  await admin.database().ref(`cards/${userId}`).set({
    fullName: fullName || '',
    dob: dob || '',
    balance: balance || 0,
    expiryDays: expiryDays || 0,
    publicKey: publicKeyPem,
    createdAt: Date.now(),
    lastVerified: Date.now()
  });
  
  return { success: true, message: 'Card registered successfully' };
});

/**
 * Convert raw RSA public key bytes to PEM format
 * Input: Base64 string of [Modulus 128 bytes][Exponent 3 bytes]
 */
function convertToPEM(publicKeyBase64) {
  const keyBytes = Buffer.from(publicKeyBase64, 'base64');
  
  // Extract modulus (128 bytes) and exponent (3 bytes)
  const modulus = keyBytes.slice(0, 128);
  const exponent = keyBytes.slice(128, 131);
  
  // Create RSA public key object
  const key = crypto.createPublicKey({
    key: {
      n: modulus,
      e: exponent
    },
    format: 'jwk'
  });
  
  // Export as PEM
  return key.export({ type: 'spki', format: 'pem' });
}
