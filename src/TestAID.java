import javax.smartcardio.*;

public class TestAID {
    public static void main(String[] args) {
        // Danh sách AID để thử
        byte[][] testAIDs = {
            // AID hiện tại
            {(byte)0x26, (byte)0x12, (byte)0x20, (byte)0x03, (byte)0x20, (byte)0x03, (byte)0x00},
            
            // Default JavaCard test AIDs
            {(byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05},
            {(byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07},
            
            // Common RID prefixes
            {(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x62, (byte)0x03, (byte)0x01},
            {(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x00, (byte)0x00},
        };
        
        try {
            PcscClient pcsc = new PcscClient();
            pcsc.connectFirstPresentOrFirst();
            System.out.println("✅ Connected to card");
            System.out.println();
            
            for (int i = 0; i < testAIDs.length; i++) {
                byte[] aid = testAIDs[i];
                System.out.printf("Testing AID #%d: %s\n", i+1, PcscClient.toHex(aid));
                
                try {
                    CommandAPDU selectCmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid);
                    ResponseAPDU resp = pcsc.transmit(selectCmd);
                    
                    System.out.printf("  >> %s\n", PcscClient.toHex(selectCmd.getBytes()));
                    System.out.printf("  << SW: %04X", resp.getSW());
                    
                    if (resp.getSW() == 0x9000) {
                        System.out.println(" ✅ SUCCESS! Applet found!");
                        System.out.printf("  Response data: %s\n", PcscClient.toHex(resp.getData()));
                        System.out.println();
                        System.out.println("🎯 Use this AID in your code:");
                        System.out.printf("   new byte[]{");
                        for (int j = 0; j < aid.length; j++) {
                            System.out.printf("(byte)0x%02X", aid[j]);
                            if (j < aid.length - 1) System.out.print(", ");
                        }
                        System.out.println("}");
                        break;
                    } else if (resp.getSW() == 0x6A82) {
                        System.out.println(" ❌ Not found (6A82)");
                    } else {
                        System.out.printf(" ⚠️ Other error\n");
                    }
                } catch (Exception e) {
                    System.out.println("  ❌ Error: " + e.getMessage());
                }
                System.out.println();
            }
            
            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("❌ No applet found with tested AIDs");
            System.out.println("Possible reasons:");
            System.out.println("  1. Applet not installed on card");
            System.out.println("  2. Different AID was used during installation");
            System.out.println("  3. Card is blank or applet was deleted");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. Use JCIDE or GPShell to list installed applets");
            System.out.println("  2. Build and install SmartCard.java with JCIDE");
            System.out.println("  3. Make sure AID in install script matches your code");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
