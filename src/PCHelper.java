// PCHelper.java (단일 파일 버전)
// 모든 클래스가 이 파일 하나에 포함되어 있습니다.

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;


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
            // 설계상 Gemini API를 사용하기로 함
            APIManager apiManager = new APIManager("YOUR_GEMINI_API_KEY"); // TODO: 실제 Gemini API 키를 입력하세요.
            ExternalExecutor externalExecutor = new ExternalExecutor();
            NotificationManager notificationManager = new NotificationManager(fileIOManager);
            UtilityManager utilityManager = new UtilityManager(externalExecutor, apiManager, fileIOManager, notificationManager);

            // 시스템 트레이 아이콘 설정
            if (!SystemTray.isSupported()) {
                System.err.println("시스템 트레이를 지원하지 않습니다.");
                // 트레이를 지원하지 않으면 바로 메인 GUI를 띄웁니다.
                utilityManager.showGUI(0);
                return;
            }

            // 트레이 아이콘 클릭 시 나타날 팝업 메뉴 생성
            PopupMenu trayMenu = new PopupMenu();
            MenuItem showItem = new MenuItem("PC HELPER 열기");
            showItem.addActionListener(e -> utilityManager.showGUI(0)); // 메인 페이지 열기

            MenuItem exitItem = new MenuItem("종료");
            exitItem.addActionListener(e -> {
                System.out.println("프로그램을 종료합니다.");
                System.exit(0); // 프로그램 완전 종료
            });

            trayMenu.add(showItem);
            trayMenu.addSeparator();
            trayMenu.add(exitItem);

            // 아이콘 이미지 로드 (실행 파일과 같은 경로에 icon.png 파일이 필요합니다)
            Image image = new ImageIcon("icon.png", "PC Helper 아이콘").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            TrayIcon trayIcon = new TrayIcon(image, "PC HELPER", trayMenu);
            trayIcon.setImageAutoSize(true);

            // 트레이 아이콘 자체를 클릭(왼쪽 클릭)했을 때의 동작
            trayIcon.addActionListener(e -> utilityManager.showGUI(0));

            try {
                SystemTray.getSystemTray().add(trayIcon);
                notificationManager.setTrayIcon(trayIcon);
            } catch (AWTException e) {
                System.err.println("트레이 아이콘을 추가할 수 없습니다.");
            }

            // 설계에 따라 PC 관리 주기와 이벤트 로그 스캔 주기를 관리할 타이머 생성
            Timer manageTimer = new Timer("manageTimer", 30); // 30일 주기로 PC 정리 알림
            Timer scanTimer = new Timer("scanTimer", 1);      // 1일 주기로 이벤트 로그 스캔

            // 백그라운드에서 주기적으로 타이머를 체크하는 스레드 실행
            new Thread(() -> {
                while (true) {
                    try {
                        // Use Case #3: 컴퓨터 관리 알림
                        if (manageTimer.checkTimer()) {
                            System.out.println("관리 타이머 만료. 알림 표시 시도.");
                            notificationManager.showManagementReminder(utilityManager);
                            manageTimer.resetTimer();
                        }

                        // Use Case #5: 오류 알림
                        if (scanTimer.checkTimer()) {
                            System.out.println("오류 스캔 타이머 만료. 스캔 시작.");
                            List<String> errors = fileIOManager.scanEvtForErrors();
                            if (!errors.isEmpty()) {
                                notificationManager.showErrorNotification(utilityManager, errors);
                            }
                            scanTimer.resetTimer();
                        }

                        // 1시간마다 체크 (3600 * 1000 밀리초)
                        Thread.sleep(3600 * 1000);
                    } catch (InterruptedException e) {
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
    private final NotificationManager notificationManager;
    private JFrame mainFrame;

    public UtilityManager(ExternalExecutor exe, APIManager aiQuery, FileIOManager fileIOManager, NotificationManager notificationManager) {
        this.exe = exe;
        this.aiQuery = aiQuery;
        this.fileIOManager = fileIOManager;
        this.notificationManager = notificationManager;
    }

    public void showGUI(int pageNum) {
        if (mainFrame != null && mainFrame.isShowing()) {
            mainFrame.toFront();
        } else {
            mainFrame = new JFrame();
            mainFrame.setSize(800, 600);
            mainFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            mainFrame.setLocationRelativeTo(null);
        }

        switch (pageNum) {
            case 1:
                showToolBox();
                break;
            case 2:
                showAIHelper();
                break;
            case 3:
                showCleaner();
                break; // 파일 정리 기능
            default:
                showMainPage();
                break;
        }
        mainFrame.setVisible(true);
        mainFrame.revalidate();
        mainFrame.repaint();
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
        gbc.gridx = 0;
        gbc.gridy = 0;
        pane.add(toolboxButton, gbc);
        gbc.gridy = 1;
        pane.add(aiButton, gbc);
    }

    // Use Case #2: 도구상자
    private void showToolBox() {
        mainFrame.setTitle("PC HELPER - 도구 상자");
        Container pane = mainFrame.getContentPane();
        pane.removeAll();
        pane.setLayout(new GridLayout(0, 1, 5, 5));

        // UI 프로토타입에 명시된 유틸리티 목록과 색상별 난이도 적용
        addToolBoxButton("오류 기록 보기", new Color(173, 216, 230), "저장된 시스템 오류 기록을 확인합니다.", () -> showErrorLog(fileIOManager.getSavedErrors()));
        addToolBoxButton("디스크 정리", new Color(173, 216, 230), "Windows 디스크 정리 유틸리티를 실행합니다.", () -> exe.executeUtility(1));
        addToolBoxButton("사용자 파일 정리", new Color(144, 238, 144), "불필요한 파일을 찾아 정리합니다.", () -> showCleaner());
        addToolBoxButton("디스크 조각 모음 및 최적화", new Color(144, 238, 144), "디스크를 최적화하여 성능을 향상시킵니다.", () -> exe.executeUtility(2));
        addToolBoxButton("장치 관리자", new Color(255, 228, 181), "하드웨어 장치를 관리합니다.", () -> exe.executeUtility(3));
        addToolBoxButton("작업 관리자", new Color(255, 228, 181), "현재 실행 중인 프로세스를 확인하고 관리합니다.", () -> exe.executeUtility(4));
        addToolBoxButton("레지스트리 편집기", new Color(255, 182, 193), "주의: 시스템 설정을 직접 수정하는 전문가용 도구입니다.", () -> exe.executeUtility(5));
    }

    private void addToolBoxButton(String name, Color color, String helpText, Runnable action) {
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
        mainFrame.getContentPane().add(panel);
    }

    // Use Case #4: AI에게 질문하기
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

        JTextField inputField = new JTextField();
        inputField.setFont(new Font("Malgun Gothic", Font.PLAIN, 14));

        inputField.addActionListener(e -> {
            String userText = inputField.getText();
            if (userText.trim().isEmpty()) return;

            chatArea.append("사용자: " + userText + "\n\n");
            inputField.setText("");

            // API 매니저를 호출하여 LLM의 답변을 받아옴 (백그라운드 스레드에서)
            new Thread(() -> {
                String response = aiQuery.sendQuery(userText);
                SwingUtilities.invokeLater(() -> chatArea.append("AI 도우미: " + response + "\n\n"));
            }).start();
        });

        pane.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        pane.add(inputField, BorderLayout.SOUTH);
    }

    // Use Case #6: 파일 정리 기능
    private void showCleaner() {
        mainFrame.setTitle("PC HELPER - 파일 정리");
        Container pane = mainFrame.getContentPane();
        pane.removeAll();
        pane.setLayout(new BorderLayout(5, 5));

        File userHome = new File(System.getProperty("user.home"));

        String[] columnNames = {"이름", "크기", "경로"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);

        // 파일 스캔은 오래 걸릴 수 있으므로 백그라운드 스레드에서 실행
        new Thread(() -> {
            fileIOManager.scanDirectory(userHome, tableModel);
        }).start();

        JScrollPane scrollPane = new JScrollPane(table);

        JPanel buttonPanel = new JPanel();
        JButton deleteButton = new JButton("선택한 파일 삭제");
        deleteButton.addActionListener(e -> {
            int[] selectedRows = table.getSelectedRows();
            if (selectedRows.length > 0) {
                int confirm = JOptionPane.showConfirmDialog(mainFrame, "선택한 " + selectedRows.length + "개의 항목을 정말 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.", "삭제 확인", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    for (int i = selectedRows.length - 1; i >= 0; i--) {
                        String path = (String) tableModel.getValueAt(selectedRows[i], 2);
                        fileIOManager.deleteFile(new File(path));
                        tableModel.removeRow(selectedRows[i]);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(mainFrame, "삭제할 파일을 선택해주세요.");
            }
        });

        buttonPanel.add(deleteButton);
        pane.add(scrollPane, BorderLayout.CENTER);
        pane.add(buttonPanel, BorderLayout.SOUTH);
    }

    // 저장된 오류 기록을 보여주는 기능
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
    }
}

// 프로그램의 모든 알림 기능을 담당하는 클래스
class NotificationManager {
    private final FileIOManager fileIOManager;
    private TrayIcon trayIcon;

    public NotificationManager(FileIOManager fileIOManager) {
        this.fileIOManager = fileIOManager;
    }

    public void setTrayIcon(TrayIcon trayIcon) {
        this.trayIcon = trayIcon;
    }

    // Use Case #3: 컴퓨터 관리 알림
    public void showManagementReminder(UtilityManager utilityManager) {
        // 설계에 따라 전체화면 프로그램 실행 중에는 알림을 유예하는 로직 추가 가능
        String message = "컴퓨터 정리를 안 한지 30일이 지났습니다!";
        String[] options = {"도구 상자 열기", "다음에 알림"};

        int choice = JOptionPane.showOptionDialog(
                null, message, "PC 관리 알림",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);

        if (choice == 0) { // "도구 상자 열기" 선택
            utilityManager.showGUI(1);
        }
    }

    // Use Case #5: 오류 알림
    public void showErrorNotification(UtilityManager utilityManager, List<String> errors) {
        fileIOManager.saveErrors(errors); // 발견된 오류를 파일에 저장
        String message = "컴퓨터에 심각한 오류 기록이 있습니다!";

        // 트레이 아이콘을 사용할 수 있으면 트레이 메시지 표시
        if (trayIcon != null) {
            trayIcon.displayMessage("오류 알림", message, TrayIcon.MessageType.ERROR);
            trayIcon.addActionListener(e -> utilityManager.showGUI(1)); // 클릭 시 도구 상자 표시
        } else { // 트레이를 사용할 수 없으면 JOptionPane으로 표시
            String[] options = {"AI에게 물어보기", "나중에 보기"};
            int choice = JOptionPane.showOptionDialog(
                    null, message, "오류 알림",
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE,
                    null, options, options[0]);

            if (choice == 0) { // "AI에게 물어보기" 선택
                utilityManager.showGUI(2);
            }
        }
    }
}

// 외부 LLM API(Gemini)와의 통신을 관리하는 클래스
class APIManager {
    private final String apiKey;
    // 프롬프트 엔지니어링: LLM의 환각 현상을 줄이고 역할에 맞는 답변을 유도
    private final String promptSuffix;

    public APIManager(String apiKey) {
        this.apiKey = apiKey;
        this.promptSuffix = " (당신은 PC 문제 해결 전문가입니다. 한국어로 친절하고 명확하게 설명해주세요.)";
    }

    // LLM에 질문을 보내고 답변을 반환하는 메소드
    public String sendQuery(String prompt) {
        if (apiKey == null || apiKey.equals("YOUR_GEMINI_API_KEY") || apiKey.trim().isEmpty()) {
            return "Gemini API 키가 설정되지 않았습니다. PCHelper.java 파일에서 API 키를 설정해주세요.";
        }

        // Gemini API 엔드포인트
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

        // API 요청 본문(Body) 생성 (JSON 형식, 특수문자 이스케이프 처리)
        String escapedPrompt = prompt.replace("\"", "\\\"");
        String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapedPrompt + promptSuffix + "\"}]}]}";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 응답 파싱 (단순화를 위해 기본적인 파싱만 수행)
            // 실제 프로젝트에서는 JSON 라이브러리(Gson, Jackson 등) 사용을 권장합니다.
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                // "text": "..." 부분을 추출
                int startIndex = responseBody.indexOf("\"text\": \"") + 9;
                int endIndex = responseBody.lastIndexOf("\"");
                if (startIndex > 8 && endIndex > startIndex) {
                    return responseBody.substring(startIndex, endIndex).replace("\\n", "\n");
                }
                return "답변을 파싱하는 데 실패했습니다.";
            } else {
                return "API 호출에 실패했습니다. 상태 코드: " + response.statusCode() + "\n" + response.body();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "API 요청 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}

// 파일 읽기, 쓰기, 삭제 및 Windows 이벤트 로그 스캔 등 파일 시스템 관련 작업을 담당하는 클래스
class FileIOManager {
    private final String errorLogFile = "error_log.dat";
    private List<String> savedErrors = new ArrayList<>();

    public FileIOManager() {
        loadErrors();
    }

    // Windows 이벤트 로그를 스캔하여 심각한 오류를 찾아 반환
    public List<String> scanEvtForErrors() {
        List<String> criticalErrors = new ArrayList<>();
        // Windows 'wevtutil' 명령어를 사용하여 지난 24시간 동안의 '심각(Level=1)' 오류를 조회
        String command = "wevtutil qe System /q:\"*[System[(Level=1) and TimeCreated[timediff(@SystemTime) <= 86400000]]]\" /c:5 /f:text";

        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                criticalErrors.add(line);
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("이벤트 로그 스캔 중 오류 발생: " + e.getMessage());
        }
        return criticalErrors;
    }

    public void saveErrors(List<String> errors) {
        this.savedErrors.addAll(errors);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(errorLogFile))) {
            oos.writeObject(this.savedErrors);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadErrors() {
        File file = new File(errorLogFile);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                this.savedErrors = (List<String>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> getSavedErrors() {
        return Collections.unmodifiableList(this.savedErrors);
    }

    // 파일 정리 기능을 위한 디렉토리 스캔
    public void scanDirectory(File directory, DefaultTableModel model) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isHidden()) continue; // 숨김 파일은 제외
            Vector<String> row = new Vector<>();
            row.add(file.getName());
            row.add(formatSize(getFileSize(file)));
            row.add(file.getAbsolutePath());
            model.addRow(row);
        }
    }

    private long getFileSize(File file) {
        if (file.isFile()) {
            return file.length();
        }
        if (file.isDirectory()) {
            long length = 0;
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    length += getFileSize(f);
                }
            }
            return length;
        }
        return 0;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }

    // 파일/디렉토리 삭제
    public void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFile(f);
                }
            }
        }
        if (!file.delete()) {
            System.err.println(file.getAbsolutePath() + " 파일 삭제 실패");
        }
    }
}

// 디스크 정리, 작업 관리자 등 외부 유틸리티 프로그램을 실행하는 역할을 하는 클래스
class ExternalExecutor {
    private final HashMap<Integer, String> utilityMap;

    public ExternalExecutor() {
        utilityMap = new HashMap<>();
        // 유틸리티 번호와 실행 명령어(또는 .msc 파일)를 매핑
        utilityMap.put(1, "cleanmgr.exe");      // 디스크 정리
        utilityMap.put(2, "dfrgui.exe");        // 디스크 조각 모음
        utilityMap.put(3, "devmgmt.msc");       // 장치 관리자
        utilityMap.put(4, "taskmgr.exe");       // 작업 관리자
        utilityMap.put(5, "regedit.exe");       // 레지스트리 편집기
    }

    public void executeUtility(int num) {
        String command = utilityMap.get(num);
        if (command != null) {
            try {
                // Windows 명령어를 실행
                new ProcessBuilder(command).start();
            } catch (IOException e) {
                System.err.println("유틸리티 '" + command + "' 실행 실패: " + e.getMessage());
                JOptionPane.showMessageDialog(null, "유틸리티를 실행할 수 없습니다.\n" + e.getMessage(), "실행 오류", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            System.err.println("유틸리티 번호 " + num + "를 찾을 수 없습니다.");
        }
    }
}

// 설정된 주기가 지났는지 확인하기 위해 날짜를 계산하는 클래스
class Timer {
    private final String name;      // 타이머 상태 저장을 위한 파일 이름
    private final int interval;     // 만료 기간 (일 단위)
    private LocalDate resetDate;    // 마지막으로 초기화된 날짜

    public Timer(String name, int interval) {
        this.name = name;
        this.interval = interval;
        this.resetDate = loadLastResetDate();
    }

    // 타이머 만료 여부 확인
    public boolean checkTimer() {
        long daysPassed = ChronoUnit.DAYS.between(resetDate, LocalDate.now());
        return daysPassed >= interval;
    }

    // 타이머를 현재 날짜로 초기화
    public void resetTimer() {
        this.resetDate = LocalDate.now();
        saveLastResetDate();
    }

    private void saveLastResetDate() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(name + ".dat"))) {
            writer.write(resetDate.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private LocalDate loadLastResetDate() {
        File file = new File(name + ".dat");
        if (file.exists()) {
            try {
                String dateStr = new String(Files.readAllBytes(Paths.get(file.getPath())));
                return LocalDate.parse(dateStr);
            } catch (Exception e) {
                // 파일 읽기 실패 시 오늘 날짜로 초기화
                System.err.println("타이머 파일 로딩 실패: " + e.getMessage());
            }
        }
        // 저장 파일이 없으면 오늘 날짜로 초기화하고 저장
        LocalDate today = LocalDate.now();
        this.resetDate = today;
        saveLastResetDate();
        return today;
    }
}
