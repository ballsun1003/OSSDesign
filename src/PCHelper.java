// PCHelper.java (단일 파일 최종 수정 버전)
// 모든 클래스가 이 파일 하나에 포함되어 있습니다.

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// 각 유틸리티의 정보를 담는 데이터 클래스
class UtilityInfo {
    final String name;
    final String path;
    final boolean needsAdmin;
    final String helpText;
    final int difficulty;

    UtilityInfo(String name, String path, boolean needsAdmin, String helpText, int difficulty) {
        this.name = name;
        this.path = path;
        this.needsAdmin = needsAdmin;
        this.helpText = helpText;
        this.difficulty = difficulty;
    }
}

// 메인 클래스. 프로그램의 시작점 역할을 합니다.
public class PCHelper {

    public static void main(String[] args) {
        // UI가 운영체제에 맞게 보이도록 Look and Feel 설정
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            // 설계 문서의 Class Diagram을 기반으로 주요 객체 생성
            FileIOManager fileIOManager = new FileIOManager();
            String apiKey = fileIOManager.readApiKey(); // 파일에서 API 키 읽기
            APIManager apiManager = new APIManager(apiKey);
            ExternalExecutor externalExecutor = new ExternalExecutor();
            NotificationManager notificationManager = new NotificationManager(fileIOManager);
            UtilityManager utilityManager = new UtilityManager(externalExecutor, apiManager, fileIOManager, notificationManager);

            // 시스템 트레이 아이콘 설정
            if (!SystemTray.isSupported()) {
                System.err.println("시스템 트레이를 지원하지 않습니다.");
                utilityManager.showGUI(0); // 트레이를 지원하지 않으면 바로 메인 GUI 표시
                return;
            }

            PopupMenu trayMenu = new PopupMenu();
            MenuItem showItem = new MenuItem("PC HELPER 열기");
            showItem.addActionListener(e -> utilityManager.showGUI(0));

            MenuItem exitItem = new MenuItem("종료");
            exitItem.addActionListener(e -> System.exit(0));

            trayMenu.add(showItem);
            trayMenu.addSeparator();
            trayMenu.add(exitItem);

            Image image = new ImageIcon("icon.png", "PC Helper 아이콘").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            TrayIcon trayIcon = new TrayIcon(image, "PC HELPER", trayMenu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> utilityManager.showGUI(0));

            try {
                SystemTray.getSystemTray().add(trayIcon);
                notificationManager.setTrayIcon(trayIcon);
            } catch (AWTException e) {
                System.err.println("트레이 아이콘을 추가할 수 없습니다.");
            }

            // 타이머 생성
            Timer manageTimer = new Timer("manageTimer", 30);
            Timer scanTimer = new Timer("scanTimer", 1);

            // 백그라운드 스레드 실행
            new Thread(() -> {
                while (true) {
                    try {
                        if (manageTimer.checkTimer()) {
                            notificationManager.showManagementReminder(utilityManager);
                            manageTimer.resetTimer();
                        }
                        if (scanTimer.checkTimer()) {
                            List<String> errors = fileIOManager.scanEvtForErrors();
                            if (!errors.isEmpty()) {
                                notificationManager.showErrorNotification(utilityManager, errors);
                            }
                            scanTimer.resetTimer();
                        }
                        Thread.sleep(3600 * 1000); // 1시간마다 체크
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                }
            }).start();

            System.out.println("PC HELPER가 백그라운드에서 실행 중입니다.");
        });
    }
}

// GUI 표시와 페이지 전환 등 사용자 인터페이스를 총괄하는 클래스
class UtilityManager {
    private final ExternalExecutor exe;
    private final APIManager aiQuery;
    private final FileIOManager fileIOManager;
    private JFrame mainFrame;
    private SwingWorker<?, ?> currentWorker = null;
    private List<? extends RowSorter.SortKey> cleanerSortKeys; // 파일 정리기 정렬 상태 저장

    public UtilityManager(ExternalExecutor exe, APIManager aiQuery, FileIOManager fileIOManager, NotificationManager notificationManager) {
        this.exe = exe;
        this.aiQuery = aiQuery;
        this.fileIOManager = fileIOManager;
    }

    public void showGUI(int pageNum) {
        if (mainFrame == null) {
            mainFrame = new JFrame();
            mainFrame.setSize(800, 600);
            mainFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            mainFrame.setLocationRelativeTo(null);
        }
        mainFrame.setVisible(true);
        mainFrame.toFront();

        // 페이지 전환 시 진행중이던 작업 취소
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        switch (pageNum) {
            case 1: showToolBox(); break;
            case 2: showAIHelper(); break;
            case 3: showCleaner(); break;
            case 4: showErrorLog(fileIOManager.getSavedErrors()); break;
            default: showMainPage(); break;
        }
    }

    private void refreshUI() {
        mainFrame.revalidate();
        mainFrame.repaint();
    }

    private JButton createBackButton(Runnable action) {
        JButton backButton = new JButton("뒤로 가기");
        backButton.addActionListener(e -> action.run());
        return backButton;
    }

    private void showMainPage() {
        mainFrame.setTitle("PC HELPER");
        Container pane = mainFrame.getContentPane();
        pane.removeAll();
        pane.setLayout(new GridBagLayout());

        JButton toolboxButton = new JButton("도구 상자");
        toolboxButton.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        toolboxButton.addActionListener(e -> showToolBox());

        JButton aiButton = new JButton("AI 도우미");
        aiButton.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        aiButton.addActionListener(e -> showAIHelper());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        pane.add(toolboxButton, gbc);
        gbc.gridy = 1;
        pane.add(aiButton, gbc);

        refreshUI();
    }

    private void showToolBox() {
        mainFrame.setTitle("PC HELPER - 도구 상자");
        Container pane = mainFrame.getContentPane();
        pane.removeAll();

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridLayout(0, 1, 5, 5));

        // utils.txt 파일에서 유틸리티 목록 읽어오기
        List<UtilityInfo> utilities = fileIOManager.readUtilities();

        for (UtilityInfo util : utilities) {
            Color color;
            switch (util.difficulty) {
                case 1: color = new Color(173, 216, 230); break; // 쉬움
                case 2: color = new Color(144, 238, 144); break; // 보통
                case 3: color = new Color(255, 228, 181); break; // 어려움
                case 4: color = new Color(255, 182, 193); break; // 전문가
                default: color = Color.LIGHT_GRAY;
            }

            // 각 버튼에 대한 액션 생성
            Runnable action = () -> {
                if ("showErrorLog".equalsIgnoreCase(util.path)) {
                    showErrorLog(fileIOManager.getSavedErrors());
                } else if ("showCleaner".equalsIgnoreCase(util.path)) {
                    showCleaner();
                } else {
                    // 외부 프로그램 실행
                    boolean isExpert = (util.difficulty == 4);
                    exe.execute(util.path, util.needsAdmin, isExpert);
                }
            };
            addToolBoxButton(contentPanel, util.name, color, util.helpText, action);
        }

        pane.setLayout(new BorderLayout());
        pane.add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        pane.add(createBackButton(this::showMainPage), BorderLayout.SOUTH);

        refreshUI();
    }

    private void addToolBoxButton(JPanel parent, String name, Color color, String helpText, Runnable action) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JButton button = new JButton(name);
        button.setBackground(color);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFont(new Font("Malgun Gothic", Font.PLAIN, 14));
        button.addActionListener(e -> action.run());

        JButton helpButton = new JButton("?");
        helpButton.setMargin(new Insets(2, 8, 2, 8));
        helpButton.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        helpButton.addActionListener(e -> JOptionPane.showMessageDialog(mainFrame, helpText, name + " 도움말", JOptionPane.INFORMATION_MESSAGE));

        panel.add(button, BorderLayout.CENTER);
        panel.add(helpButton, BorderLayout.EAST);
        parent.add(panel);
    }

    private void showAIHelper() {
        mainFrame.setTitle("PC HELPER - AI 도우미");
        Container pane = mainFrame.getContentPane();
        pane.removeAll();
        pane.setLayout(new BorderLayout(5, 5));

        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JTextArea inputArea = new JTextArea(3, 20); // 3줄 높이, 20컬럼 너비의 입력창
        inputArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 14));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBackground(new Color(240, 240, 240)); // 회색 배경
        JScrollPane inputScrollPane = new JScrollPane(inputArea);

        JButton sendButton = new JButton("전송");
        sendButton.addActionListener(e -> {
            String userText = inputArea.getText();
            if (userText.trim().isEmpty()) return;

            chatArea.append("사용자: " + userText + "\n\n");
            inputArea.setText("");

            new Thread(() -> {
                String response = aiQuery.sendQuery(userText);
                SwingUtilities.invokeLater(() -> chatArea.append("AI 도우미: " + response + "\n\n"));
            }).start();
        });

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputScrollPane, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        pane.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        pane.add(bottomPanel, BorderLayout.SOUTH);
        pane.add(createBackButton(this::showMainPage), BorderLayout.NORTH);

        refreshUI();
    }

    private void updateCleanerUI(Container pane, File directory) {
        CardLayout cardLayout = new CardLayout();
        JPanel mainPanel = new JPanel(cardLayout);

        // 로딩 패널
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        JLabel loadingLabel = new JLabel("파일 목록을 읽는 중입니다...");
        loadingLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        JButton cancelButton = new JButton("취소");

        JPanel loadingContent = new JPanel();
        loadingContent.setLayout(new BoxLayout(loadingContent, BoxLayout.Y_AXIS));
        loadingContent.add(loadingLabel);
        loadingContent.add(Box.createRigidArea(new Dimension(0, 10)));
        loadingContent.add(progressBar);
        loadingContent.add(Box.createRigidArea(new Dimension(0, 10)));
        loadingContent.add(cancelButton);
        loadingPanel.add(loadingContent);

        // 파일 목록 패널
        JPanel fileListPanel = new JPanel(new BorderLayout(5, 5));

        mainPanel.add(loadingPanel, "loading");
        mainPanel.add(fileListPanel, "files");

        pane.removeAll();
        pane.add(mainPanel, BorderLayout.CENTER);
        cardLayout.show(mainPanel, "loading");
        refreshUI();

        currentWorker = new SwingWorker<List<File>, Void>() {
            @Override
            protected List<File> doInBackground() throws Exception {
                return fileIOManager.scanDirectory(directory);
            }
            @Override
            protected void done() {
                if (isCancelled()) {
                    showToolBox();
                    return;
                }
                try {
                    List<File> files = get();
                    setupCleanerUI(fileListPanel, directory, files);
                    cardLayout.show(mainPanel, "files");
                } catch (Exception e) {
                    e.printStackTrace();
                    cardLayout.show(mainPanel, "files");
                }
            }
        };

        cancelButton.addActionListener(e -> currentWorker.cancel(true));
        currentWorker.execute();
    }

    private void setupCleanerUI(JPanel panel, File directory, List<File> files) {
        panel.removeAll();

        // 상단 컨트롤 패널
        JPanel topPanel = new JPanel(new BorderLayout());
        JTextField pathField = new JTextField(directory.getAbsolutePath());
        pathField.setEditable(false);
        JButton upButton = new JButton("위로");
        upButton.setEnabled(directory.getParentFile() != null);
        upButton.addActionListener(e -> updateCleanerUI(mainFrame.getContentPane(), directory.getParentFile()));
        topPanel.add(upButton, BorderLayout.WEST);
        topPanel.add(pathField, BorderLayout.CENTER);

        // 하단 컨트롤 패널
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton sortByName = new JButton("이름순"), sortBySize = new JButton("크기순"), sortByDate = new JButton("날짜순"), deleteButton = new JButton("선택 파일 삭제");
        controlPanel.add(sortByName);
        controlPanel.add(sortBySize);
        controlPanel.add(sortByDate);
        controlPanel.add(deleteButton);

        String[] columnNames = {"선택", "이름", "크기", "수정 날짜"};
        DefaultTableModel tableModel = new DefaultTableModel(null, columnNames) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 0: return Boolean.class;
                    case 1: return File.class;
                    case 2:
                    case 3: return Long.class;
                    default: return Object.class;
                }
            }
            @Override
            public boolean isCellEditable(int row, int column) { return column == 0; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(50);

        AtomicLong maxFileSize = new AtomicLong(0);
        table.getColumnModel().getColumn(1).setCellRenderer(new FileCellRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new SizeBarRenderer(maxFileSize));
        table.getColumnModel().getColumn(3).setCellRenderer(new DateCellRenderer());

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // 정렬 상태 유지
        if (cleanerSortKeys != null) {
            sorter.setSortKeys(cleanerSortKeys);
        }
        sorter.addRowSorterListener(e -> {
            if (e.getType() == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
                cleanerSortKeys = sorter.getSortKeys();
            }
        });

        sortByName.addActionListener(e -> sorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING))));
        sortBySize.addActionListener(e -> sorter.setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.DESCENDING))));
        sortByDate.addActionListener(e -> sorter.setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.DESCENDING))));

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.convertRowIndexToModel(table.getSelectedRow());
                    File file = (File) tableModel.getValueAt(row, 1);
                    if (file.isDirectory()) {
                        updateCleanerUI(mainFrame.getContentPane(), file);
                    }
                }
            }
        });

        deleteButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(mainFrame, "선택한 항목들을 정말 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.", "삭제 확인", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                for (int i = tableModel.getRowCount() - 1; i >= 0; i--) {
                    if ((Boolean) tableModel.getValueAt(i, 0)) {
                        fileIOManager.deleteFile((File) tableModel.getValueAt(i, 1));
                        tableModel.removeRow(i);
                    }
                }
            }
        });

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(controlPanel, BorderLayout.CENTER);
        bottomPanel.add(createBackButton(this::showToolBox), BorderLayout.EAST);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        tableModel.setRowCount(0);
        for (File file : files) {
            tableModel.addRow(new Object[]{false, file, -1L, file.lastModified()});
        }

        currentWorker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                long currentMax = 0;
                List<Long> sizes = new ArrayList<>();
                for(int i=0; i<files.size(); i++){
                    if (isCancelled()) break;
                    File file = files.get(i);
                    long size = fileIOManager.getFileSize(file);
                    sizes.add(size);
                    if (size > currentMax) currentMax = size;
                }
                maxFileSize.set(currentMax);
                for (int i = 0; i < sizes.size(); i++) {
                    if (isCancelled()) break;
                    publish(new Object[]{i, sizes.get(i)});
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] chunk : chunks) {
                    tableModel.setValueAt(chunk[1], (Integer) chunk[0], 2);
                }
            }

            @Override
            protected void done() {
                if (!isCancelled()) {
                    table.repaint();
                }
            }
        };
        currentWorker.execute();

        refreshUI();
    }

    private void showCleaner() {
        mainFrame.setTitle("PC HELPER - 파일 정리");
        updateCleanerUI(mainFrame.getContentPane(), new File(System.getProperty("user.home")));
    }

    private void showErrorLog(List<String> errors) {
        mainFrame.setTitle("PC HELPER - 오류 기록 보기");
        Container pane = mainFrame.getContentPane();
        pane.removeAll();
        pane.setLayout(new BorderLayout());

        JTextArea errorArea = new JTextArea();
        errorArea.setEditable(false);
        errorArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        if (errors == null || errors.isEmpty()) {
            errorArea.setText("저장된 오류 기록이 없습니다.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String error : errors) {
                sb.append(error).append("\n---------------------------------\n");
            }
            errorArea.setText(sb.toString());
        }

        pane.add(new JScrollPane(errorArea), BorderLayout.CENTER);
        pane.add(createBackButton(this::showToolBox), BorderLayout.SOUTH);

        refreshUI();
    }
}

// 프로그램의 모든 알림 기능을 담당하는 클래스
class NotificationManager {
    private TrayIcon trayIcon;
    private final FileIOManager fileIOManager;

    public NotificationManager(FileIOManager fileIOManager) { this.fileIOManager = fileIOManager; }
    public void setTrayIcon(TrayIcon trayIcon) { this.trayIcon = trayIcon; }

    public void showManagementReminder(UtilityManager utilityManager) {
        String message = "컴퓨터 정리를 안 한지 30일이 지났습니다!";
        String[] options = {"도구 상자 열기", "다음에 알림"};
        int choice = JOptionPane.showOptionDialog(null, message, "PC 관리 알림", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 0) utilityManager.showGUI(1);
    }

    public void showErrorNotification(UtilityManager utilityManager, List<String> errors) {
        fileIOManager.saveErrors(errors);
        String message = "컴퓨터에 심각한 오류 기록이 있습니다!";
        if (trayIcon != null) {
            trayIcon.displayMessage("오류 알림", message, TrayIcon.MessageType.ERROR);
            trayIcon.addActionListener(e -> utilityManager.showGUI(4));
        } else {
            String[] options = {"오류 기록 보기", "나중에 보기"};
            int choice = JOptionPane.showOptionDialog(null, message, "오류 알림", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (choice == 0) utilityManager.showGUI(4);
        }
    }
}

// 외부 LLM API(Gemini)와의 통신을 관리하는 클래스
class APIManager {
    private final String apiKey;
    private final String promptSuffix;

    public APIManager(String apiKey) {
        this.apiKey = apiKey;
        this.promptSuffix = " (당신은 PC 문제 해결 전문가입니다. 한국어로 친절하고 명확하게 설명해주세요.)";
    }

    public String sendQuery(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "API 키가 설정되지 않았습니다. 프로그램 폴더에 APIKey.txt 파일을 생성하고 키를 입력해주세요.";
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + apiKey;
        String escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n");
        String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapedPrompt + promptSuffix + "\"}]}]}";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                String searchText = "\"text\": \"";
                int startIndex = responseBody.indexOf(searchText);
                if (startIndex != -1) {
                    startIndex += searchText.length();
                    int endIndex = responseBody.indexOf("\"", startIndex);
                    if (endIndex != -1) {
                        return responseBody.substring(startIndex, endIndex).replace("\\n", "\n");
                    }
                }
                return "답변을 파싱하는 데 실패했습니다: " + responseBody;
            } else {
                return "API 호출 실패. 상태 코드: " + response.statusCode() + "\n" + response.body();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "API 요청 중 오류 발생: " + e.getMessage();
        }
    }
}

// 파일 시스템 관련 작업을 담당하는 클래스
class FileIOManager {
    private List<String> savedErrors = new ArrayList<>();
    private final String errorLogFile = "error_log.dat";

    public FileIOManager() {
        loadErrors();
        createDefaultUtilsFileIfNeeded();
    }

    public String readApiKey() {
        try {
            return Files.readString(Paths.get("APIKey.txt"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("APIKey.txt 파일을 찾을 수 없습니다.");
            return null;
        }
    }

    public List<UtilityInfo> readUtilities() {
        File utilsFile = new File("utils.txt");
        List<UtilityInfo> utilities = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(utilsFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",", 5);
                if (parts.length == 5) {
                    boolean needsAdmin = "1".equals(parts[2].trim());
                    utilities.add(new UtilityInfo(parts[0].trim(), parts[1].trim(), needsAdmin, parts[3].trim(), Integer.parseInt(parts[4].trim())));
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "utils.txt 파일을 읽는 중 오류 발생.", "파일 오류", JOptionPane.ERROR_MESSAGE);
        }
        return utilities;
    }

    private void createDefaultUtilsFileIfNeeded() {
        File utilsFile = new File("utils.txt");
        if (!utilsFile.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(utilsFile, StandardCharsets.UTF_8))) {
                writer.println("# 유틸리티 목록: 이름,경로,관리자권한(1=필요),도움말,난이도(1~4)");
                writer.println("오류 기록 보기,showErrorLog,0,저장된 시스템 오류 기록을 확인합니다.,1");
                writer.println("디스크 정리,cleanmgr.exe,0,Windows 디스크 정리 유틸리티를 실행합니다.,1");
                writer.println("사용자 파일 정리,showCleaner,0,불필요한 파일을 찾아 정리합니다.,2");
                writer.println("디스크 조각 모음 및 최적화,dfrgui.exe,1,디스크를 최적화하여 성능을 향상시킵니다.,2");
                writer.println("장치 관리자,mmc.exe devmgmt.msc,1,하드웨어 장치를 관리합니다.,3");
                writer.println("작업 관리자,taskmgr.exe,0,현재 실행 중인 프로세스를 확인하고 관리합니다.,3");
                writer.println("레지스트리 편집기,regedit.exe,1,주의: 시스템 설정을 직접 수정하는 전문가용 도구입니다.,4");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> scanEvtForErrors() {
        List<String> errors = new ArrayList<>();
        // **오류 수정**: chcp 65001 명령으로 콘솔을 UTF-8 모드로 변경 후 wevtutil 실행
        String command = "cmd /c chcp 65001 > nul && wevtutil qe System /q:\"*[System[(Level=1) and TimeCreated[timediff(@SystemTime) <= 86400000]]]\" /c:5 /f:text";
        try {
            Process process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) errors.add(line);
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("이벤트 로그 스캔 오류: " + e.getMessage());
        }
        return errors;
    }

    public void saveErrors(List<String> errors) {
        this.savedErrors.addAll(errors);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(errorLogFile))) {
            oos.writeObject(this.savedErrors);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @SuppressWarnings("unchecked")
    private void loadErrors() {
        if (new File(errorLogFile).exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(errorLogFile))) {
                this.savedErrors = (List<String>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) { e.printStackTrace(); }
        }
    }

    public List<String> getSavedErrors() { return Collections.unmodifiableList(this.savedErrors); }

    public List<File> scanDirectory(File directory) {
        List<File> fileList = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) return fileList;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isHidden()) fileList.add(file);
            }
        }
        return fileList;
    }

    public long getFileSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();

        long length = 0;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                try {
                    if (f.isFile()) {
                        length += f.length();
                    } else if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
                        length += getFileSize(f);
                    }
                } catch (Exception e) {
                    System.err.println("파일 크기 계산 중 오류 (건너뜀): " + f.getAbsolutePath());
                }
            }
        }
        return length;
    }

    public void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) deleteFile(f);
            }
        }
        if (!file.delete()) System.err.println(file.getAbsolutePath() + " 파일 삭제 실패");
    }
}

// 외부 유틸리티 프로그램을 실행하는 클래스
class ExternalExecutor {

    public void execute(String command, boolean needsAdmin, boolean isExpertTool) {
        if (isExpertTool) {
            int result = JOptionPane.showConfirmDialog(null,
                    "이 도구는 시스템에 심각한 손상을 줄 수 있는 전문가용 기능입니다.\n계속하시겠습니까?",
                    "경고", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) return;
        }

        try {
            if (needsAdmin) {
                executeAsAdmin(command);
            } else {
                Runtime.getRuntime().exec("cmd /c " + command);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "유틸리티 실행 실패: " + e.getMessage(), "실행 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void executeAsAdmin(String command) throws IOException {
        String[] commandParts = command.split(" ", 2);
        String executable = commandParts[0];
        String args = commandParts.length > 1 ? commandParts[1] : "";

        String script = "Set objShell = CreateObject(\"Shell.Application\")\n"
                + "objShell.ShellExecute \"" + executable + "\", \"" + args + "\", \"\", \"runas\", 1";

        File vbsFile = File.createTempFile("runas", ".vbs");
        vbsFile.deleteOnExit();

        try (PrintWriter writer = new PrintWriter(vbsFile)) {
            writer.print(script);
        }

        new ProcessBuilder("wscript.exe", vbsFile.getAbsolutePath()).start();
    }
}

// 주기 확인용 타이머 클래스
class Timer {
    private final String name;
    private final int interval;
    private LocalDate resetDate;

    public Timer(String name, int interval) {
        this.name = name;
        this.interval = interval;
        this.resetDate = loadLastResetDate();
    }

    public boolean checkTimer() {
        return ChronoUnit.DAYS.between(resetDate, LocalDate.now()) >= interval;
    }

    public void resetTimer() {
        this.resetDate = LocalDate.now();
        saveLastResetDate();
    }

    private void saveLastResetDate() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(name + ".dat"))) {
            writer.write(resetDate.toString());
        } catch (IOException e) { e.printStackTrace(); }
    }

    private LocalDate loadLastResetDate() {
        File file = new File(name + ".dat");
        if (file.exists()) {
            try {
                return LocalDate.parse(Files.readString(Paths.get(file.getPath())));
            } catch (Exception e) {
                System.err.println(name + ".dat 파일 로딩 실패.");
            }
        }
        LocalDate today = LocalDate.now();
        this.resetDate = today;
        saveLastResetDate();
        return today;
    }
}

// 파일 정리 기능의 테이블 셀 렌더러 (아이콘, 이름 표시)
class FileCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof File) {
            File file = (File) value;
            setIcon(FileSystemView.getFileSystemView().getSystemIcon(file));
            setText(file.getName());
        }
        return this;
    }
}

// 파일 정리 기능의 테이블 셀 렌더러 (크기 막대그래프 표시)
class SizeBarRenderer extends JProgressBar implements TableCellRenderer {
    private final AtomicLong maxFileSize;

    public SizeBarRenderer(AtomicLong maxFileSize) {
        super(0, 100);
        this.maxFileSize = maxFileSize;
        setStringPainted(true);
        setBorderPainted(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof Long) {
            long size = (Long) value;
            if (size == -1L) { // 아직 계산 중
                setString("계산 중...");
                setValue(0);
            } else {
                long max = maxFileSize.get();
                setValue(max > 0 ? (int) (((double) size / max) * 100.0) : 0);
                setString(formatSize(size));
            }
        }
        return this;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }
}

// 파일 정리 기능의 테이블 셀 렌더러 (날짜 표시)
class DateCellRenderer extends DefaultTableCellRenderer {
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (c instanceof JLabel && value instanceof Long) {
            ((JLabel) c).setText(formatter.format(new Date((Long) value)));
        }
        return c;
    }
}
