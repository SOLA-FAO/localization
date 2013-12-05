package org.sola.clients.swing.localizer;

import java.awt.Cursor;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import org.apache.commons.io.FileUtils;

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

    private class TableColumn {

        String name;
        String type;
        Integer order;

        public TableColumn() {

        }

        public TableColumn(String name, String type, Integer order) {
            this.name = name;
            this.type = type;
            this.order = order;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Integer getOrder() {
            return order;
        }

        public void setOrder(Integer order) {
            this.order = order;
        }
    }

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
                || txtFolderPath.getText().equals("") || txtDBName.getText().equals("")
                || langCode == null) {
            JOptionPane.showMessageDialog(this, "Fill in all required fields", "Warning", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        File f = new File(txtFolderPath.getText());
        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "Selected folder doesn't exist", "Warning", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void exportRefData() {
        if (!checkDBSettings()) {
            return;
        }

        final String langCode = txtLanguageCode.getValue().toString();

        btnExport.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        progressBar.setValue(0);

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

                    progressBar.setMinimum(0);
                    progressBar.setMaximum(refTables.size());
                    int cnt = 0;

                    // Create connection
                    conn = createConnection();
                    Statement cmd = null;

                    // Create folder
                    savePath = txtFolderPath.getText()
                            + System.getProperty("file.separator")
                            + "SOLA DB Resources " + langCode.toUpperCase();

                    File resourceFolder = new File(savePath);

                    if (resourceFolder.isDirectory() && resourceFolder.exists()) {
                        FileUtils.deleteDirectory(resourceFolder);
                        resourceFolder.mkdir();
                    } else {
                        resourceFolder.mkdir();
                    }

                    // Scroll through the reference data tables
                    for (Map.Entry<Object, Object> table : refTables.entrySet()) {
                        progressBar.setValue(cnt);
                        cnt += 1;

                        String tableName = table.getKey().toString();
                        txtMessages.append("Exporting " + tableName);
                        txtMessages.append("\r\n");

                        cmd = conn.createStatement();
                        boolean hasDescription = true;
                        if (tableName.equalsIgnoreCase("system.language")) {
                            hasDescription = false;
                        }

                        String sql;

                        if (hasDescription) {
                            sql = String.format("SELECT code, get_translation(display_value, '%s') as display_value,  "
                                    + "get_translation(description, '%s') as description FROM %s;", langCode, langCode, tableName);
                        } else {
                            sql = String.format("SELECT code, get_translation(display_value, '%s') as display_value  "
                                    + "FROM %s;", langCode, tableName);
                        }

                        ResultSet rs = cmd.executeQuery(sql);

                        // Create file and write
                        BufferedWriter out = null;
                        File file = new File(resourceFolder.getPath() + System.getProperty("file.separator") + tableName + ".txt");

                        try {
                            if (!file.exists()) {
                                file.createNewFile();
                            }
                            
                            FileOutputStream fw = new FileOutputStream(file);
                            //out = new BufferedWriter(new OutputStreamWriter(fw, "UTF-8"));
                            out = new BufferedWriter(new OutputStreamWriter(fw));
                            int i = 0;

                            // Loop thought the table records
                            while (rs.next()) {
                                // add to file
                                if (i > 0) {
                                    out.newLine();
                                    out.newLine();
                                }
                                i = 1;
                                out.write(tableName + "." + rs.getString("code") + ".display_value = " + rs.getString("display_value"));
                                if (hasDescription) {
                                    out.newLine();
                                    String descr = "";
                                    rs.getString("description");
                                    if (!rs.wasNull()) {
                                        descr = rs.getString("description");
                                    }
                                    out.write(tableName + "." + rs.getString("code") + ".description = " + descr);
                                }
                            }

                        } catch (Exception e) {
                            exception = e;
                            return null;
                        } finally {
                            if (out != null) {
                                try {
                                    out.close();
                                } catch (IOException ex) {
                                    exception = ex;
                                    return null;
                                }
                            }
                        }

                        rs.close();
                        txtMessages.append("SUCCESS");
                        txtMessages.append("\r\n");
                    }

                    // Export BRs
                    progressBar.setValue(cnt);
                    String brTable = "system.br";
                    txtMessages.append("Exporting " + brTable);
                    txtMessages.append("\r\n");

                    cmd = conn.createStatement();

                    String sql = String.format("SELECT id, get_translation(feedback, '%s') as feedback FROM %s;",
                            langCode, brTable);

                    ResultSet rs = cmd.executeQuery(sql);

                    // Create file and write
                    BufferedWriter out = null;
                    File file = new File(resourceFolder.getPath() + System.getProperty("file.separator") + brTable + ".txt");

                    try {
                        if (!file.exists()) {
                            file.createNewFile();
                        }
                        FileWriter fw = new FileWriter(file.getAbsoluteFile());
                        out = new BufferedWriter(fw);
                        int i = 0;

                        // Loop thought the table records
                        while (rs.next()) {
                            // add to file
                            if (i > 0) {
                                out.newLine();
                                out.newLine();
                            }
                            i = 1;

                            String feedback = "";

                            rs.getString("feedback");
                            if (!rs.wasNull()) {
                                feedback = rs.getString("feedback");
                            }

                            out.write(brTable + "." + rs.getString("id") + ".feedback = " + feedback);
                        }

                    } catch (Exception e) {
                        exception = e;
                        return null;
                    } finally {
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException ex) {
                                exception = ex;
                                return null;
                            }
                        }
                    }

                    rs.close();
                    txtMessages.append("SUCCESS");
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
                    txtMessages.append("Done!\n");
                }
            }
        };
        task.execute();
    }

    private void importRefData() {
        if (!checkDBSettings()) {
            return;
        }

        if (JOptionPane.showConfirmDialog(this,
                "Make sure you have entered correct language code for updating reference data tables\r\n"
                + "In case of the wrong code provided, existing values can be overridden.\r\n"
                + "Do you want to continue?") != JOptionPane.YES_OPTION) {
            return;
        }

        final String langCode = txtLanguageCode.getValue().toString();
        btnImportRefData.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        progressBar.setValue(0);

        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {

            Exception exception;

            public Void doInBackground() {

                txtMessages.setText("");
                Connection conn = null;

                try {

                    String folderPath = txtFolderPath.getText();
                    File f = new File(folderPath);
                    String[] files = f.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return new File(dir, name).isFile();
                        }
                    });

                    if (files.length < 1) {
                        exception = new Exception("No Reference tables found in the folder.");
                        return null;
                    }

                    progressBar.setMinimum(0);
                    progressBar.setMaximum(files.length - 1);
                    int cnt = 0;

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

                    String separator = System.getProperty("file.separator");

                    // Scroll through the reference data table files
                    for (String fName : files) {
                        progressBar.setValue(cnt);
                        cnt += 1;

                        String tableName = fName.substring(0, fName.lastIndexOf("."));
                        txtMessages.append("Importing " + tableName);
                        txtMessages.append("\r\n");
                        int updatesCount = 0;

                        // Read file and make update statements
                        BufferedReader br = null;

                        try {

                            String line;
                            br = new BufferedReader(new FileReader(folderPath + separator + fName));

                            String pKeyValue = "";
                            String field = "";
                            String value = "";
                            String pKeyField = "code";

                            if (tableName.equalsIgnoreCase("system.br")) {
                                pKeyField = "id";
                            }

                            // Read lines
                            while ((line = br.readLine()) != null) {

                                line = line.trim();
                                if (line.length() < 1) {
                                    continue;
                                }

                                if (line.startsWith(tableName + ".") && line.contains("=")) {
                                    // Update previous value. This is reuired if values is multi lines
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
                                        cmd.executeUpdate(sql);
                                        updatesCount += 1;
                                    }

                                    // Get the code, field and value to update
                                    String[] tmp = line.split("=", 2);
                                    tmp[0] = tmp[0].trim();
                                    String[] tmp2 = tmp[0].split("\\.");

                                    pKeyValue = tmp2[2];
                                    field = tmp2[3];
                                    value = tmp[1].trim().replace("'", "''");

                                } else {
                                    value += "\r\n" + line.replace("'", "''");
                                }
                            }

                            // Update last values
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
                                cmd.executeUpdate(sql);
                                updatesCount += 1;
                            }

                        } catch (Exception e) {
                            exception = e;
                            return null;
                        } finally {
                            try {
                                if (br != null) {
                                    br.close();
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                        txtMessages.append("SUCCESS. " + updatesCount + " updates made.");
                        txtMessages.append("\r\n");
                    }

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
                btnImportRefData.setEnabled(true);
                setCursor(null);

                if (exception != null) {
                    JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(null,
                            "Data was successfully imported. Check SOLA DB",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    txtMessages.append("Done!\n");
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

    private void extractResources() {
        if (txtBundleLanguageCode.getValue() == null || txtSolaExportFolder.getText().equals("")
                || txtExportDestinationFolder.getText().equals("")) {
            JOptionPane.showMessageDialog(null, "Fill in required fields", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check SOLA folder exists
        File f = new File(txtSolaExportFolder.getText());

        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "Selected SOLA root folder doesn't exist", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!f.getName().equals("code")) {
            JOptionPane.showMessageDialog(this, "Selected SOLA root folder (code)", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check destination folder exists
        f = new File(txtExportDestinationFolder.getText());

        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "Selected destination folder doesn't exist", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String langCode = txtBundleLanguageCode.getValue().toString();

        // Create folder
        final String savePath = txtExportDestinationFolder.getText()
                + System.getProperty("file.separator")
                + "SOLA Bundle Resources " + langCode.toUpperCase();

        File resourceFolder = new File(savePath);

        if (resourceFolder.isDirectory() && resourceFolder.exists()) {
            try {
                FileUtils.deleteDirectory(resourceFolder);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            resourceFolder.mkdir();
        } else {
            resourceFolder.mkdir();
        }

        btnExtract.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {
            Exception exception;
            int cnt = 0;

            public Void doInBackground() {
                try {
                    progressBar.setIndeterminate(true);

                    // Go through the folders and copy resources
                    cnt = copyFiles(txtSolaExportFolder.getText(), savePath, langCode, txtSolaExportFolder.getText());
                } catch (Exception e) {
                    exception = e;
                    return null;
                }
                return null;
            }

            @Override
            public void done() {
                btnExtract.setEnabled(true);
                progressBar.setIndeterminate(false);
                setCursor(null);

                if (exception != null) {
                    JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(null,
                            String.format("Bundle files were successfully extracted. \r\nCheck \"%s\" folder\r\n"
                                    + String.valueOf(cnt) + " files were copied.", savePath),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };
        task.execute();
    }

    private void importBundles() {
        if (txtBundlesImportSourceFolder.getText().equals("") || txtBundlesSolaImportFolder.getText().equals("")) {
            JOptionPane.showMessageDialog(null, "Fill in required fields", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check SOLA folder exists
        File f = new File(txtBundlesSolaImportFolder.getText());

        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "Selected SOLA root folder doesn't exist", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!f.getName().equals("code")) {
            JOptionPane.showMessageDialog(this, "Selected SOLA root folder (code)", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check source folder exists
        File sourceFolder = new File(txtBundlesImportSourceFolder.getText());

        if (!sourceFolder.exists()) {
            JOptionPane.showMessageDialog(this, "Selected source folder doesn't exist", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (JOptionPane.showConfirmDialog(this, "If bundle files already exists they will be overridden "
                + "\r\nAre you sure?") != JOptionPane.YES_OPTION) {
            return;
        }

        btnImportBundles.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {
            Exception exception;
            int cnt = 0;

            public Void doInBackground() {
                try {
                    progressBar.setIndeterminate(true);

                    // Go through the source folders and copy resources
                    cnt = copyFiles(txtBundlesImportSourceFolder.getText(),
                            txtBundlesSolaImportFolder.getText(), "", txtBundlesImportSourceFolder.getText());
                } catch (Exception e) {
                    exception = e;
                    return null;
                }
                return null;
            }

            @Override
            public void done() {
                btnImportBundles.setEnabled(true);
                progressBar.setIndeterminate(false);
                setCursor(null);

                if (exception != null) {
                    JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(null,
                            String.format("Bundle files were successfully imported. "
                                    + String.valueOf(cnt) + " files were copied.\r\nCheck SOLA projects."),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };
        task.execute();
    }

    private int copyFiles(final String sourceFolder, final String savePath, final String langCode, String folderPath) {
        File f = new File(folderPath);

        // Copy files
        String[] files = f.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isFile();
            }
        });

        int cnt = 0;

        for (String fName : files) {
            if (fName.endsWith("_" + langCode + ".properties")
                    || (langCode.equals("") && fName.endsWith(".properties"))) {
                String currentFilePath = folderPath + System.getProperty("file.separator") + fName;
                String newFilePath = savePath + System.getProperty("file.separator")
                        + folderPath.substring(sourceFolder.length());

                try {
                    FileUtils.copyFileToDirectory(new File(currentFilePath), new File(newFilePath));
                    cnt += 1;
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return cnt;
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
                cnt += copyFiles(sourceFolder, savePath, langCode,
                        folderPath + System.getProperty("file.separator") + dName);
            }
        }
        return cnt;
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

    private void generateScripts() {
        if (!checkDBSettings()) {
            return;
        }

        btnGenerateBrScripts.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        progressBar.setValue(0);

        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {

            Exception exception;
            String savePath = "";

            public Void doInBackground() {

                txtMessages.setText("");
                Connection conn = null;
                BufferedWriter out = null;

                try {
                    // Create folder
                    savePath = txtFolderPath.getText()
                            + System.getProperty("file.separator")
                            + "SOLA DB Scripts";

                    File resourceFolder = new File(savePath);

                    if (resourceFolder.isDirectory() && resourceFolder.exists()) {
                        FileUtils.deleteDirectory(resourceFolder);
                        resourceFolder.mkdir();
                    } else {
                        resourceFolder.mkdir();
                    }

                    // Create connection
                    conn = createConnection();
                    Statement cmd = conn.createStatement();
                    Statement cmd2 = conn.createStatement();
                    String sql = "SELECT code FROM system.br_validation_target_type";

                    ResultSet rs = cmd.executeQuery(sql);
                    ResultSet rs2;
                    ArrayList<String> targets = new ArrayList<String>();

                    while (rs.next()) {
                        targets.add(rs.getString("code"));
                    }

                    rs.close();

                    if (targets.size() < 1) {
                        exception = new Exception("No BR targets found");
                        return null;
                    }

                    targets.add("generators");

                    // Get reference data tables list
                    String[] refTables = getRefTablesList();

                    if (refTables == null) {
                        exception = new Exception("No Reference tables found in the properties.");
                        return null;
                    }

                    progressBar.setMinimum(0);
                    progressBar.setMaximum(targets.size() + refTables.length);
                    int cnt = 0;

                    String dashes = "----------------------------------------------------------------------------------------------------";
                    String brTemplate = "INSERT INTO system.br(id, technical_type_code, feedback, technical_description) \r\n"
                            + "VALUES ('%s', '%s', '%s', '%s');";
                    String brDefinitionTemplate = "INSERT INTO system.br_definition(br_id, active_from, active_until, body) \r\n"
                            + "VALUES ('%s', now(), 'infinity', '%s');";
                    String brValidationTemplate = "INSERT INTO system.br_validation(br_id, target_code, target_application_moment, severity_code, order_of_execution) \r\n"
                            + "VALUES ('%s', '%s', '%s', '%s', %s);";
                    String lastUpdate = "update system.br set display_name = id where display_name !=id;";

                    txtMessages.append("Generating business rules script");
                    txtMessages.append("\r\n");

                    // Scroll through the target objects
                    for (String target : targets) {
                        progressBar.setValue(cnt);
                        cnt += 1;

                        txtMessages.append("Generating scripts for " + target);
                        txtMessages.append("\r\n");

                        if (target.equals("generators")) {
                            // Get BRs without targets
                            sql = "SELECT id, technical_type_code, feedback, technical_description FROM system.br "
                                    + "WHERE id NOT IN (SELECT br_id FROM system.br_validation)";
                        } else {
                            // Get BRs by target
                            sql = "SELECT id, technical_type_code, feedback, technical_description FROM system.br "
                                    + "WHERE id IN (SELECT br_id FROM system.br_validation WHERE target_code = '" + target + "')";
                        }

                        rs = cmd.executeQuery(sql);

                        try {
                            // Create file
                            String fileName = savePath + System.getProperty("file.separator") + "br_target_" + target + ".sql";
                            if (target.equals("generators")) {
                                fileName = savePath + System.getProperty("file.separator") + "br_" + target + ".sql";
                            }

                            File file = new File(fileName);
                            if (!file.exists()) {
                                file.createNewFile();
                            }

                            FileOutputStream fw = new FileOutputStream(file);
                            out = new BufferedWriter(new OutputStreamWriter(fw, "UTF-8"));

                            while (rs.next()) {
                                String brId = prepareString(rs.getString("id"));

                                out.write(String.format(brTemplate,
                                        brId,
                                        prepareString(rs.getString("technical_type_code")),
                                        prepareString(rs.getString("feedback")),
                                        prepareString(rs.getString("technical_description"))));

                                out.newLine();
                                out.newLine();

                                // Get definitions
                                sql = "SELECT br_id, active_from, active_until, body FROM system.br_definition WHERE br_id = '" + brId + "'";
                                rs2 = cmd2.executeQuery(sql);

                                while (rs2.next()) {
                                    out.write(String.format(brDefinitionTemplate,
                                            brId,
                                            prepareString(rs2.getString("body"))));
                                    out.newLine();
                                    out.newLine();
                                }
                                rs2.close();

                                // Get validations
                                sql = "SELECT br_id, target_code, target_application_moment, severity_code, order_of_execution FROM system.br_validation WHERE br_id = '" + brId + "'";
                                rs2 = cmd2.executeQuery(sql);

                                while (rs2.next()) {
                                    out.write(String.format(brValidationTemplate,
                                            brId,
                                            prepareString(rs2.getString("target_code")),
                                            prepareString(rs2.getString("target_application_moment")),
                                            prepareString(rs2.getString("severity_code")),
                                            rs2.getInt("order_of_execution")));
                                    out.newLine();
                                    out.newLine();
                                }
                                rs2.close();

                                out.write(dashes);
                                out.newLine();
                                out.newLine();
                            }

                            out.write(lastUpdate);
                            out.newLine();
                            rs.close();

                        } catch (Exception e) {
                            exception = e;
                            return null;
                        } finally {
                            if (out != null) {
                                try {
                                    out.close();
                                } catch (IOException ex) {
                                    exception = ex;
                                    return null;
                                }
                            }
                        }

                        txtMessages.append("SUCCESS");
                        txtMessages.append("\r\n");
                    }

                    // Generate reference data script
                    txtMessages.append("Generating script for reference data tables");
                    txtMessages.append("\r\n");

                    String insertTemplate = "INSERT INTO %s (%s) \r\nVALUES (%s);";

                    try {
                        // Create reference data file
                        String fileName = savePath + System.getProperty("file.separator") + "reference_tables.sql";

                        File file = new File(fileName);
                        if (!file.exists()) {
                            file.createNewFile();
                        }

                        FileOutputStream fw = new FileOutputStream(file);
                        out = new BufferedWriter(new OutputStreamWriter(fw, "UTF-8"));

                        // Scroll through reference data tables
                        for (String refTable : refTables) {
                            progressBar.setValue(cnt);
                            cnt += 1;

                            String fullTableName = refTable;
                            String tableName = fullTableName.split("\\.")[1].trim();
                            String schemaName = fullTableName.split("\\.")[0].trim();

                            txtMessages.append("Generating statements for " + fullTableName + " table");
                            txtMessages.append("\r\n");

                            sql = "SELECT column_name, data_type, ordinal_position FROM information_schema.columns "
                                    + "WHERE table_name = '" + tableName
                                    + "' AND table_schema = '" + schemaName + "' ORDER BY ordinal_position";
                            rs = cmd.executeQuery(sql);

                            ArrayList<TableColumn> columns = new ArrayList<TableColumn>();
                            String columnNames = "";
                            String columnValues = "";

                            while (rs.next()) {
                                columns.add(new TableColumn(rs.getString("column_name"),
                                        rs.getString("data_type"), rs.getInt("ordinal_position")));
                            }
                            rs.close();

                            Collections.sort(columns, new Comparator<TableColumn>() {

                                @Override
                                public int compare(TableColumn s1, TableColumn s2) {
                                    if (s1.getOrder() < s2.getOrder()) {
                                        return -1;
                                    } else if (s1.getOrder() > s2.getOrder()) {
                                        return 1;
                                    } else {
                                        return 0;
                                    }
                                }
                            });

                            String columnName;

                            // Make column names string
                            for (TableColumn column : columns) {
                                if (columnNames.length() > 0) {
                                    columnNames += ", ";
                                }
                                columnNames += column.getName();
                            }

                            if (columnNames.equals("")) {
                                continue;
                            }

                            // Get reference data table values and generate insert statements
                            sql = "SELECT " + columnNames + " FROM " + fullTableName;
                            rs = cmd.executeQuery(sql);

                            // Scroll through the rows
                            while (rs.next()) {
                                columnValues = "";
                                for (TableColumn column : columns) {
                                    columnName = column.getName();

                                    // Generate values to insert
                                    if (columnValues.length() > 0) {
                                        columnValues += ", ";
                                    }

                                    if (column.getType().equalsIgnoreCase("integer")
                                            || column.getType().equalsIgnoreCase("numeric")) {
                                        rs.getInt(columnName);
                                        if (rs.wasNull()) {
                                            columnValues += "null";
                                        } else {
                                            columnValues += rs.getString(columnName);
                                        }
                                    } else {
                                        rs.getString(columnName);
                                        if (rs.wasNull()) {
                                            columnValues += "null";
                                        } else {
                                            columnValues += "'" + rs.getString(columnName).replace("'", "''") + "'";
                                        }
                                    }
                                }

                                // Write to file
                                try {
                                    out.write(String.format(insertTemplate, fullTableName, columnNames, columnValues));
                                    out.newLine();
                                    out.newLine();
                                } catch (Exception e) {
                                    System.out.println(e.getMessage());
                                }
                                
                            }
                            rs.close();

                            out.write(dashes);
                            out.newLine();
                            out.newLine();

                            txtMessages.append("SUCCESS");
                            txtMessages.append("\r\n");
                        }

                    } catch (Exception e) {
                        exception = e;
                        return null;
                    } finally {
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException ex) {
                                exception = ex;
                                return null;
                            }
                        }
                    }

                    progressBar.setValue(cnt);
                    cmd.close();
                    cmd2.close();

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
                btnGenerateBrScripts.setEnabled(true);
                setCursor(null);

                if (exception != null) {
                    JOptionPane.showMessageDialog(null, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane.showMessageDialog(null,
                            String.format(
                                    "Scripts were successfully generated. \r\nCheck \"%s\" folder",
                                    savePath),
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    txtMessages.append("Done!\n");
                }
            }
        };
        task.execute();
    }

    // Returns ordered list of reference data tables from properties file
    private String[] getRefTablesList() {
        String[] refTables = null;
        String[] tmpArray = new String[200];
        BufferedReader br = null;
        int i = 0;

        try {
            String line;
            br = new BufferedReader(new InputStreamReader(MainForm.class.getResourceAsStream("/org/sola/clients/swing/localizer/ReferenceDataTables.properties")));

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() < 1) {
                    continue;
                }
                tmpArray[i] = line;
                i += 1;
            }

            if (i > 0) {
                refTables = new String[i];
                for (int j = 0; j <= i; j++) {
                    refTables[j] = tmpArray[j];
                }
            }
            
        } catch (Exception e) {
            return refTables;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return refTables;
    }

    private String prepareString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("'", "''");
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
        jLabel7 = new javax.swing.JLabel();
        txtFolderPath = new javax.swing.JTextField();
        btnBrowse = new javax.swing.JButton();
        btnExport = new javax.swing.JButton();
        btnImportRefData = new javax.swing.JButton();
        btnGenerateBrScripts = new javax.swing.JButton();
        jPanel9 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        txtDBName = new javax.swing.JTextField();
        jPanel10 = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        txtLanguageCode = new javax.swing.JFormattedTextField();
        jPanel2 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        btnExtract = new javax.swing.JButton();
        jLabel13 = new javax.swing.JLabel();
        jPanel13 = new javax.swing.JPanel();
        jLabel14 = new javax.swing.JLabel();
        txtBundlesSolaImportFolder = new javax.swing.JTextField();
        btnBrowseSolaRootFolder = new javax.swing.JButton();
        jPanel14 = new javax.swing.JPanel();
        jLabel15 = new javax.swing.JLabel();
        txtBundlesImportSourceFolder = new javax.swing.JTextField();
        btnBrowseBundlesSourceFolder = new javax.swing.JButton();
        btnImportBundles = new javax.swing.JButton();
        jPanel16 = new javax.swing.JPanel();
        jLabel16 = new javax.swing.JLabel();
        txtBundleLanguageCode = new javax.swing.JFormattedTextField();
        jPanel12 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        txtExportDestinationFolder = new javax.swing.JTextField();
        btnExportPath = new javax.swing.JButton();
        jPanel11 = new javax.swing.JPanel();
        jLabel11 = new javax.swing.JLabel();
        txtSolaExportFolder = new javax.swing.JTextField();
        btnBrowseSolaPath = new javax.swing.JButton();
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
                .addGap(0, 0, Short.MAX_VALUE))
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
                .addGap(0, 80, Short.MAX_VALUE))
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
                .addGap(66, 113, Short.MAX_VALUE))
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

        jLabel7.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel7.setText(bundle.getString("MainForm.jLabel7.text")); // NOI18N

        txtFolderPath.setText(bundle.getString("MainForm.txtFolderPath.text")); // NOI18N

        btnBrowse.setText(bundle.getString("MainForm.btnBrowse.text")); // NOI18N
        btnBrowse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBrowseActionPerformed(evt);
            }
        });

        btnExport.setText(bundle.getString("MainForm.btnExport.text")); // NOI18N
        btnExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportActionPerformed(evt);
            }
        });

        btnImportRefData.setText(bundle.getString("MainForm.btnImportRefData.text")); // NOI18N
        btnImportRefData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnImportRefDataActionPerformed(evt);
            }
        });

        btnGenerateBrScripts.setText(bundle.getString("MainForm.btnGenerateBrScripts.text")); // NOI18N
        btnGenerateBrScripts.setToolTipText(bundle.getString("MainForm.btnGenerateBrScripts.toolTipText")); // NOI18N
        btnGenerateBrScripts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGenerateBrScriptsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addComponent(txtFolderPath, javax.swing.GroupLayout.PREFERRED_SIZE, 241, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnBrowse, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(btnExport, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnImportRefData, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnGenerateBrScripts)))
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtFolderPath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowse)
                    .addComponent(btnExport)
                    .addComponent(btnImportRefData)
                    .addComponent(btnGenerateBrScripts)))
        );

        jLabel8.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel8.setText(bundle.getString("MainForm.jLabel8.text")); // NOI18N

        txtDBName.setText(bundle.getString("MainForm.txtDBName.text")); // NOI18N

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(jLabel8)
                .addGap(0, 0, Short.MAX_VALUE))
            .addComponent(txtDBName, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
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

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 709, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 222, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab(bundle.getString("MainForm.jPanel1.TabConstraints.tabTitle"), jPanel1); // NOI18N

        jLabel10.setBackground(new java.awt.Color(0, 102, 102));
        jLabel10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(255, 255, 255));
        jLabel10.setText(bundle.getString("MainForm.jLabel10.text")); // NOI18N
        jLabel10.setOpaque(true);

        btnExtract.setText(bundle.getString("MainForm.btnExtract.text")); // NOI18N
        btnExtract.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExtractActionPerformed(evt);
            }
        });

        jLabel13.setBackground(new java.awt.Color(0, 102, 102));
        jLabel13.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(255, 255, 255));
        jLabel13.setText(bundle.getString("MainForm.jLabel13.text")); // NOI18N
        jLabel13.setOpaque(true);

        jLabel14.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel14.setText(bundle.getString("MainForm.jLabel14.text")); // NOI18N

        txtBundlesSolaImportFolder.setText(bundle.getString("MainForm.txtBundlesSolaImportFolder.text")); // NOI18N

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
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addComponent(txtBundlesSolaImportFolder)
                .addGap(18, 18, 18)
                .addComponent(btnBrowseSolaRootFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addComponent(jLabel14)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtBundlesSolaImportFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseSolaRootFolder)))
        );

        jLabel15.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel15.setText(bundle.getString("MainForm.jLabel15.text")); // NOI18N

        txtBundlesImportSourceFolder.setText(bundle.getString("MainForm.txtBundlesImportSourceFolder.text")); // NOI18N
        txtBundlesImportSourceFolder.setToolTipText(bundle.getString("MainForm.txtBundlesImportSourceFolder.toolTipText")); // NOI18N

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
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addComponent(txtBundlesImportSourceFolder)
                .addGap(18, 18, 18)
                .addComponent(btnBrowseBundlesSourceFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addComponent(jLabel15)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtBundlesImportSourceFolder, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowseBundlesSourceFolder)))
        );

        btnImportBundles.setText(bundle.getString("MainForm.btnImportBundles.text")); // NOI18N
        btnImportBundles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnImportBundlesActionPerformed(evt);
            }
        });

        jLabel16.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/red_asterisk.gif"))); // NOI18N
        jLabel16.setText(bundle.getString("MainForm.jLabel16.text")); // NOI18N

        try {
            txtBundleLanguageCode.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.MaskFormatter("LL_UU")));
        } catch (java.text.ParseException ex) {
            ex.printStackTrace();
        }
        txtBundleLanguageCode.setToolTipText(bundle.getString("MainForm.txtBundleLanguageCode.toolTipText")); // NOI18N

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addComponent(jLabel16)
                .addGap(0, 23, Short.MAX_VALUE))
            .addComponent(txtBundleLanguageCode)
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addComponent(jLabel16)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 7, Short.MAX_VALUE)
                .addComponent(txtBundleLanguageCode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

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
                .addContainerGap(404, Short.MAX_VALUE))
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

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(39, 39, 39)
                        .addComponent(btnExtract, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnImportBundles, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExtract))
                .addGap(18, 18, 18)
                .addComponent(jLabel13, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnImportBundles)
                .addContainerGap(86, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab(bundle.getString("MainForm.jPanel2.TabConstraints.tabTitle"), jPanel2); // NOI18N

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
                .addContainerGap(86, Short.MAX_VALUE))
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
            .addComponent(jPanel7, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 754, Short.MAX_VALUE)
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
        exportRefData();
    }//GEN-LAST:event_btnExportActionPerformed

    private void btnBrowseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseActionPerformed
        selectFolder(txtFolderPath);
    }//GEN-LAST:event_btnBrowseActionPerformed

    private void btnBrowseSolaPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseSolaPathActionPerformed
        selectFolder(txtSolaExportFolder);
    }//GEN-LAST:event_btnBrowseSolaPathActionPerformed

    private void btnExtractActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExtractActionPerformed
        extractResources();
    }//GEN-LAST:event_btnExtractActionPerformed

    private void btnExportPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportPathActionPerformed
        selectFolder(txtExportDestinationFolder);
    }//GEN-LAST:event_btnExportPathActionPerformed

    private void btnBrowseSolaRootFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseSolaRootFolderActionPerformed
        selectFolder(txtBundlesSolaImportFolder);
    }//GEN-LAST:event_btnBrowseSolaRootFolderActionPerformed

    private void btnBrowseBundlesSourceFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBrowseBundlesSourceFolderActionPerformed
        selectFolder(txtBundlesImportSourceFolder);
    }//GEN-LAST:event_btnBrowseBundlesSourceFolderActionPerformed

    private void btnImportBundlesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnImportBundlesActionPerformed
        importBundles();
    }//GEN-LAST:event_btnImportBundlesActionPerformed

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

    private void btnImportRefDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnImportRefDataActionPerformed
        importRefData();
    }//GEN-LAST:event_btnImportRefDataActionPerformed

    private void btnGenerateBrScriptsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGenerateBrScriptsActionPerformed
        generateScripts();
    }//GEN-LAST:event_btnGenerateBrScriptsActionPerformed

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
    private javax.swing.JButton btnBrowse;
    private javax.swing.JButton btnBrowseBundlesSourceFolder;
    private javax.swing.JButton btnBrowseSolaPath;
    private javax.swing.JButton btnBrowseSolaRootFolder;
    private javax.swing.JButton btnExport;
    private javax.swing.JButton btnExportHelpPath;
    private javax.swing.JButton btnExportPath;
    private javax.swing.JButton btnExtract;
    private javax.swing.JButton btnExtractHelp;
    private javax.swing.JButton btnGenerateBrScripts;
    private javax.swing.JButton btnHelpSourceFolder;
    private javax.swing.JButton btnImportBundles;
    private javax.swing.JButton btnImportHelp;
    private javax.swing.JButton btnImportHelpPath;
    private javax.swing.JButton btnImportRefData;
    private javax.swing.JButton btnSolaHelpPath;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel19;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel20;
    private javax.swing.JPanel jPanel21;
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
    private javax.swing.JFormattedTextField txtBundleLanguageCode;
    private javax.swing.JTextField txtBundlesImportSourceFolder;
    private javax.swing.JTextField txtBundlesSolaImportFolder;
    private javax.swing.JTextField txtDBName;
    private javax.swing.JTextField txtExportDestinationFolder;
    private javax.swing.JTextField txtExportHelpDestinationFolder;
    private javax.swing.JTextField txtFolderPath;
    private javax.swing.JFormattedTextField txtHelpLanguageCode;
    private javax.swing.JTextField txtHelpSourceFodler;
    private javax.swing.JTextField txtHostName;
    private javax.swing.JFormattedTextField txtLanguageCode;
    private javax.swing.JTextArea txtMessages;
    private javax.swing.JPasswordField txtPassword;
    private javax.swing.JTextField txtPortNumber;
    private javax.swing.JTextField txtSolaExportFolder;
    private javax.swing.JTextField txtSolaHelpFolder;
    private javax.swing.JTextField txtSolaHelpFolderForImport;
    private javax.swing.JTextField txtUsername;
    // End of variables declaration//GEN-END:variables
}
