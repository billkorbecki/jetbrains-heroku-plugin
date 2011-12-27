package com.jetbrains.heroku;

import com.heroku.api.App;
import com.heroku.api.Heroku;
import com.heroku.api.Key;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.Pair;
import com.jetbrains.heroku.service.HerokuApplicationService;
import com.jetbrains.heroku.herokuapi.Credentials;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.jetbrains.heroku.ui.AppsTableModel;
import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jetbrains.heroku.ui.GuiUtil.table;

/**
 * @author mh
 * @since 17.12.11
 */
public class HerokuSettings implements Configurable {

    private JTextField emailField = new JTextField();
    private JPasswordField passwordField = new JPasswordField();
    private JTextField tokenField = new JTextField();
    private final AppsTableModel appsModel= new AppsTableModel();
    private final HerokuApplicationService herokuApplicationService;
    private KeysTableModel keyModel= new KeysTableModel();
    private AtomicInteger selectedKey;
    private AtomicInteger selectedApp;

    public HerokuSettings(HerokuApplicationService herokuApplicationService) {
        this.herokuApplicationService = herokuApplicationService;
    }

    @Nls
    public String getDisplayName() {
        return "Heroku";
    }

    public Icon getIcon() {
        return new ImageIcon("/icons/heroku_icon_16.png");
    }

    public String getHelpTopic() {
        return null;
    }

    public JComponent createComponent() {
        emailField.setColumns(20);
        tokenField.setColumns(20);
        passwordField.setColumns(10);
        reset();
        final DefaultFormBuilder builder = new DefaultFormBuilder(new FormLayout(
                "right:pref, 6dlu, pref, 10dlu, right:pref, 6dlu, pref, 10dlu, pref, 10dlu:grow(0.1)", // columns
                "pref, pref, pref, min(pref;100dlu), pref, min(pref;50dlu)"));// rows
        builder.appendSeparator("Heroku Credentials");
        builder.append("Heroku-Email", emailField);
        builder.append("Password", passwordField);
        builder.append(new JButton(new AbstractAction("Retrieve API-Token") {
            public void actionPerformed(ActionEvent e) {
                final String apiKey = herokuApplicationService.obtainApiToken(emailField.getText(), passwordField.getPassword());
                tokenField.setText(apiKey);
                checkCredentials();
            }
        }),1);
        builder.nextLine();
        builder.append("API-Token");
        builder.append(tokenField, 5);
        builder.append(new JButton(new AbstractAction("Check Credentials") {
            public void actionPerformed(ActionEvent e) {
                checkCredentials();
            }
        }), 1);
        builder.nextLine();
        appendAppsTable(builder);
        appendKeysTable(builder);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                checkCredentials();
            }
        });
        return builder.getPanel();
    }

    private void appendAppsTable(DefaultFormBuilder builder) {
        selectedApp = new AtomicInteger(-1);
        builder.append(table(appsModel,selectedApp), 10);
        builder.append(new JButton(new AbstractAction("Add Application") {
            public void actionPerformed(ActionEvent e) {
                Pair<String, Boolean> newApplicationInfo = Messages.showInputDialogWithCheckBox("Please enter the new Heroku Application Name or leave blank for default:", "New Heroku Application Name", "Cedar stack", true, true, Messages.getQuestionIcon(), null, null);
                final Heroku.Stack stack = newApplicationInfo.second ? Heroku.Stack.Cedar : Heroku.Stack.Bamboo;
                App newApp = herokuApplicationService.createApplication(newApplicationInfo.first, stack);
                GitUIUtil.notifySuccess(null, "Created App", "Sucessfully created App " + newApp.getName() + " on " + newApp.getStack());
                appsModel.update(herokuApplicationService.listApps());
            }
        }), 1);
        builder.append(new JButton(new AbstractAction("Destroy App") {
            public void actionPerformed(ActionEvent e) {
                App app = appsModel.getApplication(selectedApp.get());
                if (app==null) return;
                if (Messages.showYesNoDialog("Really destroy app "+app.getName()+" this is irrecoverable!","Destroy App",Messages.getWarningIcon())!=Messages.YES) return;
                herokuApplicationService.destroyApp(app);
                GitUIUtil.notifySuccess(null, "Created App", "Sucessfully Destroyed App " + app.getName());
                appsModel.update(herokuApplicationService.listApps());
            }
        }), 1);
        builder.nextLine();
    }

    private void appendKeysTable(DefaultFormBuilder builder) {
        selectedKey = new AtomicInteger(-1);
        builder.append(table(keyModel, selectedKey), 10);
        builder.append(new JButton(new AbstractAction("Add Key") {
            public void actionPerformed(ActionEvent e) {
                final String key = Messages.showMultilineInputDialog(null, "Input public ssh key", "Add SSH-KEy", null, Messages.getQuestionIcon(), null);
                herokuApplicationService.addKey(key);
                keyModel.update(herokuApplicationService.listKeys());
            }
        }), 1);
        builder.append(new JButton(new AbstractAction("Remove Key") {
            public void actionPerformed(ActionEvent e) {
                Key key = keyModel.getKey(selectedKey.get());
                if (key==null) return;
                herokuApplicationService.removeKey(key);
                keyModel.update(herokuApplicationService.listKeys());
            }
        }), 1);
    }

    public boolean isModified() {
        return true;
    }

    private String getToken() {
        return String.valueOf(tokenField.getText());
    }

    private String getName() {
        return emailField.getText();
    }

    public void apply() throws ConfigurationException {
        if (testLogin()) {
            herokuApplicationService.update(getName(), getToken());
        }
    }

    public void reset() {
        final Credentials credentials = herokuApplicationService.getCredentials();
        this.emailField.setText(credentials != null ? credentials.getEmail() : null);
        this.tokenField.setText(credentials != null ? credentials.getToken() : null);
    }

    public void disposeUIResources() {
        emailField = null;
        tokenField = null;
    }

    public void checkCredentials() {
        if (testLogin()) {
            GitUIUtil.notifySuccess(null, "Heroku Login", "Heroku Login successful! Authorized: " + getName());
        }
    }

    private boolean testLogin() {
        final Credentials credentials = herokuApplicationService.login(getName(), getToken());
        final boolean validCredentials = credentials != null && credentials.valid();

        if (validCredentials) {
            this.appsModel.update(herokuApplicationService.allApps(credentials));
            this.keyModel.update(herokuApplicationService.listKeys());
        }
        return validCredentials;
    }

    private static class KeysTableModel extends AbstractTableModel {
        private final List<Key> keys=new ArrayList<Key>();

        public void update(List<Key> keys) {
            this.keys.clear();
            this.keys.addAll(keys);
            fireTableDataChanged();
        }
        @Override
        public int getRowCount() {
            return keys.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            final Key key = getKey(rowIndex);
            return columnIndex==0 ? key.getEmail() : key.getContents();
        }

        @Override
        public String getColumnName(int column) {
            return column==0 ? "Email" : "Key";
        }

        public Key getKey(int row) {
            if (row==-1) return null;
            return keys.get(row);
        }
    }
}