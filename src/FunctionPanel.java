import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel bên phải - Hiển thị chức năng theo role (Employee / Admin)
 * FIXED: Đọc thẻ sau khi tạo bằng cách verify PIN trước
 */
public class FunctionPanel extends JPanel {
    private final JTextArea logArea;
    private final JPanel controlPanel;
    private final JLabel roleLabel;
    private String currentRole;
    private final PcscClient pcsc;
    private CardData currentCard;

    public FunctionPanel(PcscClient pcsc) {
        this.pcsc = pcsc;
        this.currentRole = "EMPLOYEE"; // Mặc định

        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));

        // Register as card event listener
        CardEventBroadcaster.getInstance().addCardListener(card -> {
            SwingUtilities.invokeLater(() -> displayCardInfo(card));
        });
        
        // Register as purchase approval listener
        CardEventBroadcaster.getInstance().addPurchaseListener((items, totalPrice) -> {
            return showPurchaseApprovalDialog(items, totalPrice);
        });
        
        // Register as topup approval listener
        CardEventBroadcaster.getInstance().addTopupListener((amount, paymentMethod) -> {
            return showTopupApprovalDialog(amount, paymentMethod);
        });

        // Top: Tiêu đề vai trò - Modern gradient
        JPanel topPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, new Color(103, 58, 183), 
                                                           getWidth(), 0, new Color(156, 39, 176));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        topPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 12));
        roleLabel = new JLabel("👤 NHÂN VIÊN");
        roleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        roleLabel.setForeground(Color.WHITE);
        topPanel.add(roleLabel);
        topPanel.setPreferredSize(new Dimension(800, 55));
        add(topPanel, BorderLayout.NORTH);

        // Center: Log area - Modern styling
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(245, 245, 250));
        logArea.setForeground(new Color(30, 40, 50));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        logArea.setText("Sẵn sàng\n");
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 210), 1));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: Control buttons - Modern layout
        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));
        controlPanel.setBackground(new Color(248, 250, 252));
        controlPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 210)));

        // Nút cho Employee (mặc định)
        addEmployeeButtons();

        add(controlPanel, BorderLayout.SOUTH);
    }

    private void addEmployeeButtons() {
        controlPanel.removeAll();
        logArea.setText("Nhân Viên: Quẹt thẻ khách để xem thông tin\n");

        JButton swipeBtn = createModernButton("Quẹt Thẻ Khách", new Color(33, 150, 243));
        swipeBtn.addActionListener(e -> readCustomerCard());
        controlPanel.add(swipeBtn);

        controlPanel.revalidate();
        controlPanel.repaint();
    }

    private void addAdminButtons() {
        controlPanel.removeAll();
        logArea.setText("Admin: Tạo hoặc quẹt thẻ\n");

        JButton createBtn = createModernButton("Tạo Thẻ Mới", new Color(76, 175, 80));
        createBtn.addActionListener(e -> createNewCard());
        controlPanel.add(createBtn);

        JButton swipeBtn = createModernButton("Quẹt Thẻ", new Color(255, 152, 0));
        swipeBtn.addActionListener(e -> readCustomerCard());
        controlPanel.add(swipeBtn);
        
        JButton deleteBtn = createModernButton("Xóa Thẻ", new Color(244, 67, 54));
        deleteBtn.addActionListener(e -> deleteCard());
        controlPanel.add(deleteBtn);
        
        JButton unlockBtn = createModernButton("Mở Khóa", new Color(255, 193, 7));
        unlockBtn.addActionListener(e -> unlockCard());
        controlPanel.add(unlockBtn);
        
        JButton resetPinBtn = createModernButton("Reset PIN", new Color(156, 39, 176));
        resetPinBtn.addActionListener(e -> resetPin());
        controlPanel.add(resetPinBtn);
        
        JButton editInfoBtn = createModernButton("Sửa Thông Tin", new Color(33, 150, 243));
        editInfoBtn.addActionListener(e -> editCardInfo());
        controlPanel.add(editInfoBtn);

        controlPanel.revalidate();
        controlPanel.repaint();
    }

    public void switchRole(String role) {
        this.currentRole = role;

        if (role.equals("ADMIN")) {
            roleLabel.setText("ADMIN");
            addAdminButtons();
        } else {
            roleLabel.setText("NHÂN VIÊN");
            addEmployeeButtons();
        }
    }

    private void readCustomerCard() {
        logArea.setText("");
        logArea.append("[TIẾN HÀNH] Đặt thẻ vào đúng vị trí...\n");

        new Thread(() -> {
            try {
                Thread.sleep(500);
                pcsc.connectFirstPresentOrFirst();
                logArea.append("[OK] Kết nối thẻ thành công!\n\n");

                // Select applet
                javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                        new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
                javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);

                if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                    logArea.append("[LỖI] Không kết nối được applet\n");
                    return;
                }

                // Read
                javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
                javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);

                if ((readResp.getSW() & 0xFF00) != 0x9000) {
                    logArea.append("[LỖI] Không đọc được dữ liệu\n");
                    return;
                }

                currentCard = CardHelper.parseReadResponse(readResp.getData());
                logArea.append("==== THÔNG TIN KHÁCH HÀNG ====\n");
                if (currentCard.fullName != null && !currentCard.fullName.isEmpty()) {
                    logArea.append("👤Họ Tên: " + currentCard.fullName + "\n");
                }
                logArea.append("Ngày Sinh: " + currentCard.getDobString() + "\n");
                logArea.append("ID: " + currentCard.userId + "\n");
                if (currentCard.balance == -1 || currentCard.expiryDays == -1) {
                    logArea.append("Số Dư: [Mã hóa - cần PIN để xem]\n");
                    logArea.append("Hạn Tập: [Mã hóa - cần PIN để xem]\n");
                } else {
                    logArea.append("💰 Số Dư: " + String.format("%,d VND", currentCard.balance) + "\n");
                    logArea.append("📅 Hạn Tập: " + currentCard.expiryDays + " ngày\n");
                }
                
                // Admin info
                if (currentRole.equals("ADMIN")) {
                    logArea.append("\nADMIN INFO:\n");
                    logArea.append("PIN Retry: " + currentCard.pinRetry + "/5\n");
                    String status = currentCard.pinRetry == 0 ? "LOCKED" : "ACTIVE";
                    logArea.append("Status: " + status + "\n");
                }

            } catch (Exception ex) {
                logArea.append("[LỖI] " + ex.getMessage() + "\n");
            }
        }).start();
    }

    private void createNewCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Họ tên (bắt buộc)
        JTextField nameField = new JTextField(20);
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Họ Tên (*):" ), gbc);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        // Ngày sinh (DatePicker)
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Ngày Sinh (*):"), gbc);
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 31, 1));
        JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 12, 1));
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(2000, 1900, 2025, 1));
        daySpinner.setPreferredSize(new Dimension(50, 25));
        monthSpinner.setPreferredSize(new Dimension(50, 25));
        yearSpinner.setPreferredSize(new Dimension(70, 25));
        datePanel.add(new JLabel("Ngày:"));
        datePanel.add(daySpinner);
        datePanel.add(new JLabel("Tháng:"));
        datePanel.add(monthSpinner);
        datePanel.add(new JLabel("Năm:"));
        datePanel.add(yearSpinner);
        gbc.gridx = 1;
        panel.add(datePanel, gbc);

        // Số dư (optional - mặc định 0)
        JTextField balanceField = new JTextField("0");
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Số Dư (VND):"), gbc);
        gbc.gridx = 1;
        panel.add(balanceField, gbc);

        // Hạn tập (optional - mặc định 0)
        JTextField expiryField = new JTextField("0");
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Hạn Tập (ngày):"), gbc);
        gbc.gridx = 1;
        panel.add(expiryField, gbc);

        // PIN (6 chữ số, mặc định 000000)
        JTextField pinField = new JTextField("000000");
        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("PIN (6 số):"), gbc);
        gbc.gridx = 1;
        panel.add(pinField, gbc);

        int option = JOptionPane.showConfirmDialog(this, panel, "Tạo Thẻ Mới", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) return;

        try {
            // Kiểm tra họ tên
            String fullName = nameField.getText().trim();
            if (fullName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Họ tên không được để trống!");
                return;
            }

            // Kiểm tra PIN
            String pinStr = pinField.getText().trim();
            if (!pinStr.matches("\\d{6}")) {
                JOptionPane.showMessageDialog(this, "❌ PIN phải là 6 chữ số!");
                return;
            }

            CardData newCard = new CardData();
            // ID tự động (random hoặc tăng dần)
            newCard.userId = (int) (Math.random() * 65535) + 1;
            newCard.fullName = fullName;
            newCard.balance = Integer.parseInt(balanceField.getText());
            newCard.expiryDays = (short) Integer.parseInt(expiryField.getText());
            
            // DOB
            newCard.dobDay = (byte) ((Integer) daySpinner.getValue()).intValue();
            newCard.dobMonth = (byte) ((Integer) monthSpinner.getValue()).intValue();
            newCard.dobYear = (short) ((Integer) yearSpinner.getValue()).intValue();
            
            // PIN
            newCard.pin = pinStr; // Use full 6-digit string
            newCard.pinRetry = 5; // Default 5 attempts

            logArea.append("\n[BƯỚC 1] Kết nối thẻ...\n");
            pcsc.connectFirstPresentOrFirst();
            logArea.append("[OK] Kết nối thành công!\n");

            // Select applet
            logArea.append("[BƯỚC 2] Chọn applet...\n");
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không chọn được applet (SW: " + 
                             Integer.toHexString(selectResp.getSW()).toUpperCase() + ")\n");
                return;
            }
            logArea.append("[OK] Applet đã sẵn sàng!\n");

            // Check if card is blank by reading
            logArea.append("[BƯỚC 2.5] Kiểm tra trạng thái thẻ...\n");
            javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
            
            if ((readResp.getSW() & 0xFF00) == 0x9000) {
                byte[] data = readResp.getData();
                int existingUserId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                
                if (existingUserId != 0) {
                    logArea.append("[CẢNH BÁO] Thẻ đã có dữ liệu (UserID: " + existingUserId + ")\n");
                    logArea.append("[BƯỚC 2.6] Xóa dữ liệu cũ (reset thẻ)...\n");
                    
                    // Write blank data (UserID=0) to reset card
                    byte[] blankData = new byte[64];
                    blankData[34] = 5; // Reset PIN retry to 5
                    
                    javax.smartcardio.CommandAPDU deleteCmd = 
                        new javax.smartcardio.CommandAPDU(0x00, 0xD0, 0x00, 0x00, blankData);
                    javax.smartcardio.ResponseAPDU deleteResp = pcsc.transmit(deleteCmd);
                    
                    if ((deleteResp.getSW() & 0xFF00) != 0x9000) {
                        logArea.append("[LỖI] Không thể xóa dữ liệu cũ (SW: " + 
                                     Integer.toHexString(deleteResp.getSW()).toUpperCase() + ")\n");
                        return;
                    }
                    logArea.append("[OK] Đã xóa dữ liệu cũ, thẻ đã trống!\n");
                } else {
                    logArea.append("[OK] Thẻ đang trống, sẵn sàng ghi mới\n");
                }
            }

            // Write card data
            logArea.append("[BƯỚC 3] Ghi dữ liệu vào thẻ...\n");
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(newCard);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);

            if ((writeResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Ghi thẻ thất bại (SW: " + 
                             Integer.toHexString(writeResp.getSW()).toUpperCase() + ")\n");
                return;
            }
            
            logArea.append("[OK] Ghi thẻ thành công!\n\n");
            logArea.append("════════════════════════════\n");
            logArea.append("    ✅ TẠO THẺ THÀNH CÔNG\n");
            logArea.append("════════════════════════════\n");
            logArea.append(formatCardInfo(newCard));
            logArea.append("PIN: " + pinStr + "\n");
            logArea.append("════════════════════════════\n");
            
            // Push to Firebase (auto-sync)
            try {
                FirebaseClient firebase = new FirebaseClient();
                firebase.pushCardData(newCard);
            } catch (Exception fbEx) {
                // Silent fail
            }
            
            JOptionPane.showMessageDialog(this, 
                "✅ Tạo thẻ thành công!\n\n" +
                "Họ Tên: " + newCard.fullName + "\n" +
                "ID: " + newCard.userId + "\n" +
                "PIN: " + pinStr,
                "Thành Công",
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception ex) {
            logArea.append("[LỖI NGHIÊM TRỌNG] " + ex.getMessage() + "\n");
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "❌ Lỗi: " + ex.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Helper method to create modern styled buttons for admin panel
     */
    private JButton createModernButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(brighten(bgColor, 20));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(bgColor);
            }
        });
        return btn;
    }

    /**
     * Helper to brighten color on hover
     */
    private Color brighten(Color c, int amount) {
        int r = Math.min(255, c.getRed() + amount);
        int g = Math.min(255, c.getGreen() + amount);
        int b = Math.min(255, c.getBlue() + amount);
        return new Color(r, g, b);
    }
    
    /**
     * Display card info when customer swipes (real-time sync)
     */
    private void displayCardInfo(CardData card) {
        logArea.setText("");
        logArea.append("═══ KHÁCH HÀNG QUẸT THẺ ═══\n\n");
        logArea.append(formatCardInfo(card));
        
        if (currentRole.equals("ADMIN")) {
            logArea.append("\nTrạng thái thẻ:\n");
            logArea.append("Retry Counter: " + card.pinRetry + "/5\n");
            logArea.append("Trạng thái: " + (card.isLocked() ? "Đã khóa" : "Hoạt động") + "\n");
        }
        
        if (card.expiryDays <= 0) {
            logArea.append("\n THẺ HẾT HẠN!\n");
        } else if (card.expiryDays <= 7) {
            logArea.append("\n THẺ SẮP HẾT HẠN!\n");
        }
    }
    
    /**
     * Format card info as string (reusable helper)
     */
    private String formatCardInfo(CardData card) {
        StringBuilder sb = new StringBuilder();
        if (card.fullName != null && !card.fullName.isEmpty()) {
            sb.append("Họ Tên: ").append(card.fullName).append("\n");
        } else {
            sb.append("Họ Tên: N/A\n");
        }
        sb.append("Ngày Sinh: ").append(card.getDobString()).append("\n");
        sb.append("ID: ").append(card.userId).append("\n");
        
        // Handle encrypted balance/expiry
        if (card.balance == -1) {
            sb.append("Số Dư: [Mã hóa - cần PIN để xem]\n");
        } else {
            sb.append("Số Dư: ").append(String.format("%,d", card.balance)).append(" VND\n");
        }
        
        if (card.expiryDays == -1) {
            sb.append("Hạn Tập: [Mã hóa - cần PIN để xem]\n");
        } else {
            sb.append("Hạn Tập: ").append(card.expiryDays).append(" ngày\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Show purchase approval dialog (called from customer window)
     */
    private boolean showPurchaseApprovalDialog(List<CardEventBroadcaster.CartItem> items, int totalPrice) {
        StringBuilder message = new StringBuilder();
        message.append("YÊU CẦU MUA HÀNG TỪ KHÁCH:\n\n");
        for (CardEventBroadcaster.CartItem item : items) {
            message.append("• ").append(item.item.name)
                   .append(" x").append(item.quantity)
                   .append(" = ").append(item.item.price * item.quantity).append(" VND\n");
        }
        message.append("\nTổng cộng: ").append(totalPrice).append(" VND\n\n");
        message.append("Kiểm tra kho và xác nhận?");
        
        int result = JOptionPane.showConfirmDialog(
            this,
            message.toString(),
            "Xác Nhận Mua Hàng",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        boolean approved = (result == JOptionPane.YES_OPTION);
        
        logArea.append("\n" + (approved ? "[✓ CHẤP NHẬN]" : "[✗ TỪ CHỐI]") + " Đơn hàng " + totalPrice + " VND\n");
        
        return approved;
    }
    
    private boolean showTopupApprovalDialog(int amount, String paymentMethod) {
        String icon = paymentMethod.contains("QR") ? "📱" : "💵";
        String message = "YÊU CẦU NẠP TIỀN TỪ KHÁCH:\n\n" +
                        icon + " Phương thức: " + paymentMethod + "\n" +
                        "Số tiền: " + String.format("%,d VND", amount) + "\n\n" +
                        "Xác nhận đã nhận tiền?";
        
        int result = JOptionPane.showConfirmDialog(
            this,
            message,
            "Xác Nhận Nạp Tiền",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        boolean approved = (result == JOptionPane.YES_OPTION);
        
        logArea.append("\n" + (approved ? "[✓ CHẤP NHẬN]" : "[✗ TỪ CHỐI]") + 
                      " Nạp " + String.format("%,d VND", amount) + " (" + paymentMethod + ")\n");
        
        return approved;
    }
    
    /**
     * Delete card - Admin only
     */
    private void deleteCard() {
        logArea.setText("");
        logArea.append("[ADMIN] Xóa thẻ người dùng\n\n");
        
        try {
            pcsc.connectFirstPresentOrFirst();
            logArea.append("[OK] Kết nối thẻ thành công!\n\n");
            
            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            // Read current data first
            javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
            
            if ((readResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Đọc thẻ thất bại\n");
                return;
            }
            
            CardData card = CardHelper.parseReadResponse(readResp.getData());
            
            // Confirm deletion
            String confirmMsg = "XÓA THẺ NGƯỜI DÙNG?\n\n" +
                              formatCardInfo(card) + "\n" +
                              "Hành động này KHÔNG THỂ HOÀN TÁC!";
            
            int confirm = JOptionPane.showConfirmDialog(
                this,
                confirmMsg,
                "Xác Nhận Xóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (confirm != JOptionPane.YES_OPTION) {
                logArea.append("[HỦY] Không xóa thẻ\n");
                return;
            }
            
            // Reset card to zero values
            CardData emptyCard = new CardData();
            emptyCard.userId = 0;
            emptyCard.balance = 0;
            emptyCard.expiryDays = 0;
            emptyCard.pin = "000000"; 
            emptyCard.pinRetry = 5;
            emptyCard.fullName = "";
            emptyCard.dobDay = 0;
            emptyCard.dobMonth = 0;
            emptyCard.dobYear = 0;
            
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(emptyCard);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);
            
            if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                logArea.append("[THÀNH CÔNG] Đã xóa thẻ:\n");
                logArea.append(" Họ Tên: " + (card.fullName != null ? card.fullName : "N/A") + "\n");
                logArea.append(" ID: " + card.userId + "\n");
                logArea.append("Thẻ đã được reset về mặc định\n");

                // Also delete from Firebase server
                try {
                    FirebaseClient firebase = new FirebaseClient();
                    boolean deleted = firebase.deleteCardData(card.userId);
                    if (deleted) {
                        logArea.append("[FIREBASE] ✅ Đã xóa record trên server\n");
                    } else {
                        logArea.append("[FIREBASE] ⚠️ Không xóa được record trên server\n");
                    }
                } catch (Exception fbEx) {
                    logArea.append("[FIREBASE] ⚠️ Lỗi khi xóa trên server: " + fbEx.getMessage() + "\n");
                }
                JOptionPane.showMessageDialog(this, 
                    "Xóa thẻ thành công!\nThẻ đã được reset.",
                    "Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                logArea.append("[LỖI] Xóa thẻ thất bại (SW: " + 
                             Integer.toHexString(writeResp.getSW()).toUpperCase() + ")\n");
            }
            
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }
    
    /**
     * Unlock card - Admin only (reset retry counter without changing PIN)
     */
    private void unlockCard() {
        if (currentCard == null || currentCard.userId == 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        logArea.setText("");
        logArea.append("[ADMIN] Mở khóa thẻ #" + currentCard.userId + "\n\n");
        
        if (currentCard.pinRetry >= 5) {
            JOptionPane.showMessageDialog(this, "Thẻ chưa bị khóa (Retry: " + currentCard.pinRetry + "/5)", 
                "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Mở khóa thẻ cho: " + currentCard.fullName + "\n\n" +
            "Retry hiện tại: " + currentCard.pinRetry + "/5\n" +
            "Sẽ reset về: 5/5\n\n" +
            "Xác nhận mở khóa?",
            "Xác Nhận Mở Khóa",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
            
        if (confirm != JOptionPane.YES_OPTION) {
            logArea.append("[HỦY] Không mở khóa\n");
            return;
        }
        
        try {
            pcsc.connectFirstPresentOrFirst();
            
            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            // Use admin unlock command (no PIN required)
            logArea.append("[BƯỚC 1] Gửi lệnh admin unlock...\n");
            javax.smartcardio.CommandAPDU unlockCmd = CardHelper.buildAdminUnlockCommand();
            javax.smartcardio.ResponseAPDU unlockResp = pcsc.transmit(unlockCmd);
            
            if ((unlockResp.getSW() & 0xFF00) == 0x9000) {
                logArea.append("[✅ THÀNH CÔNG] Đã mở khóa thẻ!\n");
                logArea.append("Retry counter: 5/5\n");
                
                JOptionPane.showMessageDialog(this, 
                    "✅ Mở khóa thành công!\n\n" +
                    "Retry counter đã reset về 5/5",
                    "Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);
                    
                currentCard.pinRetry = 5;
            } else {
                logArea.append("[LỖI] Mở khóa thất bại (SW: " + 
                             Integer.toHexString(unlockResp.getSW()).toUpperCase() + ")\n");
            }
            
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }
    
    /**
     * Reset PIN - Admin only (requires old PIN to re-encrypt balance/expiry)
     */
    private void resetPin() {
        if (currentCard == null || currentCard.userId == 0) {
            JOptionPane.showMessageDialog(this, "❌ Vui lòng quẹt thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        logArea.setText("");
        logArea.append("[ADMIN] Reset PIN cho thẻ #" + currentCard.userId + "\n\n");
        
        String newPin = JOptionPane.showInputDialog(this, 
            "Admin Reset PIN\n" +
            "Reset PIN cho: " + currentCard.fullName + "\n\n" +
            "Nhập PIN mới (6 chữ số):",
            "🔐 PIN Mới",
            JOptionPane.WARNING_MESSAGE);
        
        if (newPin == null) {
            logArea.append("[HỦY] Không reset PIN\n");
            return;
        }
        
        if (!newPin.matches("\\d{6}")) {
            JOptionPane.showMessageDialog(this, "❌ PIN phải là 6 chữ số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "CẢNH BÁO:\n\n" +
            "Bạn cần kiểm tra lại thông tin khách hàng trước khi reset PIN.\n\n" +
            "Xác nhận reset PIN?",
            "Xác Nhận",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (confirm != JOptionPane.YES_OPTION) {
            logArea.append("[HỦY] Không reset PIN\n");
            return;
        }
        
        try {
            pcsc.connectFirstPresentOrFirst();
            
            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            // Use admin reset PIN command (no old PIN required)
            logArea.append("[BƯỚC 1] Gửi lệnh admin reset PIN...\n");
            javax.smartcardio.CommandAPDU resetCmd = CardHelper.buildAdminResetPinCommand(newPin);
            javax.smartcardio.ResponseAPDU resetResp = pcsc.transmit(resetCmd);
            
            if ((resetResp.getSW() & 0xFF00) == 0x9000) {
                logArea.append("[✅ THÀNH CÔNG] Đã reset PIN!\n");
                logArea.append("PIN mới: " + newPin + "\n");
                logArea.append("Retry counter: 5/5\n\n");
                
                // Auto-pull from Firebase
                logArea.append("[FIREBASE] Đang tải balance/expiry từ server...\n");
                try {
                    FirebaseClient firebase = new FirebaseClient();
                    CardFirebaseData fbData = firebase.getCardData(currentCard.userId);
                    
                    currentCard.pin = newPin;
                    currentCard.balance = fbData.balance;
                    currentCard.expiryDays = fbData.expiryDays;
                    currentCard.pinRetry = 5;
                    
                    logArea.append("[FIREBASE] ✅ Đã load từ server!\n");
                    logArea.append("   Balance: " + fbData.balance + " VNĐ\n");
                    logArea.append("   Expiry: " + fbData.expiryDays + " ngày\n");
                    
                } catch (Exception fbEx) {
                    logArea.append("[FIREBASE] ⚠️ Không load được: " + fbEx.getMessage() + "\n");
                    logArea.append("[CẢNH BÁO] Sẽ reset balance/expiry về 0\n");
                    
                    currentCard.pin = newPin;
                    currentCard.balance = 0;
                    currentCard.expiryDays = 0;
                    currentCard.pinRetry = 5;
                    
                    JOptionPane.showMessageDialog(this,
                        "⚠️ Không thể load data từ Firebase!\n\n" +
                        fbEx.getMessage() + "\n\n" +
                        "Balance/Expiry sẽ reset về 0\n" +
                        "Vui lòng dùng 'Sửa Thông Tin' để cập nhật thủ công!",
                        "Cảnh Báo",
                        JOptionPane.WARNING_MESSAGE);
                }
                
                // Verify PIN mới trước khi ghi để tránh SW 69 82
                logArea.append("\n[BƯỚC 2] Xác thực PIN mới...\n");
                javax.smartcardio.CommandAPDU verifyCmd = CardHelper.buildVerifyPinCommand(newPin);
                javax.smartcardio.ResponseAPDU verifyResp = pcsc.transmit(verifyCmd);
                if ((verifyResp.getSW() & 0xFF00) != 0x9000) {
                    logArea.append("[LỖI] Verify PIN mới thất bại (SW: " + Integer.toHexString(verifyResp.getSW()).toUpperCase() + ")\n");
                    JOptionPane.showMessageDialog(this,
                        "❌ Không verify được PIN mới!",
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Ghi dữ liệu sau khi verify thành công
                logArea.append("[BƯỚC 3] Ghi balance/expiry với PIN mới...\n");
                javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(currentCard);
                javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);
                
                if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                    logArea.append("[✅ HOÀN TẤT] Đã cập nhật đầy đủ thông tin!\n");
                    logArea.append("PIN mới: " + newPin + "\n");
                    logArea.append("Balance: " + currentCard.balance + " VNĐ\n");
                    logArea.append("Expiry: " + currentCard.expiryDays + " ngày\n");
                    
                    // Push to Firebase (auto-sync)
                    try {
                        FirebaseClient firebase = new FirebaseClient();
                        firebase.pushCardData(currentCard);
                    } catch (Exception fbEx) {
                        // Silent fail
                    }
                    
                    JOptionPane.showMessageDialog(this,
                        "✅ Reset PIN hoàn tất!\n\n" +
                        "PIN mới: " + newPin + "\n" +
                        "Balance: " + currentCard.balance + " VNĐ\n" +
                        "Expiry: " + currentCard.expiryDays + " ngày\n\n" +
                        "(Đã tự động load từ Firebase)",
                        "Thành Công",
                        JOptionPane.INFORMATION_MESSAGE);
                } else {
                    logArea.append("[LỖI] Không thể ghi balance/expiry (SW: " +
                                 Integer.toHexString(writeResp.getSW()).toUpperCase() + ")\n");
                    JOptionPane.showMessageDialog(this,
                        "⚠️ PIN đã reset nhưng không ghi được balance/expiry\n\n" +
                        "Vui lòng dùng nút 'Sửa Thông Tin' để cập nhật!",
                        "Cảnh Báo",
                        JOptionPane.WARNING_MESSAGE);
                }
            } else {
                logArea.append("[LỖI] Reset PIN thất bại (SW: " + 
                             Integer.toHexString(resetResp.getSW()).toUpperCase() + ")\n");
            }
            
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
            ex.printStackTrace();
        }
    }
    
    /**
     * Edit card info (Name, DOB) - Admin only
     */
    private void editCardInfo() {
        if (currentCard == null || currentCard.userId == 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng quẹt thẻ trước!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        logArea.setText("");
        logArea.append("[ADMIN] Chỉnh sửa thông tin thẻ #" + currentCard.userId + "\n\n");
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Name field
        JTextField nameField = new JTextField(currentCard.fullName, 20);
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Họ Tên:"), gbc);
        gbc.gridx = 1;
        panel.add(nameField, gbc);
        
        // DOB fields
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Ngày Sinh:"), gbc);
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JSpinner daySpinner = new JSpinner(new SpinnerNumberModel((int)currentCard.dobDay, 1, 31, 1));
        JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel((int)currentCard.dobMonth, 1, 12, 1));
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel((int)currentCard.dobYear, 1900, 2025, 1));
        daySpinner.setPreferredSize(new Dimension(50, 25));
        monthSpinner.setPreferredSize(new Dimension(50, 25));
        yearSpinner.setPreferredSize(new Dimension(70, 25));
        datePanel.add(new JLabel("Ngày:"));
        datePanel.add(daySpinner);
        datePanel.add(new JLabel("Tháng:"));
        datePanel.add(monthSpinner);
        datePanel.add(new JLabel("Năm:"));
        datePanel.add(yearSpinner);
        gbc.gridx = 1;
        panel.add(datePanel, gbc);
        
        // Balance/Expiry fields
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Số Dư (VNĐ):"), gbc);
        JTextField balanceField = new JTextField(String.valueOf(currentCard.balance), 15);
        gbc.gridx = 1;
        panel.add(balanceField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Hạn Tập (ngày):"), gbc);
        JTextField expiryField = new JTextField(String.valueOf(currentCard.expiryDays), 15);
        gbc.gridx = 1;
        panel.add(expiryField, gbc);
        
        // Firebase load button
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JButton loadFromFirebaseBtn = new JButton("📥 Load Balance/Expiry từ Firebase");
        loadFromFirebaseBtn.setBackground(new Color(52, 168, 83));
        loadFromFirebaseBtn.setForeground(Color.WHITE);
        loadFromFirebaseBtn.setFocusPainted(false);
        loadFromFirebaseBtn.addActionListener(e -> {
            try {
                FirebaseClient firebase = new FirebaseClient();
                if (!firebase.testConnection()) {
                    int configure = JOptionPane.showConfirmDialog(this,
                        "⚠️ Chưa cấu hình Firebase!\n\n" +
                        "Bạn có muốn cấu hình ngay không?",
                        "Cấu Hình Firebase",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    if (configure == JOptionPane.YES_OPTION) {
                        showFirebaseConfig();
                        firebase = new FirebaseClient(); // Reload config
                    } else {
                        return;
                    }
                }
                
                CardFirebaseData fbData = firebase.getCardData(currentCard.userId);
                balanceField.setText(String.valueOf(fbData.balance));
                expiryField.setText(String.valueOf(fbData.expiryDays));
                
                JOptionPane.showMessageDialog(this,
                    "✅ Đã load từ Firebase!\n\n" +
                    "Balance: " + fbData.balance + " VNĐ\n" +
                    "Expiry: " + fbData.expiryDays + " ngày",
                    "Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "❌ Lỗi kết nối Firebase!\n\n" + ex.getMessage(),
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(loadFromFirebaseBtn, gbc);
        
        int option = JOptionPane.showConfirmDialog(this, panel, 
            "Chỉnh Sửa Thông Tin", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) {
            logArea.append("[HỦY] Không chỉnh sửa\n");
            return;
        }
        
        String newName = nameField.getText().trim();
        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "❌ Họ tên không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            pcsc.connectFirstPresentOrFirst();
            
            // Select applet
            javax.smartcardio.CommandAPDU selectCmd = new javax.smartcardio.CommandAPDU(0x00, 0xA4, 0x04, 0x00,
                    new byte[]{(byte)0x26,(byte)0x12,(byte)0x20,(byte)0x03,(byte)0x03,(byte)0x00});
            javax.smartcardio.ResponseAPDU selectResp = pcsc.transmit(selectCmd);
            if ((selectResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Không thể select applet\n");
                return;
            }
            
            // Read current data
            javax.smartcardio.CommandAPDU readCmd = CardHelper.buildReadCommand();
            javax.smartcardio.ResponseAPDU readResp = pcsc.transmit(readCmd);
            if ((readResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] Đọc thẻ thất bại\n");
                return;
            }
            
            CardData card = CardHelper.parseReadResponse(readResp.getData());
            
            // Update info
            card.fullName = newName;
            card.dobDay = (byte) ((Integer) daySpinner.getValue()).intValue();
            card.dobMonth = (byte) ((Integer) monthSpinner.getValue()).intValue();
            card.dobYear = (short) ((Integer) yearSpinner.getValue()).intValue();
            
            // Update balance/expiry if changed
            try {
                card.balance = Integer.parseInt(balanceField.getText().trim());
                card.expiryDays = (short) Integer.parseInt(expiryField.getText().trim());
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "❌ Balance/Expiry phải là số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Need PIN to write - use default or ask
            if (card.pin == null || card.pin.isEmpty()) {
                String pin = JOptionPane.showInputDialog(this, 
                    "⚠️ Cần PIN để ghi dữ liệu\n\n" +
                    "Nhập PIN của thẻ (6 chữ số):",
                    "Yêu Cầu PIN",
                    JOptionPane.QUESTION_MESSAGE);
                if (pin == null || !pin.matches("\\d{6}")) {
                    logArea.append("[HỦY] Không có PIN hợp lệ\n");
                    return;
                }
                card.pin = pin;
            }
            
            // MUST verify PIN before WRITE (security requirement)
            logArea.append("[BƯỚC 1] Verify PIN trước khi ghi...\n");
            javax.smartcardio.CommandAPDU verifyCmd = CardHelper.buildVerifyPinCommand(card.pin);
            javax.smartcardio.ResponseAPDU verifyResp = pcsc.transmit(verifyCmd);
            if ((verifyResp.getSW() & 0xFF00) != 0x9000) {
                logArea.append("[LỖI] PIN không đúng! (SW: " + 
                             Integer.toHexString(verifyResp.getSW()).toUpperCase() + ")\n");
                JOptionPane.showMessageDialog(this, "❌ PIN không đúng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            logArea.append("[OK] PIN đúng!\n");
            
            // Write back
            logArea.append("[BƯỚC 2] Ghi dữ liệu mới...\n");
            javax.smartcardio.CommandAPDU writeCmd = CardHelper.buildWriteCommand(card);
            javax.smartcardio.ResponseAPDU writeResp = pcsc.transmit(writeCmd);
            
            if ((writeResp.getSW() & 0xFF00) == 0x9000) {
                logArea.append("[✅ THÀNH CÔNG] Đã cập nhật thông tin!\n");
                logArea.append("Họ tên mới: " + card.fullName + "\n");
                logArea.append("Ngày sinh mới: " + card.getDobString() + "\n");
                
                // Push to Firebase (auto-sync)
                try {
                    FirebaseClient firebase = new FirebaseClient();
                    firebase.pushCardData(card);
                } catch (Exception fbEx) {
                    // Silent fail
                }
                
                JOptionPane.showMessageDialog(this, 
                    "✅ Cập nhật thành công!\n\n" +
                    "Họ tên: " + card.fullName + "\n" +
                    "Ngày sinh: " + card.getDobString(),
                    "Thành Công",
                    JOptionPane.INFORMATION_MESSAGE);
                    
                currentCard = card;
            } else {
                logArea.append("[LỖI] Cập nhật thất bại (SW: " + 
                             Integer.toHexString(writeResp.getSW()).toUpperCase() + ")\n");
            }
            
        } catch (Exception ex) {
            logArea.append("[LỖI] " + ex.getMessage() + "\n");
        }
    }
    
    /**
     * Show Firebase configuration dialog
     */
    private void showFirebaseConfig() {
        FirebaseClient firebase = new FirebaseClient();
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Firebase URL
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Database URL:"), gbc);
        JTextField urlField = new JTextField(firebase.getDatabaseUrl() != null ? firebase.getDatabaseUrl() : "", 30);
        gbc.gridx = 1;
        panel.add(urlField, gbc);
        
        // Example hint
        gbc.gridx = 1; gbc.gridy = 1;
        JLabel hintLabel = new JLabel("<html><i>Ví dụ: https://your-project.firebaseio.com</i></html>");
        hintLabel.setForeground(Color.GRAY);
        panel.add(hintLabel, gbc);
        
        // API Key (optional)
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("API Key (tùy chọn):"), gbc);
        JTextField apiKeyField = new JTextField(20);
        gbc.gridx = 1;
        panel.add(apiKeyField, gbc);
        
        int option = JOptionPane.showConfirmDialog(this, panel,
            "⚙️ Cấu Hình Firebase", JOptionPane.OK_CANCEL_OPTION);
            
        if (option == JOptionPane.OK_OPTION) {
            String url = urlField.getText().trim();
            String apiKey = apiKeyField.getText().trim();
            
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Database URL không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            firebase.setDatabaseUrl(url);
            if (!apiKey.isEmpty()) {
                firebase.setApiKey(apiKey);
            }
            
            JOptionPane.showMessageDialog(this,
                "✅ Đã lưu cấu hình Firebase!\n\n" +
                "Database URL: " + url,
                "Thành Công",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
}