package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;

import org.mindrot.jbcrypt.BCrypt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Provides direct access to the config.xml file in the file system.
 *
 * This class should only be used if the syncthing API is not available (usually during startup).
 */
public class ConfigXml {

    public class OpenConfigException extends RuntimeException {
    }

    private static final String TAG = "ConfigXml";

    private final Context mContext;
    @Inject SharedPreferences mPreferences;

    private final File mConfigFile;

    private Document mConfig;

    public ConfigXml(Context context) throws OpenConfigException {
        mContext = context;
        mConfigFile = Constants.getConfigFile(mContext);
        boolean isFirstStart = !mConfigFile.exists();
        if (isFirstStart) {
            Log.i(TAG, "App started for the first time. Generating keys and config.");
            generateKeysConfig(context);
        }

        readConfig();

        if (isFirstStart) {
            boolean changed = false;

            /* Synthing devices */
            changed = changeLocalDeviceName() || changed;

            /* Syncthing folders */
            changed = changeDefaultFolder() || changed;

            /* Syncthing options */
            /**
              * As verifying the impact of disabling restartOnWakeup is not
              * completed yet, we stick to what has been default in the past
              * and keep this syncthing core feature enabled.
              * After additional tests, we want to disable restartOnWakeup
              * as advised by calmh, Nutomic, AudriusButkevicius to save battery.
              * see https://github.com/syncthing/syncthing-android/issues/368
              * and https://forum.syncthing.net/t/question-about-restartonwakeup-setting/2222/11
              */
            Element options = (Element) mConfig.getDocumentElement()
                    .getElementsByTagName("options").item(0);
            changed = setConfigElement(options, "restartOnWakeup", "true") || changed;

            // Save changes if we made any.
            if (changed) {
                saveChanges();
            }
        }
    }

    private void readConfig() {
        if (!mConfigFile.canRead() && !Util.fixAppDataPermissions(mContext)) {
            throw new OpenConfigException();
        }
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Log.d(TAG, "Trying to read '" + mConfigFile + "'");
            mConfig = db.parse(mConfigFile);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            Log.w(TAG, "Cannot read '" + mConfigFile + "'", e);
            throw new OpenConfigException();
        }
        Log.i(TAG, "Loaded Syncthing config file");
    }

    private void generateKeysConfig(Context context) {
        new SyncthingRunnable(context, SyncthingRunnable.Command.generate).run();
    }

    public URL getWebGuiUrl() {
        try {
            return new URL("https://" + getGuiElement().getElementsByTagName("address").item(0).getTextContent());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse web interface URL", e);
        }
    }

    public String getApiKey() {
        return getGuiElement().getElementsByTagName("apikey").item(0).getTextContent();
    }

    public String getUserName() {
        return getGuiElement().getElementsByTagName("user").item(0).getTextContent();
    }

    /**
     * Updates the config file.
     *
     * Sets ignorePerms flag to true on every folder, force enables TLS, sets the
     * username/password, and disables weak hash checking.
     */
    @SuppressWarnings("SdCardPath")
    public void updateIfNeeded() {
        boolean changed = false;

        /* Get preference - PREF_USE_FOLDER_OBSERVER */
        boolean prefUseFolderObserver = false;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefUseFolderObserver = mPreferences.getBoolean(Constants.PREF_USE_FOLDER_OBSERVER, false);

        /* Read existing config version */
        int iConfigVersion = Integer.parseInt(mConfig.getDocumentElement().getAttribute("version"));
        Log.i(TAG, "Found existing config version " + Integer.toString(iConfigVersion));

        /* Get refs to important config objects */
        NodeList folders = mConfig.getDocumentElement().getElementsByTagName("folder");

        /* Check if we have to do manual migration from version X to Y */
        if (iConfigVersion == 27) {
          /* fsWatcher transition - https://github.com/syncthing/syncthing/issues/4882 */
          Log.i(TAG, "Migrating config version " + Integer.toString(iConfigVersion) + " to 28 ...");

          /* Enable fsWatcher for all folders */
          for (int i = 0; i < folders.getLength(); i++) {
            Element r = (Element) folders.item(i);

            // Enable "fsWatcherEnabled" attribute.
            if (!r.hasAttribute("fsWatcherEnabled") ||
                    !Boolean.parseBoolean(r.getAttribute("fsWatcherEnabled"))) {
              Log.i(TAG, "Set 'fsWatcherEnabled' on folder " + r.getAttribute("id"));
              r.setAttribute("fsWatcherEnabled", Boolean.toString(true));
              changed = true;
            }
          }

          /**
            * Set config version to 28 after manual config migration
            * This prevents "unackedNotificationID" getting populated
            * with the fsWatcher GUI notification.
            */
          iConfigVersion = 28;
          mConfig.getDocumentElement().setAttribute("version", Integer.toString(iConfigVersion));
          Log.i(TAG, "Config version " + Integer.toString(iConfigVersion) + " reached.");
        }

        /**
          * Force-disable fsWatcher for all folders if prefUseFolderObserver has been manually set
          * in experimental options. This should give users the option to go back to the legacy
          * implementation with FolderObserver watching for file changes instead of fsWatcher.
          * Intended to be advised in the issue tracker if a user encounters a critical bug with
          * the new fsWatcher. To be removed in a future release.
        */
        if (prefUseFolderObserver) {
          Log.i(TAG, "Disabling fsWatcher on all folders because experimental option to use FolderObserver was manually set.");
          for (int i = 0; i < folders.getLength(); i++) {
            Element r = (Element) folders.item(i);

            // Disable "fsWatcherEnabled" attribute.
            if (r.hasAttribute("fsWatcherEnabled")) {
              r.setAttribute("fsWatcherEnabled", Boolean.toString(false));
              changed = true;
            }
          }
        }

        /* Section - folders */
        for (int i = 0; i < folders.getLength(); i++) {
            Element r = (Element) folders.item(i);
            // Set ignorePerms attribute.
            if (!r.hasAttribute("ignorePerms") ||
                    !Boolean.parseBoolean(r.getAttribute("ignorePerms"))) {
                Log.i(TAG, "Set 'ignorePerms' on folder " + r.getAttribute("id"));
                r.setAttribute("ignorePerms", Boolean.toString(true));
                changed = true;
            }

            // Set 'hashers' (see https://github.com/syncthing/syncthing-android/issues/384) on the
            // given folder.
            changed = setConfigElement(r, "hashers", "1") || changed;
        }

        /* Section - GUI */
        // Enforce TLS.
        Element gui = getGuiElement();
        changed = setConfigElement(gui, "tls", "true") || changed;

        // Set user to "syncthing"
        changed = setConfigElement(gui, "user", "syncthing") || changed;

        // Set password to the API key
        Node password = gui.getElementsByTagName("password").item(0);
        if (password == null) {
            password = mConfig.createElement("password");
            gui.appendChild(password);
        }
        String apikey = getApiKey();
        String pw = password.getTextContent();
        boolean passwordOk;
        try {
            passwordOk = !TextUtils.isEmpty(pw) && BCrypt.checkpw(apikey, pw);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Malformed password", e);
            passwordOk = false;
        }
        if (!passwordOk) {
            Log.i(TAG, "Updating password");
            password.setTextContent(BCrypt.hashpw(apikey, BCrypt.gensalt(4)));
            changed = true;
        }

        /* Section - options */
        // Disable weak hash benchmark for faster startup.
        // https://github.com/syncthing/syncthing/issues/4348
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);
        changed = setConfigElement(options, "weakHashSelectionMethod", "never") || changed;

        /* Check if we have to dismiss any specific "unackedNotificationID" */
        /* Dismiss "fsWatcherNotification" according to https://github.com/syncthing/syncthing-android/pull/1051 */
        if (getConfigElement(options, "unackedNotificationID").contains("fsWatcherNotification")) {
          Log.i(TAG, "Remove option 'unackedNotificationID' to dismiss 'fsWatcherNotification'.");
          // According to review, it is sufficient to remove all "unackedNotificationID" contents.
          // changed = setConfigElement(options, "unackedNotificationID", getConfigElement(options, "unackedNotificationID").replace("fsWatcherNotification", "")) || changed;
          options.removeChild(options.getElementsByTagName("unackedNotificationID").item(0));
          changed = true;
        }

        // Save changes if we made any.
        if (changed) {
            saveChanges();
        }
    }

    private String getConfigElement(Element parent, String tagName) {
        Node element = parent.getElementsByTagName(tagName).item(0);
        if (element == null) {
            return "";
        }
        return (String) element.getTextContent();
    }

    private boolean setConfigElement(Element parent, String tagName, String textContent) {
        Node element = parent.getElementsByTagName(tagName).item(0);
        if (element == null) {
            element = mConfig.createElement(tagName);
            parent.appendChild(element);
        }
        if (!textContent.equals(element.getTextContent())) {
            element.setTextContent(textContent);
            return true;
        }
        return false;
    }

    private Element getGuiElement() {
        return (Element) mConfig.getDocumentElement().getElementsByTagName("gui").item(0);
    }

    /**
     * Set model name as device name for Syncthing.
     *
     * We need to iterate through XML nodes manually, as mConfig.getDocumentElement() will also
     * return nested elements inside folder element.
     *
     * Returns if changes to the config have been made.
     */
    private boolean changeLocalDeviceName() {
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Log.i(TAG, "changeLocalDeviceName: Current device name updated to build.MODEL.");
                ((Element) node).setAttribute("name", Build.MODEL);

                /**
                  * Only alter the first occurency of the device tag in assumption it is the device running this instance.
                  * Reason: Sometimes encountered a bug when manually restored syncthing db+config with root explorer
                  *         where app started and all devices got their name altered to the model name. Config was messed up then.
                  *         This should work around the problem at least until a better fix will be implemented.
                  * ToDo:   Implement a better fix by reading the device ID first, e.g. from REST API and only alter the correct
                  *         node's device name attribute.
                  */
                return true;
            }
        }

        // No changes have been made.
        return false;
    }

    /**
     * Change default folder id to camera and path to camera folder path.
     * Returns if changes to the config have been made.
     */
    private boolean changeDefaultFolder() {
        Element folder = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("folder").item(0);
        String model = Build.MODEL
                .replace(" ", "_")
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_-]", "");
        folder.setAttribute("label", mContext.getString(R.string.default_folder_label));
        folder.setAttribute("id", mContext.getString(R.string.default_folder_id, model));
        folder.setAttribute("path", Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        folder.setAttribute("type", "readonly");
        return true;
    }

    /**
     * Writes updated mConfig back to file.
     */
    private void saveChanges() {
        if (!mConfigFile.canWrite() && !Util.fixAppDataPermissions(mContext)) {
            Log.w(TAG, "Failed to save updated config. Cannot change the owner of the config file.");
            return;
        }
        try {
            Log.i(TAG, "Writing updated config back to file");
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(mConfig);
            StreamResult streamResult = new StreamResult(mConfigFile);
            transformer.transform(domSource, streamResult);
        } catch (TransformerException e) {
            Log.w(TAG, "Failed to save updated config", e);
        }
    }
}
