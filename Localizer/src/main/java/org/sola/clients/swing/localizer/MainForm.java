package org.sola.clients.swing.localizer;

import java.awt.Cursor;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Properties;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import java.util.Map;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Vector;
import javax.swing.filechooser.FileFilter;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

public class MainForm extends javax.swing.JFrame {

    private class Language {

        String code;
        Integer itemOrder;

        public Language() {

        }

        public Language(String code, Integer itemOrder) {
            this.code = code;
            this.itemOrder = itemOrder;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public Integer getItemOrder() {
            return itemOrder;
        }

        public void setItemOrder(Integer itemOrder) {
            this.itemOrder = itemOrder;
        }
    }

    private class BundleResource {

        private String bundlePath;
        private String key;
        private String value;

        public String getBundlePath() {
            return bundlePath;
        }

        public void setBundlePath(String bundlePath) {
            this.bundlePath = bundlePath;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public BundleResource() {
        }

        public BundleResource(String bundlePath, String key, String value) {
            this.bundlePath = bundlePath;
            this.key = key;
            this.value = value;
        }
    }

    private class SortedProperties extends Properties {

        @Override
        public Enumeration keys() {
            Enumeration keysEnum = super.keys();
            Vector<String> keyList = new Vector<String>();
            while (keysEnum.hasMoreElements()) {
                keyList.add((String) keysEnum.nextElement());
            }
            Collections.sort(keyList);
            return keyList.elements();
        }
    }

    int resourceRowNumber = 0;
    CellStyle headerStyle;
    CellStyle sysColStyle;
    CellStyle defStyle;
    CellStyle missingStyle;
    CellStyle protectedStyle;

    /**
     * Creates new form MainForm
     */
    public MainForm() {
        initComponents();
    }

    private boolean checkDBSettings() {
        Object langCode = txtLanguageCode.getValue();
        if (txtHostName.getText().equals("") || txtPortNumber.getText().equals("")
                || txtUsername.getText().equals("") || txtPassword.getPassword().length < 1
                || txtDBName.getText().equals("") || langCode == null) {
            JOptionPane.showMessageDialog(this, "Fill in DB settings", "Warning", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private boolean checkExportFolder() {
        if (txtSolaExportFolder.getText().equals("")) {
            JOptionPane.showMessageDialog(this, "Select root project folder", "Warning", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void exportResources() {
        if (!checkDBSettings() || !checkExportFolder()) {
            return;
        }

        final String langCode = txtLanguageCode.getValue().toString();
        String localeCodes[] = langCode.split("-");
        final String languageCode = localeCodes[0];
        final String countryCode = localeCodes[1];

        btnExport.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {

            Exception exception;
            String savePath = "";

            public Void doInBackground() {

                txtMessages.setText("");
                Connection conn = null;

                try {
                    Properties refTables = new Properties();
                    refTables.load(MainForm.class.getResourceAsStream("/org/sola/clients/swing/localizer/ReferenceDataTables.properties"));

                    if (refTables.size() < 1) {
                        exception = new Exception("No Reference tables found in the properties.");
                        return null;
                    }

                    progressBar.setIndeterminate(true);
                    int cnt = 0;

                    // Create connection
                    conn = createConnection();
                    Statement cmd = null;

                    // Create workbook
                    Workbook wb = new HSSFWorkbook();
                    Sheet sheet = wb.createSheet("DB");
                    sheet.setAutobreaks(true);

                    // Create styles
                    headerStyle = wb.createCellStyle();
                    headerStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
                    headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                    headerStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
                    headerStyle.setBorderBottom(CellStyle.BORDER_THIN);
                    headerStyle.setBorderTop(CellStyle.BORDER_THIN);
                    headerStyle.setBorderLeft(CellStyle.BORDER_THIN);
                    headerStyle.setBorderRight(CellStyle.BORDER_THIN);

                    sysColStyle = wb.createCellStyle();
                    sysColStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
                    sysColStyle.setBorderBottom(CellStyle.BORDER_THIN);
                    sysColStyle.setBorderTop(CellStyle.BORDER_THIN);
                    sysColStyle.setBorderLeft(CellStyle.BORDER_THIN);
                    sysColStyle.setBorderRight(CellStyle.BORDER_THIN);

                    defStyle = wb.createCellStyle();
                    defStyle.setWrapText(true);
                    defStyle.setAlignment(CellStyle.ALIGN_LEFT);
                    defStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
                    defStyle.setLocked(false);
                    defStyle.setBorderBottom(CellStyle.BORDER_THIN);
                    defStyle.setBorderTop(CellStyle.BORDER_THIN);
                    defStyle.setBorderLeft(CellStyle.BORDER_THIN);
                    defStyle.setBorderRight(CellStyle.BORDER_THIN);

                    missingStyle = wb.createCellStyle();
                    missingStyle.setWrapText(true);
                    missingStyle.setAlignment(CellStyle.ALIGN_LEFT);
                    missingStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
                    missingStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
                    missingStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
                    missingStyle.setLocked(false);
                    missingStyle.setBorderBottom(CellStyle.BORDER_THIN);
                    missingStyle.setBorderTop(CellStyle.BORDER_THIN);
                    missingStyle.setBorderLeft(CellStyle.BORDER_THIN);
                    missingStyle.setBorderRight(CellStyle.BORDER_THIN);

                    protectedStyle = wb.createCellStyle();
                    protectedStyle.setWrapText(true);
                    protectedStyle.setAlignment(CellStyle.ALIGN_LEFT);
                    protectedStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
                    Font font1 = wb.createFont();
                    font1.setColor(IndexedColors.DARK_RED.getIndex());
                    protectedStyle.setFont(font1);
                    protectedStyle.setLocked(true);
                    protectedStyle.setBorderBottom(CellStyle.BORDER_THIN);
                    protectedStyle.setBorderTop(CellStyle.BORDER_THIN);
                    protectedStyle.setBorderLeft(CellStyle.BORDER_THIN);
                    protectedStyle.setBorderRight(CellStyle.BORDER_THIN);

                    // Add header
                    int i = 1;
                    Row headerRow = sheet.createRow(0);
                    headerRow.setHeightInPoints(18f);
                    headerRow.createCell(0).setCellValue("Table");
                    headerRow.getCell(0).setCellStyle(headerStyle);
                    headerRow.createCell(1).setCellValue("Code/ID");
                    headerRow.getCell(1).setCellStyle(headerStyle);
                    headerRow.createCell(2).setCellValue("Column");
                    headerRow.getCell(2).setCellStyle(headerStyle);
                    headerRow.createCell(3).setCellValue("String to translate");
                    headerRow.getCell(3).setCellStyle(headerStyle);
                    headerRow.createCell(4).setCellValue("Default Value");
                    headerRow.getCell(4).setCellStyle(headerStyle);

                    sheet.setColumnWidth(3, 256 * 80);
                    sheet.setColumnWidth(4, 256 * 80);

                    sheet.setDefaultColumnStyle(0, sysColStyle);
                    sheet.setDefaultColumnStyle(1, sysColStyle);
                    sheet.setDefaultColumnStyle(2, sysColStyle);
                    sheet.setDefaultColumnStyle(3, defStyle);
                    sheet.setDefaultColumnStyle(4, defStyle);

                    sheet.setColumnHidden(0, true);
                    sheet.setColumnHidden(1, true);
                    sheet.setColumnHidden(2, true);
                    sheet.createFreezePane(0, 1);

                    sheet.protectSheet("123");

                    savePath = txtExportDestinationFolder.getText()
                            + System.getProperty("file.separator")
                            + "Resources.xls";

                    // Scroll through the reference data tables
                    for (Map.Entry<Object, Object> table : refTables.entrySet()) {
                        // Check table exists
                        String tableName = table.getKey().toString();
                        String schemaName = tableName.split("\\.")[0];
                        String tblName = tableName.split("\\.")[1];

                        String sql = "SELECT EXISTS ("
                                + "    SELECT 1 "
                                + "    FROM   pg_catalog.pg_class c "
                                + "    JOIN   pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                                + "    WHERE  n.nspname = '" + schemaName + "' "
                                + "    AND    c.relname = '" + tblName + "' "
                                + "    AND    c.relkind = 'r' "
                                + ")";

                        cmd = conn.createStatement();
                        ResultSet rs = cmd.executeQuery(sql);

                        if (rs.next()) {
                            if (!rs.getBoolean(1)) {
                                rs.close();
                                continue;
                            }
                        }

                        rs.close();
                        cnt += 1;

                        txtMessages.append("Exporting " + tableName);
                        txtMessages.append("\r\n");

                        boolean hasDescription = true;
                        if (tableName.equalsIgnoreCase("system.language")) {
                            hasDescription = false;
                        }

                        if (hasDescription) {
                            if (tableName.equalsIgnoreCase("application.request_type")) {
                                sql = String.format("SELECT code, get_translation(display_value, '%s') as display_value, "
                                        + "get_translation(display_value, null) as default_display_value, "
                                        + "get_translation(description, '%s') as description, "
                                        + "get_translation(description, null) as default_description, "
                                        + "get_translation(display_group_name, '%s') as display_group_name, "
                                        + "get_translation(display_group_name, null) as default_display_group_name "
                                        + "FROM %s;", langCode, langCode, langCode, tableName);
                            } else {
                                sql = String.format("SELECT code, get_translation(display_value, '%s') as display_value, "
                                        + "get_translation(display_value, null) as default_display_value, "
                                        + "get_translation(description, '%s') as description, "
                                        + "get_translation(description, null) as default_description "
                                        + "FROM %s;", langCode, langCode, tableName);
                            }
                        } else {
                            sql = String.format("SELECT code, get_translation(display_value, '%s') as display_value, "
                                    + "get_translation(display_value, null) as default_display_value "
                                    + "FROM %s;", langCode, tableName);
                        }

                        cmd = conn.createStatement();
                        rs = cmd.executeQuery(sql);

                        try {
                            // Loop thought the table records
                            while (rs.next()) {
                                String displayValue = rs.getString("display_value");
                                String defDisplayValue = rs.getString("default_display_value");

                                Row row = sheet.createRow(i);
                                row.createCell(0).setCellValue(tableName);
                                row.createCell(1).setCellValue(rs.getString("code"));
                                row.createCell(2).setCellValue("display_value");
                                row.createCell(3).setCellValue(displayValue);
                                row.createCell(4).setCellValue(defDisplayValue);
                                if (displayValue.equals(defDisplayValue)) {
                                    row.getCell(3).setCellStyle(missingStyle);
                                }
                                row.getCell(0).setCellType(HSSFCell.CELL_TYPE_STRING);
                                row.getCell(1).setCellType(HSSFCell.CELL_TYPE_STRING);
                                row.getCell(2).setCellType(HSSFCell.CELL_TYPE_STRING);
                                row.getCell(3).setCellType(HSSFCell.CELL_TYPE_STRING);
                                row.getCell(4).setCellType(HSSFCell.CELL_TYPE_STRING);

                                row.getCell(4).setCellStyle(protectedStyle);
                                i += 1;
                                if (hasDescription) {
                                    String descr = rs.getString("description");
                                    if (rs.wasNull()) {
                                        descr = "";
                                    }

                                    String descr_def = rs.getString("default_description");
                                    if (rs.wasNull()) {
                                        descr_def = "";
                                    }

                                    row = sheet.createRow(i);
                                    row.createCell(0).setCellValue(tableName);
                                    row.createCell(1).setCellValue(rs.getString("code"));
                                    row.createCell(2).setCellValue("description");
                                    row.createCell(3).setCellValue(descr);
                                    row.createCell(4).setCellValue(descr_def);
                                    row.getCell(4).setCellStyle(protectedStyle);
                                    if (descr.equals(descr_def)) {
                                        row.getCell(3).setCellStyle(missingStyle);
                                    }
                                    row.getCell(0).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(1).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(2).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(3).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(4).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    i += 1;
                                }
                                if (tableName.equalsIgnoreCase("application.request_type")) {
                                    String groupName = rs.getString("display_group_name");
                                    if (rs.wasNull()) {
                                        groupName = "";
                                    }

                                    String groupNameDef = rs.getString("default_display_group_name");
                                    if (rs.wasNull()) {
                                        groupNameDef = "";
                                    }

                                    row = sheet.createRow(i);
                                    row.createCell(0).setCellValue(tableName);
                                    row.createCell(1).setCellValue(rs.getString("code"));
                                    row.createCell(2).setCellValue("display_group_name");
                                    row.createCell(3).setCellValue(groupName);
                                    row.createCell(4).setCellValue(groupNameDef);
                                    row.getCell(4).setCellStyle(protectedStyle);
                                    if (groupName.equals(groupNameDef)) {
                                        row.getCell(3).setCellStyle(missingStyle);
                                    }
                                    row.getCell(0).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(1).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(2).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(3).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    row.getCell(4).setCellType(HSSFCell.CELL_TYPE_STRING);
                                    i += 1;
                                }
                            }
                        } catch (Exception e) {
                            exception = e;
                            return null;
                        }

                        rs.close();
                        txtMessages.append("SUCCESS");
                        txtMessages.append("\r\n");
                    }

                    // Export BRs
                    String brTable = "system.br";
                    txtMessages.append("Exporting " + brTable);
                    txtMessages.append("\r\n");

                    cmd = conn.createStatement();

                    String sql = String.format("SELECT id, get_translation(feedback, '%s') as feedback, "
                            + "get_translation(feedback, null) as default_feedback FROM %s;",
                            langCode, brTable);

                    ResultSet rs = cmd.executeQuery(sql);

                    try {
                        // Loop thought the table records
                        while (rs.next()) {
                            String feedback = rs.getString("feedback");
                            if (rs.wasNull()) {
                                feedback = "";
                            }

                            String feedback_def = rs.getString("default_feedback");
                            if (rs.wasNull()) {
                                feedback_def = "";
                            }

                            Row row = sheet.createRow(i);
                            row.createCell(0).setCellValue(brTable);
                            row.createCell(1).setCellValue(rs.getString("id"));
                            row.createCell(2).setCellValue("feedback");
                            row.createCell(3).setCellValue(feedback);
                            row.createCell(4).setCellValue(feedback_def);
                            row.getCell(4).setCellStyle(protectedStyle);
                            if (feedback.equals(feedback_def)) {
                                row.getCell(3).setCellStyle(missingStyle);
                            }
                            row.getCell(0).setCellType(HSSFCell.CELL_TYPE_STRING);
                            row.getCell(1).setCellType(HSSFCell.CELL_TYPE_STRING);
                            row.getCell(2).setCellType(HSSFCell.CELL_TYPE_STRING);
                            row.getCell(3).setCellType(HSSFCell.CELL_TYPE_STRING);
                            row.getCell(4).setCellType(HSSFCell.CELL_TYPE_STRING);
                            i += 1;
                        }

                    } catch (Exception e) {
                        exception = e;
                        return null;
                    }

                    // Export resources
                    try {
                        Sheet resourceSheet = wb.createSheet("Bundles");
                        resourceSheet.setAutobreaks(true);

                        Row bundlesHeaderRow = resourceSheet.createRow(0);
                        bundlesHeaderRow.setHeightInPoints(18f);
                        bundlesHeaderRow.createCell(0).setCellValue("Bundle");
                        bundlesHeaderRow.getCell(0).setCellStyle(headerStyle);
                        bundlesHeaderRow.createCell(1).setCellValue("Key");
                        bundlesHeaderRow.getCell(1).setCellStyle(headerStyle);
                        bundlesHeaderRow.createCell(2).setCellValue("String to translate");
                        bundlesHeaderRow.getCell(2).setCellStyle(headerStyle);
                        bundlesHeaderRow.createCell(3).setCellValue("Default value");
                        bundlesHeaderRow.getCell(3).setCellStyle(headerStyle);

                        resourceSheet.setColumnWidth(2, 256 * 80);
                        resourceSheet.setColumnWidth(3, 256 * 80);

                        resourceSheet.setDefaultColumnStyle(0, sysColStyle);
                        resourceSheet.setDefaultColumnStyle(1, sysColStyle);
                        resourceSheet.setDefaultColumnStyle(2, defStyle);
                        resourceSheet.setDefaultColumnStyle(3, defStyle);

                        resourceSheet.setColumnHidden(0, true);
                        resourceSheet.setColumnHidden(1, true);
                        resourceSheet.createFreezePane(0, 1);

                        resourceRowNumber = 1;
                        txtMessages.append("Processing bundle files...");
                        txtMessages.append("\r\n");

                        cnt = extractResources(txtSolaExportFolder.getText(), resourceSheet,
                                new Locale(languageCode, countryCode), txtSolaExportFolder.getText());
                        resourceSheet.protectSheet("123");

                        txtMessages.append(String.valueOf(cnt) + " bundle files were successfully processed. ");
                        txtMessages.append("\r\n");

                    } catch (Exception e) {
                        exception = e;
                        return null;
                    }

                    FileOutputStream fileOut = new FileOutputStream(savePath);
                    wb.write(fileOut);
                    fileOut.close();

                    rs.close();
                    txtMessages.append("Done!\n");
                    txtMessages.append("\r\n");

                    cmd.close();

                } catch (Exception e) {
                    exception = e;
                    return null;
                } finally {
                    try {
                        if (conn != null) {
                            conn.close();
                        }
                    } catch (Exception e) {
                    }
                }
                return null;
            }

            /*
             * Executed in event dispatching thread
             */
            @Override
            public void done() {
                progressBar.setIndeterminate(false);
                btnExport.setEnabled(true);
                setCursor(null);

                if (exception != null) {
                    JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(null,
                            String.format(
                                    "Data was successfully exported. \r\nCheck \"%s\" folder",
                                    savePath),
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };

        task.execute();
    }

    private void importResources() {
        if (!checkDBSettings()) {
            return;
        }

        if (txtResourcesFile.getText().equals("")) {
            JOptionPane.showMessageDialog(this, "Select resources file");
            return;
        }

        final File resources = new File(txtResourcesFile.getText());

        if (!resources.exists()) {
            JOptionPane.showMessageDialog(this, "Select resources file doesn't exist");
            return;
        }

        if (txtSolaImportFolder.getText().equals("")) {
            JOptionPane.showMessageDialog(this, "Select root project folder");
            return;
        }

        File projectFolder = new File(txtSolaImportFolder.getText());

        if (!projectFolder.exists()) {
            JOptionPane.showMessageDialog(this, "Selected root project folder doesn't exist");
            return;
        }

        if (!projectFolder.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Selected root project folder is not folder");
            return;
        }

        if (JOptionPane.showConfirmDialog(this,
                "Make sure you have entered correct language code for updating reference data tables\r\n"
                + "In case of the wrong code provided, existing values can be overridden.\r\n"
                + "Do you want to continue?") != JOptionPane.YES_OPTION) {
            return;
        }

        final String langCode = txtLanguageCode.getValue().toString();
        btnImportResources.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        progressBar.setIndeterminate(true);

        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {

            Exception exception;

            public Void doInBackground() {

                txtMessages.setText("");
                Connection conn = null;
                boolean dbTabFound = true;

                try {
                    FileInputStream fileIn = new FileInputStream(resources);
                    POIFSFileSystem fs = new POIFSFileSystem(fileIn);
                    HSSFWorkbook wb = new HSSFWorkbook(fs);
                    HSSFSheet sheet = wb.getSheetAt(0);

                    if (!sheet.getSheetName().equals("DB")) {
                        //exception = new Exception("Sheet with DB resources not found.");
                        //return null;
                        dbTabFound = false;
                        txtMessages.append("DB tab is not found");
                        txtMessages.append("\r\n");
                    }

                    if (dbTabFound) {
                        // Create connection
                        Class.forName("org.postgresql.Driver");
                        conn = createConnection();
                        Statement cmd = conn.createStatement();

                        // Check language code exist
                        String sql = "SELECT code, item_order FROM system.language "
                                + "WHERE lower(code) = lower('" + langCode + "') ORDER BY item_order";

                        ResultSet rs = cmd.executeQuery(sql);
                        if (!rs.next()) {
                            // Language doesn't exist, inset new record
                            sql = "INSERT INTO system.language (code, display_value, active, is_default, item_order) VALUES "
                                    + "('" + langCode + "', 'new_lanuage', 't', 'f', (SELECT MAX(item_order) FROM system.language) + 1)";
                            cmd.executeUpdate(sql);
                        }

                        rs.close();

                        // Read all languages for correct order
                        sql = "SELECT code, item_order FROM system.language ORDER BY item_order";
                        rs = cmd.executeQuery(sql);
                        ArrayList<Language> langs = new ArrayList<Language>();

                        while (rs.next()) {
                            langs.add(new Language(rs.getString("code"), rs.getInt("item_order")));
                        }

                        Collections.sort(langs, new Comparator<Language>() {

                            @Override
                            public int compare(Language s1, Language s2) {
                                if (s1.getItemOrder() < s2.getItemOrder()) {
                                    return -1;
                                } else if (s1.getItemOrder() > s2.getItemOrder()) {
                                    return 1;
                                } else {
                                    return 0;
                                }
                            }
                        });

                        rs.close();

                        if (langs.size() < 1) {
                            exception = new Exception("Languages not found in the table");
                            return null;
                        }

                        // Scroll through the reference data table lines
                        txtMessages.append("Importing ref tables");
                        txtMessages.append("\r\n");
                        int lastRow = sheet.getLastRowNum();

                        for (int i = sheet.getFirstRowNum() + 1; i <= lastRow; i++) {
                            HSSFRow row = sheet.getRow(i);

                            String tableName = row.getCell(0).getStringCellValue();

                            // Make updates
                            String pKeyValue = row.getCell(1).getStringCellValue();
                            String field = row.getCell(2).getStringCellValue();
                            row.getCell(3).setCellType(HSSFCell.CELL_TYPE_STRING);
                            String value = row.getCell(3).getStringCellValue();
                            String pKeyField = "code";

                            if (tableName.equalsIgnoreCase("system.br")) {
                                pKeyField = "id";
                            }

                            if (!pKeyValue.equals("")) {
                                // Get current value and combine localized string
                                sql = String.format("SELECT %s FROM %s WHERE %s = '%s'", field, tableName, pKeyField, pKeyValue);
                                rs = cmd.executeQuery(sql);
                                if (rs.next()) {
                                    value = makeLocalizedString(rs.getString(field), langCode, value, langs);
                                }
                                rs.close();

                                // Update previous values
                                sql = String.format("UPDATE %s SET %s = '%s' WHERE %s = '%s'",
                                        tableName, field, value, pKeyField, pKeyValue);
                                System.out.println(sql);
                                cmd.executeUpdate(sql);
                            }
                        }

                        txtMessages.append("All reference data tables were updated.");
                        txtMessages.append("\r\n");
                        cmd.close();
                    }
                    txtMessages.append("Copying resource files.");
                    txtMessages.append("\r\n");

                    // Copy/create bundle files
                    HSSFSheet resSheet = wb.getSheetAt(1);
                    
                    if (!resSheet.getSheetName().equals("Bundles")) {
                        boolean bundlesTabFound = false;
                        // Try first tab if DB tab wasn't found
                        if(!dbTabFound){
                            // try first tab
                            resSheet = wb.getSheetAt(0);
                            if (resSheet.getSheetName().equals("Bundles")) {
                                bundlesTabFound = true;
                            }
                        }
                        if(!bundlesTabFound){
                            exception = new Exception("Sheet with Bundle resources not found.");
                            return null;
                        }
                    }

                    // Create a collection of resources;
                    List<BundleResource> bundleResources = new ArrayList();
                    int lastRow = resSheet.getLastRowNum();
                    for (int i = resSheet.getFirstRowNum() + 1; i <= lastRow; i++) {
                        HSSFRow row = resSheet.getRow(i);
                        row.getCell(2).setCellType(HSSFCell.CELL_TYPE_STRING);
                        bundleResources.add(new BundleResource(
                                row.getCell(0).getStringCellValue(),
                                row.getCell(1).getStringCellValue(),
                                row.getCell(2).getStringCellValue()));
                    }

                    // Sort list by bundle name and key
                    Collections.sort(bundleResources, new Comparator<BundleResource>() {

                        public int compare(BundleResource r1, BundleResource r2) {
                            if (r1.getBundlePath().equals(r2.getBundlePath())) {
                                return r1.getKey().compareTo(r2.getKey());
                            } else {
                                return r1.getBundlePath().compareTo(r2.getBundlePath());
                            }
                        }
                    });

                    String localeCode = langCode.replace("-", "_");

                    // Loop through collection and copy/create bundle files
                    int cnt = 0;
                    String projectRootFolder = txtSolaImportFolder.getText();
                    SortedProperties p = null;
                    String bundleFile = "";
                    int i = bundleResources.size();

                    for (BundleResource bundleResource : bundleResources) {
                        String tmpBundleName = bundleResource.getBundlePath().replace("\\", System.getProperty("file.separator"));
                        tmpBundleName = tmpBundleName.replace("//", System.getProperty("file.separator"));

                        String bundleFolder = projectRootFolder + System.getProperty("file.separator") + tmpBundleName.substring(0, tmpBundleName.lastIndexOf(System.getProperty("file.separator")));
                        tmpBundleName = tmpBundleName.substring(tmpBundleName.lastIndexOf(System.getProperty("file.separator")) + 1);
                        String tmpBundleFile = bundleFolder + System.getProperty("file.separator") + tmpBundleName + "_" + localeCode + ".properties";

                        if (!bundleFile.equals(tmpBundleFile)) {
                            // Check bundle folder exists
                            if (!new File(bundleFolder).exists()) {
                                txtMessages.append("Bundle folder \"" + bundleFolder + "\" doesn't exist");
                                txtMessages.append("\r\n");
                                continue;
                            }

                            // Check if bundle file existis
                            if (!bundleFile.equals("")) {
                                File f = new File(bundleFile);
                                if (f.exists()) {
                                    // Delete old file
                                    f.delete();
                                }

                                // Create new file
                                f.createNewFile();

                                // Store bundle file
                                FileOutputStream fos = new FileOutputStream(bundleFile);
                                p.store(fos, "");
                                fos.flush();
                                fos.close();
                                p = new SortedProperties();
                            } else {
                                p = new SortedProperties();
                            }

                            bundleFile = tmpBundleFile;
                            cnt += 1;
                        }

                        if (p != null) {
                            //p.put(bundleResource.getKey(), bundleResource.getValue().replace("\n", "\\n\\\n"));
                            p.put(bundleResource.getKey(), bundleResource.getValue());
                            i = i - 1;
                            if (i == 0) {
                                // Store last file
                                FileOutputStream fos = new FileOutputStream(bundleFile);
                                p.store(fos, "");
                                fos.flush();
                                fos.close();
                            }
                        }
                    }

                    txtMessages.append(cnt + " files were updated/created.");
                    txtMessages.append("\r\nDone!");

                } catch (Exception e) {
                    exception = e;
                    return null;
                } finally {
                    try {
                        if (conn != null) {
                            conn.close();
                        }
                    } catch (Exception e) {
                    }
                }
                return null;
            }

            /*
             * Executed in event dispatching thread
             */
            @Override
            public void done() {
                progressBar.setIndeterminate(false);
                btnImportResources.setEnabled(true);
                setCursor(null);

                if (exception != null) {
                    JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(null,
                            "Data was successfully imported. Check SOLA DB",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };
        task.execute();
    }

    private Connection createConnection() throws SQLException {
        String connString = String.format("jdbc:postgresql://%s:%s/%s",
                txtHostName.getText(),
                txtPortNumber.getText(),
                txtDBName.getText());
        return DriverManager.getConnection(connString,
                txtUsername.getText(),
                new String(txtPassword.getPassword()));
    }

    private String makeLocalizedString(String localizedString, String langCode,
            String translatedString, ArrayList<Language> langs) {
        if (langCode == null || langCode.equals("") || langs == null || langs.size() < 1) {
            return localizedString;
        }

        if (translatedString == null) {
            translatedString = "";
        }
        translatedString = translatedString.replace("'", "''");

        if (localizedString == null) {
            localizedString = "";
        }

        localizedString = localizedString.replace("'", "''");
        String[] translatedStrings = localizedString.split("::::");
        localizedString = "";
        int i = 0;

        for (Language lang : langs) {
            String currTranslatedString = "";

            if (!localizedString.equals("")) {
                localizedString += "::::";
            }

            if (translatedStrings.length > i) {
                currTranslatedString = translatedStrings[i];
            }

            if (langCode.toLowerCase().equals(lang.getCode().toLowerCase())) {
                currTranslatedString = translatedString;
            }

            localizedString += currTranslatedString;

            i += 1;
        }
        return localizedString;
    }

    private int extractResources(final String sourceFolder, Sheet sheet,
            final Locale locale, String folderPath) throws MalformedURLException, Exception {
        // Skip folders with system config files
        if (!folderPath.equalsIgnoreCase(sourceFolder)) {
            String destFolderName = folderPath.substring(sourceFolder.length() + 1).replace("\\", ".").replace("/", ".");
            if (destFolderName.equalsIgnoreCase("clients.swing.admin.src.main.resources.config")
                    || destFolderName.equalsIgnoreCase("clients.swing.bulk-operations.src.main.resources.config")
                    || destFolderName.equalsIgnoreCase("clients.swing.common.src.main.resources.org.sola.clients.swing.common.tasks.resources")
                    || destFolderName.equalsIgnoreCase("clients.swing.common.src.main.resources.org.sola.clients.swing.common.tasks")
                    || destFolderName.equalsIgnoreCase("clients.swing.desktop.src.main.resources.config")
                    || destFolderName.equalsIgnoreCase("clients.swing.desktop.src.org.sola.clients.desktop.resources")
                    || destFolderName.equalsIgnoreCase("clients.swing.geotools-ui.src.main.resources.org.geotools.swing.extended.resources")
                    || destFolderName.equalsIgnoreCase("clients.swing.geotools-ui.src.main.resources.org.geotools.swing.extended.util.resources")
                    || destFolderName.equalsIgnoreCase("services.services-common.src.main.resources")
                    || destFolderName.equalsIgnoreCase("clients.swing.gis.src.main.resources.org.sola.clients.swing.gis.layer.resources")
                    || destFolderName.equalsIgnoreCase("common.rules.src.main.resources")
                    || destFolderName.equalsIgnoreCase("services.test-common.src.main.resources")) {
                return 0;
            }
        }

        File f = new File(folderPath);

        // Copy files
        String[] files = f.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isFile();
            }
        });

        int cnt = 0;

        List<String> bundles = new ArrayList<String>();

        URL[] urls = {f.toURI().toURL()};
        ClassLoader loader = new URLClassLoader(urls);

        for (String fName : files) {
            if (fName.endsWith(".properties") && fName.length() > 11) {
                // Check for default bundle file
                if (!fName.matches("^[\\w\\-. ]+\\_[a-zA-Z]{2}\\_[a-zA-Z]{2}.properties$")) {
                    bundles.add(fName.substring(0, fName.lastIndexOf(".")));
                    cnt += 1;
                }
            }
        }

        String relFolderPath = "";
        if (folderPath.length() > sourceFolder.length()) {
            relFolderPath = folderPath.substring(sourceFolder.length() + 1);
        }

        if (bundles.size() > 0) {
            for (String bundle : bundles) {
                System.out.println("processing " + relFolderPath + System.getProperty("file.separator") + bundle);
                ResourceBundle rb = ResourceBundle.getBundle(bundle, locale, loader);
                ResourceBundle rbd = ResourceBundle.getBundle(bundle, new Locale("default"), loader);
                String bundlePath = relFolderPath + System.getProperty("file.separator") + bundle;

                for (String key : rb.keySet()) {
                    Row row = sheet.createRow(resourceRowNumber);
                    row.createCell(0).setCellValue(bundlePath);
                    row.createCell(1).setCellValue(key);
                    row.createCell(2).setCellValue(rb.getString(key));
                    if (rbd.containsKey(key)) {
                        row.createCell(3).setCellValue(rbd.getString(key));
                        row.getCell(3).setCellStyle(protectedStyle);
                        if (rb.getString(key).equals(rbd.getString(key))) {
                            row.getCell(2).setCellStyle(missingStyle);
                        }
                    }
                    if ((bundlePath.endsWith("common\\messaging\\src\\main\\resources\\org\\sola\\messages\\client\\Bundle")
                            || bundlePath.endsWith("common/messaging/src/main/resources/org/sola/messages/client/Bundle")
                            || bundlePath.endsWith("common\\messaging\\src\\main\\resources\\org\\sola\\messages\\gis\\Bundle")
                            || bundlePath.endsWith("common/messaging/src/main/resources/org/sola/messages/gis/Bundle")
                            || bundlePath.endsWith("common\\messaging\\src\\main\\resources\\org\\sola\\messages\\service\\Bundle")
                            || bundlePath.endsWith("common/messaging/src/main/resources/org/sola/messages/service/Bundle"))
                            && key.endsWith(".type")) {
                        row.getCell(2).setCellStyle(protectedStyle);
                    }
                    row.getCell(0).setCellType(HSSFCell.CELL_TYPE_STRING);
                    row.getCell(1).setCellType(HSSFCell.CELL_TYPE_STRING);
                    row.getCell(2).setCellType(HSSFCell.CELL_TYPE_STRING);
                    resourceRowNumber += 1;
                }
            }
        }

        String[] directories = f.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }
        });

        for (String dName : directories) {
            // Skip target folders
            if (!dName.equals("target")) {
                cnt += extractResources(sourceFolder, sheet, locale,
                        folderPath + System.getProperty("file.separator") + dName);
            }
        }
        return cnt;
    }

    private void selectFile(JTextField f, final String ext) {
        JFileChooser jfc = new JFileChooser();
        jfc.setApproveButtonText("Select");
        jfc.setApproveButtonMnemonic('a');
        jfc.setDialogType(JFileChooser.OPEN_DIALOG);
        jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        jfc.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.getName().endsWith("." + ext) || f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "*." + ext;
            }
        });
        int result = jfc.showSaveDialog(this);
        if (result == JFileChooser.CANCEL_OPTION) {
            return;
        }
        f.setText(jfc.getSelectedFile().getPath());
    }

    private void selectFolder(JTextField f) {
        // Show folder selection dialog
        JFileChooser jfc = new JFileChooser();
        jfc.setApproveButtonText("Select");
        jfc.setApproveButtonMnemonic('a');
        jfc.setDialogType(JFileChooser.OPEN_DIALOG);
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = jfc.showSaveDialog(this);
        if (result == JFileChooser.CANCEL_OPTION) {
            return;
        }
        f.setText(jfc.getSelectedFile().getPath());
    }

    private void exportHelp() {
        if (txtSolaHelpFolder.getText().equals("") || txtExportHelpDestinationFolder.getText().equals("")
                || txtHelpLanguageCode.getValue() == null) {
            JOptionPane.showMessageDialog(null, "Fill in required fields", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check destination folder exists
        File destFolder = new File(txtExportHelpDestinationFolder.getText());

        if (!destFolder.exists()) {
            JOptionPane.showMessageDialog(this, "Selected destination folder doesn't exist", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String separator = System.getProperty("file.separator");
        String helpPath = String.format(txtSolaHelpFolder.getText()
                + "%ssrc%smain%sresources%sorg%ssola%scommon%shelp", separator, separator, separator,
                separator, separator, separator, separator);

        File f = new File(helpPath);

        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "Select SOLA help folder (help)", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String langCode = txtHelpLanguageCode.getValue().toString();

        // Create folder
        final String savePath = txtExportHelpDestinationFolder.getText()
                + System.getProperty("file.separator")
                + "SOLA Help Resources";

        File saveFolder = new File(savePath);

        if (saveFolder.isDirectory() && saveFolder.exists()) {
            try {
                FileUtils.deleteDirectory(saveFolder);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            saveFolder.mkdir();
        } else {
            saveFolder.mkdir();
        }

        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            // Copy help folders
            String[] directories = f.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return new File(dir, name).isDirectory();
                }
            });

            for (String dName : directories) {
                if (dName.equalsIgnoreCase(langCode)) {
                    f = new File(helpPath + separator + dName);
                    FileUtils.copyDirectory(f, new File(savePath + separator + dName));
                }
            }

            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(null,
                    String.format("Help files were successfully extracted. \r\nCheck \"%s\" folder\r\n", savePath),
                    "Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            btnExtractHelp.setEnabled(true);
            setCursor(null);
        }
    }

    private void importHelp() {
        if (txtHelpSourceFodler.getText().equals("") || txtSolaHelpFolderForImport.getText().equals("")) {
            JOptionPane.showMessageDialog(null, "Fill in required fields", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (JOptionPane.showConfirmDialog(this, "If help folder already exists for "
                + "selected language it will be overridden in SOLA project."
                + "\r\nAre you sure?") != JOptionPane.YES_OPTION) {
            return;
        }

        // Check source folder exists
        File sourceFolder = new File(txtHelpSourceFodler.getText());

        if (!sourceFolder.exists()) {
            JOptionPane.showMessageDialog(this, "Selected source folder doesn't exist", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String langCode = sourceFolder.getName();
        String separator = System.getProperty("file.separator");
        String helpPath = String.format(txtSolaHelpFolderForImport.getText()
                + "%ssrc%smain%sresources%sorg%ssola%scommon%shelp", separator, separator, separator,
                separator, separator, separator, separator);

        File f = new File(helpPath);

        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "Select SOLA help folder (help)", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File saveFolder = new File(helpPath + separator + langCode);

        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            // Copy help folder
            FileUtils.copyDirectory(sourceFolder, saveFolder);

            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(null,
                    "Help files were successfully imported. \r\nCheck SOLA help project",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            btnExtractHelp.setEnabled(true);
            setCursor(null);
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        txtHostName = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        txtPortNumber = new javax.swing.JTextField();
        jPanel5 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        txtUsername = new javax.swing.JTextField();
        jPanel6 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        txtPassword = new javax.swing.JPasswordField();
        jLabel6 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtMessages = new javax.swing.JTextArea();
        jPanel8 = new javax.swing.JPanel();
        jPanel14 = new javax.swing.JPanel();
        jLabel15 = new javax.swing.JLabel();
        txtResourcesFile = new javax.swing.JTextField();
        btnBrowseBundlesSourceFolder = new javax.swing.JButton();
        jPanel13 = new javax.swing.JPanel();
        jLabel14 = new javax.swing.JLabel();
        txtSolaImportFolder = new javax.swing.JTextField();
        btnBrowseSolaRootFolder = new javax.swing.JButton();
        jPanel9 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        txtDBName = new javax.swing.JTextField();
        jPanel10 = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        txtLanguageCode = new javax.swing.JFormattedTextField();
        jLabel10 = new javax.swing.JLabel();
        jPanel22 = new javax.swing.JPanel();
        jPanel11 = new javax.swing.JPanel();
        jLabel11 = new javax.swing.JLabel();
        txtSolaExportFolder = new javax.swing.JTextField();
        btnBrowseSolaPath = new javax.swing.JButton();
        jPanel12 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        txtExportDestinationFolder = new javax.swing.JTextField();
        btnExportPath = new javax.swing.JButton();
        btnExport = new javax.swing.JButton();
        jLabel24 = new javax.swing.JLabel();
        btnImportResources = new javax.swing.JButton();
        jPanel15 = new javax.swing.JPanel();
        jLabel17 = new javax.swing.JLabel();
        jPanel17 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        txtSolaHelpFolder = new javax.swing.JTextField();
        btnSolaHelpPath = new javax.swing.JButton();
        jPanel18 = new javax.swing.JPanel();
        jLabel19 = new javax.swing.JLabel();
        txtExportHelpDestinationFolder = new javax.swing.JTextField();
        btnExportHelpPath = new javax.swing.JButton();
        jPanel19 = new javax.swing.JPanel();
        jLabel20 = new javax.swing.JLabel();
        txtHelpLanguageCode = new javax.swing.JFormattedTextField();
        btnExtractHelp = new javax.swing.JButton();
        btnImportHelp = new javax.swing.JButton();
        jPanel20 = new javax.swing.JPanel();
        jLabel21 = new javax.swing.JLabel();
        txtSolaHelpFolderForImport = new javax.swing.JTextField();
        btnImportHelpPath = new javax.swing.JButton();
        jPanel21 = new javax.swing.JPanel();
        jLabel22 = new javax.swing.JLabel();
        txtHelpSourceFodler = new javax.swing.JTextField();
        btnHelpSourceFolder = new javax.swing.JButton();
        jLabel23 = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        progressBar = new javax.swing.JProgressBar();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/sola/clients/swing/localizer/Bundle"); // NOI18N
        setTitle(bundle.getString("MainForm.title")); // NOI18N

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel1.setText(bundle.getString("MainForm.jLabel1.text")); // NOI18N

        txtHostName.setText(bundle.getString("MainForm.txtHostName.text")); // NOI18N

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addGap(0, 33, Short.MAX_VALUE))
            .addComponent(txtHostName)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtHostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jLabel2.setBackground(new java.awt.Color(0, 102, 102));
        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText(bundle.getString("MainForm.jLabel2.text")); // NOI18N
        jLabel2.setOpaque(true);

        jLabel3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel3.setText(bundle.getString("MainForm.jLabel3.text")); // NOI18N

        txtPortNumber.setText(bundle.getString("MainForm.txtPortNumber.text")); // NOI18N

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jLabel3)
                .addGap(0, 30, Short.MAX_VALUE))
            .addComponent(txtPortNumber)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtPortNumber, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jLabel4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel4.setText(bundle.getString("MainForm.jLabel4.text")); // NOI18N

        txtUsername.setText(bundle.getString("MainForm.txtUsername.text")); // NOI18N

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addComponent(jLabel4)
                .addGap(0, 47, Short.MAX_VALUE))
            .addComponent(txtUsername)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jLabel5.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel5.setText(bundle.getString("MainForm.jLabel5.text")); // NOI18N

        txtPassword.setText(bundle.getString("MainForm.txtPassword.text")); // NOI18N

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(jLabel5)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(txtPassword)
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jLabel6.setText(bundle.getString("MainForm.jLabel6.text")); // NOI18N

        txtMessages.setEditable(false);
        txtMessages.setColumns(20);
        txtMessages.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        txtMessages.setRows(5);
        jScrollPane1.setViewportView(txtMessages);

        jPanel8.setLayout(new java.awt.GridLayout(1, 2, 20, 0));

        jLabel15.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel15.setText(bundle.getString("MainForm.jLabel15.text")); // NOI18N

        txtResourcesFile.setText(bundle.getString("MainForm.txtResourcesFile.text")); // NOI18N
        txtResourcesFile.setToolTipText(bundle.getString("MainForm.txtResourcesFile.toolTipText")); // NOI18N

        btnBrowseBundlesSourceFolder.setText(bundle.getString("MainForm.btnBrowseBundlesSourceFolder.text")); // NOI18N
        btnBrowseBundlesSourceFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseBundlesSourceFolderActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addComponent(jLabel15)
                .addContainerGap(199, Short.MAX_VALUE))
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addComponent(txtResourcesFile)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnBrowseBundlesSourceFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addComponent(jLabel15)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtResourcesFile, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseBundlesSourceFolder)))
        );

        jPanel8.add(jPanel14);

        jLabel14.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel14.setText(bundle.getString("MainForm.jLabel14.text")); // NOI18N

        txtSolaImportFolder.setText(bundle.getString("MainForm.txtSolaImportFolder.text")); // NOI18N

        btnBrowseSolaRootFolder.setText(bundle.getString("MainForm.btnBrowseSolaRootFolder.text")); // NOI18N
        btnBrowseSolaRootFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseSolaRootFolderActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addComponent(jLabel14)
                .addContainerGap(118, Short.MAX_VALUE))
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addComponent(txtSolaImportFolder)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnBrowseSolaRootFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addComponent(jLabel14)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtSolaImportFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseSolaRootFolder)))
        );

        jPanel8.add(jPanel13);

        jLabel8.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel8.setText(bundle.getString("MainForm.jLabel8.text")); // NOI18N

        txtDBName.setText(bundle.getString("MainForm.txtDBName.text")); // NOI18N

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(jLabel8)
                .addGap(0, 30, Short.MAX_VALUE))
            .addComponent(txtDBName)
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtDBName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jLabel9.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel9.setText(bundle.getString("MainForm.jLabel9.text")); // NOI18N

        try {
            txtLanguageCode.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.MaskFormatter("LL-UU")));
        } catch (java.text.ParseException ex) {
            ex.printStackTrace();
        }
        txtLanguageCode.setToolTipText(bundle.getString("MainForm.txtLanguageCode.toolTipText")); // NOI18N

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(jLabel9)
                .addGap(0, 10, Short.MAX_VALUE))
            .addComponent(txtLanguageCode)
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtLanguageCode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel10.setBackground(new java.awt.Color(0, 102, 102));
        jLabel10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(255, 255, 255));
        jLabel10.setText(bundle.getString("MainForm.jLabel10.text")); // NOI18N
        jLabel10.setOpaque(true);

        jPanel22.setLayout(new java.awt.GridLayout(1, 2, 20, 0));

        jLabel11.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel11.setText(bundle.getString("MainForm.jLabel11.text")); // NOI18N

        txtSolaExportFolder.setText(bundle.getString("MainForm.txtSolaExportFolder.text")); // NOI18N

        btnBrowseSolaPath.setText(bundle.getString("MainForm.btnBrowseSolaPath.text")); // NOI18N
        btnBrowseSolaPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseSolaPathActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(jLabel11)
                .addContainerGap(118, Short.MAX_VALUE))
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(txtSolaExportFolder)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnBrowseSolaPath, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtSolaExportFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseSolaPath)))
        );

        jPanel22.add(jPanel11);

        jLabel12.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel12.setText(bundle.getString("MainForm.jLabel12.text")); // NOI18N

        txtExportDestinationFolder.setText(bundle.getString("MainForm.txtExportDestinationFolder.text")); // NOI18N

        btnExportPath.setText(bundle.getString("MainForm.btnExportPath.text")); // NOI18N
        btnExportPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportPathActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(jLabel12)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(txtExportDestinationFolder)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnExportPath, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtExportDestinationFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExportPath)))
        );

        jPanel22.add(jPanel12);

        btnExport.setText(bundle.getString("MainForm.btnExport.text")); // NOI18N
        btnExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportActionPerformed(evt);
            }
        });

        jLabel24.setBackground(new java.awt.Color(0, 102, 102));
        jLabel24.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel24.setForeground(new java.awt.Color(255, 255, 255));
        jLabel24.setText(bundle.getString("MainForm.jLabel24.text")); // NOI18N
        jLabel24.setOpaque(true);

        btnImportResources.setText(bundle.getString("MainForm.btnImportResources.text")); // NOI18N
        btnImportResources.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnImportResourcesActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane1)
                    .addComponent(jLabel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jPanel22, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(btnExport, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel24, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(btnImportResources, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jPanel22, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(btnExport, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addGap(18, 18, 18)
                        .addComponent(jLabel24, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(btnImportResources))
                .addGap(18, 18, 18)
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 88, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab(bundle.getString("MainForm.jPanel1.TabConstraints.tabTitle"), jPanel1); // NOI18N

        jLabel17.setBackground(new java.awt.Color(0, 102, 102));
        jLabel17.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(255, 255, 255));
        jLabel17.setText(bundle.getString("MainForm.jLabel17.text")); // NOI18N
        jLabel17.setOpaque(true);

        jLabel18.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel18.setText(bundle.getString("MainForm.jLabel18.text")); // NOI18N

        txtSolaHelpFolder.setText(bundle.getString("MainForm.txtSolaHelpFolder.text")); // NOI18N
        txtSolaHelpFolder.setToolTipText(bundle.getString("MainForm.txtSolaHelpFolder.toolTipText")); // NOI18N

        btnSolaHelpPath.setText(bundle.getString("MainForm.btnSolaHelpPath.text")); // NOI18N
        btnSolaHelpPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSolaHelpPathActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel17Layout = new javax.swing.GroupLayout(jPanel17);
        jPanel17.setLayout(jPanel17Layout);
        jPanel17Layout.setHorizontalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addComponent(jLabel18)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addComponent(txtSolaHelpFolder)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnSolaHelpPath, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel17Layout.setVerticalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addComponent(jLabel18)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtSolaHelpFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSolaHelpPath)))
        );

        jLabel19.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel19.setText(bundle.getString("MainForm.jLabel19.text")); // NOI18N

        txtExportHelpDestinationFolder.setText(bundle.getString("MainForm.txtExportHelpDestinationFolder.text")); // NOI18N

        btnExportHelpPath.setText(bundle.getString("MainForm.btnExportHelpPath.text")); // NOI18N
        btnExportHelpPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportHelpPathActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel18Layout = new javax.swing.GroupLayout(jPanel18);
        jPanel18.setLayout(jPanel18Layout);
        jPanel18Layout.setHorizontalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addComponent(jLabel19)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addComponent(txtExportHelpDestinationFolder)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnExportHelpPath, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel18Layout.setVerticalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addComponent(jLabel19)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtExportHelpDestinationFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExportHelpPath)))
        );

        jLabel20.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel20.setText(bundle.getString("MainForm.jLabel20.text")); // NOI18N

        try {
            txtHelpLanguageCode.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.MaskFormatter("LL")));
        } catch (java.text.ParseException ex) {
            ex.printStackTrace();
        }
        txtHelpLanguageCode.setToolTipText(bundle.getString("MainForm.txtHelpLanguageCode.toolTipText")); // NOI18N

        javax.swing.GroupLayout jPanel19Layout = new javax.swing.GroupLayout(jPanel19);
        jPanel19.setLayout(jPanel19Layout);
        jPanel19Layout.setHorizontalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addComponent(jLabel20)
                .addGap(0, 23, Short.MAX_VALUE))
            .addComponent(txtHelpLanguageCode)
        );
        jPanel19Layout.setVerticalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addComponent(jLabel20)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 7, Short.MAX_VALUE)
                .addComponent(txtHelpLanguageCode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        btnExtractHelp.setText(bundle.getString("MainForm.btnExtractHelp.text")); // NOI18N
        btnExtractHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExtractHelpActionPerformed(evt);
            }
        });

        btnImportHelp.setText(bundle.getString("MainForm.btnImportHelp.text")); // NOI18N
        btnImportHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnImportHelpActionPerformed(evt);
            }
        });

        jLabel21.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel21.setText(bundle.getString("MainForm.jLabel21.text")); // NOI18N

        txtSolaHelpFolderForImport.setText(bundle.getString("MainForm.txtSolaHelpFolderForImport.text")); // NOI18N
        txtSolaHelpFolderForImport.setToolTipText(bundle.getString("MainForm.txtSolaHelpFolderForImport.toolTipText")); // NOI18N

        btnImportHelpPath.setText(bundle.getString("MainForm.btnImportHelpPath.text")); // NOI18N
        btnImportHelpPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnImportHelpPathActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel20Layout = new javax.swing.GroupLayout(jPanel20);
        jPanel20.setLayout(jPanel20Layout);
        jPanel20Layout.setHorizontalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel21)
            .addGroup(jPanel20Layout.createSequentialGroup()
                .addComponent(txtSolaHelpFolderForImport, javax.swing.GroupLayout.PREFERRED_SIZE, 622, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnImportHelpPath, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel20Layout.setVerticalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel20Layout.createSequentialGroup()
                .addComponent(jLabel21)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtSolaHelpFolderForImport, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnImportHelpPath)))
        );

        jLabel22.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel22.setText(bundle.getString("MainForm.jLabel22.text")); // NOI18N

        txtHelpSourceFodler.setText(bundle.getString("MainForm.txtHelpSourceFodler.text")); // NOI18N
        txtHelpSourceFodler.setToolTipText(bundle.getString("MainForm.txtHelpSourceFodler.toolTipText")); // NOI18N

        btnHelpSourceFolder.setText(bundle.getString("MainForm.btnHelpSourceFolder.text")); // NOI18N
        btnHelpSourceFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHelpSourceFolderActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel21Layout = new javax.swing.GroupLayout(jPanel21);
        jPanel21.setLayout(jPanel21Layout);
        jPanel21Layout.setHorizontalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel22)
            .addGroup(jPanel21Layout.createSequentialGroup()
                .addComponent(txtHelpSourceFodler, javax.swing.GroupLayout.PREFERRED_SIZE, 622, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnHelpSourceFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel21Layout.setVerticalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel21Layout.createSequentialGroup()
                .addComponent(jLabel22)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtHelpSourceFodler, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnHelpSourceFolder)))
        );

        jLabel23.setBackground(new java.awt.Color(0, 102, 102));
        jLabel23.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel23.setForeground(new java.awt.Color(255, 255, 255));
        jLabel23.setText(bundle.getString("MainForm.jLabel23.text")); // NOI18N
        jLabel23.setOpaque(true);

        javax.swing.GroupLayout jPanel15Layout = new javax.swing.GroupLayout(jPanel15);
        jPanel15.setLayout(jPanel15Layout);
        jPanel15Layout.setHorizontalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel15Layout.createSequentialGroup()
                        .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jPanel18, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel19, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnExtractHelp, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jLabel23, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel21, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel20, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel15Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnImportHelp, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel15Layout.setVerticalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel17, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExtractHelp))
                .addGap(18, 18, 18)
                .addComponent(jLabel23, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel21, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel20, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnImportHelp)
                .addContainerGap(82, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab(bundle.getString("MainForm.jPanel15.TabConstraints.tabTitle"), jPanel15); // NOI18N

        jPanel7.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanel7.setForeground(new java.awt.Color(102, 102, 102));
        jPanel7.setPreferredSize(new java.awt.Dimension(727, 23));

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 227, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(5, 5, 5))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel7, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 721, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportActionPerformed
        exportResources();
    }//GEN-LAST:event_btnExportActionPerformed

    private void btnBrowseSolaPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseSolaPathActionPerformed
        selectFolder(txtSolaExportFolder);
    }//GEN-LAST:event_btnBrowseSolaPathActionPerformed

    private void btnExportPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportPathActionPerformed
        selectFolder(txtExportDestinationFolder);
    }//GEN-LAST:event_btnExportPathActionPerformed

    private void btnBrowseSolaRootFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseSolaRootFolderActionPerformed
        selectFolder(txtSolaImportFolder);
    }//GEN-LAST:event_btnBrowseSolaRootFolderActionPerformed

    private void btnBrowseBundlesSourceFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseBundlesSourceFolderActionPerformed
        selectFile(txtResourcesFile, "xls");
    }//GEN-LAST:event_btnBrowseBundlesSourceFolderActionPerformed

    private void btnSolaHelpPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSolaHelpPathActionPerformed
        selectFolder(txtSolaHelpFolder);
    }//GEN-LAST:event_btnSolaHelpPathActionPerformed

    private void btnExportHelpPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportHelpPathActionPerformed
        selectFolder(txtExportHelpDestinationFolder);
    }//GEN-LAST:event_btnExportHelpPathActionPerformed

    private void btnExtractHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExtractHelpActionPerformed
        exportHelp();
    }//GEN-LAST:event_btnExtractHelpActionPerformed

    private void btnImportHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnImportHelpActionPerformed
        importHelp();
    }//GEN-LAST:event_btnImportHelpActionPerformed

    private void btnImportHelpPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnImportHelpPathActionPerformed
        selectFolder(txtSolaHelpFolderForImport);
    }//GEN-LAST:event_btnImportHelpPathActionPerformed

    private void btnHelpSourceFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHelpSourceFolderActionPerformed
        selectFolder(txtHelpSourceFodler);
    }//GEN-LAST:event_btnHelpSourceFolderActionPerformed

    private void btnImportResourcesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnImportResourcesActionPerformed
        importResources();
    }//GEN-LAST:event_btnImportResourcesActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(MainForm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(MainForm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(MainForm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainForm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MainForm().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnBrowseBundlesSourceFolder;
    private javax.swing.JButton btnBrowseSolaPath;
    private javax.swing.JButton btnBrowseSolaRootFolder;
    private javax.swing.JButton btnExport;
    private javax.swing.JButton btnExportHelpPath;
    private javax.swing.JButton btnExportPath;
    private javax.swing.JButton btnExtractHelp;
    private javax.swing.JButton btnHelpSourceFolder;
    private javax.swing.JButton btnImportHelp;
    private javax.swing.JButton btnImportHelpPath;
    private javax.swing.JButton btnImportResources;
    private javax.swing.JButton btnSolaHelpPath;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel19;
    private javax.swing.JPanel jPanel20;
    private javax.swing.JPanel jPanel21;
    private javax.swing.JPanel jPanel22;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JTextField txtDBName;
    private javax.swing.JTextField txtExportDestinationFolder;
    private javax.swing.JTextField txtExportHelpDestinationFolder;
    private javax.swing.JFormattedTextField txtHelpLanguageCode;
    private javax.swing.JTextField txtHelpSourceFodler;
    private javax.swing.JTextField txtHostName;
    private javax.swing.JFormattedTextField txtLanguageCode;
    private javax.swing.JTextArea txtMessages;
    private javax.swing.JPasswordField txtPassword;
    private javax.swing.JTextField txtPortNumber;
    private javax.swing.JTextField txtResourcesFile;
    private javax.swing.JTextField txtSolaExportFolder;
    private javax.swing.JTextField txtSolaHelpFolder;
    private javax.swing.JTextField txtSolaHelpFolderForImport;
    private javax.swing.JTextField txtSolaImportFolder;
    private javax.swing.JTextField txtUsername;
    // End of variables declaration//GEN-END:variables
}
