/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.*;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.scripted_cloud.folder.FolderScriptedCloudProperty;
import org.jenkinsci.plugins.scripted_cloud.model.EnvironmentVariable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;


/**
 * @author Admin
 */
public class ScriptedCloud extends AbstractCloudImpl {

    private final String scHost;
    private String startScript;
    private String stopScript;

    private static java.util.logging.Logger VSLOG = java.util.logging.Logger.getLogger("scripted-cloud");

    private static void InternalLog(Slave slave, SlaveComputer slaveComputer, TaskListener listener, Throwable ex, Level logLevel, String format, Object... args) {
        if (!VSLOG.isLoggable(logLevel) && listener == null)
            return;
        String s = "";
        if (slave != null)
            s = String.format("[%s] ", slave.getNodeName());
        if (slaveComputer != null)
            s = String.format("[%s] ", slaveComputer.getName());
        s = s + String.format(format, args);
        if (listener != null) {
            listener.getLogger().print(s + "\n");
            if (ex != null) {
                listener.getLogger().print(ex.toString() + "\n");
                ex.printStackTrace(listener.getLogger());
            }
        }
        if (ex != null) {
            VSLOG.log(logLevel, s, ex);
        } else {
            VSLOG.log(logLevel, s);
        }
    }

    /**
     * Logs an {@link Level#INFO} message (created with {@link String#format(String, Object...)}).
     */
    public static void Log(String format, Object... args) {
        InternalLog(null, null, null, null, Level.INFO, format, args);
    }

    /**
     * Logs an {@link Level#SEVERE} message (created with {@link String#format(String, Object...)}) and a {@link Throwable} with stacktrace.
     */
    public static void Log(Throwable ex, String format, Object... args) {
        InternalLog(null, null, null, ex, Level.SEVERE, format, args);
    }

    /**
     * Logs a {@link Level#INFO} message (created with {@link String#format(String, Object...)}).
     */
    public static void Log(TaskListener listener, String format, Object... args) {
        InternalLog(null, null, listener, null, Level.INFO, format, args);
    }

    /**
     * Logs a {@link Level#SEVERE} message (created with {@link String#format(String, Object...)}) and a {@link Throwable} (with stacktrace).
     */
    public static void Log(TaskListener listener, Throwable ex, String format, Object... args) {
        InternalLog(null, null, listener, ex, Level.SEVERE, format, args);
    }

    /**
     * Logs a {@link Level#INFO} message (created with {@link String#format(String, Object...)}), prefixed by the {@link Slave} name.
     */
    public static void Log(Slave slave, String format, Object... args) {
        InternalLog(slave, null, null, null, Level.INFO, format, args);
    }

    /**
     * Logs a {@link Level#SEVERE} message (created with {@link String#format(String, Object...)}) and a {@link Throwable} (with stacktrace), prefixed by the {@link Slave} name.
     */
    public static void Log(Slave slave, Throwable ex, String format, Object... args) {
        InternalLog(slave, null, null, ex, Level.SEVERE, format, args);
    }

    /**
     * Logs a {@link Level#INFO} message (created with {@link String#format(String, Object...)}), prefixed by the {@link Slave} name.
     */
    public static void Log(Slave slave, TaskListener listener, String format, Object... args) {
        InternalLog(slave, null, listener, null, Level.INFO, format, args);
    }

    /**
     * Logs a {@link Level#SEVERE} message (created with {@link String#format(String, Object...)}) and a {@link Throwable} (with stacktrace), prefixed by the {@link Slave} name.
     */
    public static void Log(Slave slave, TaskListener listener, Throwable ex, String format, Object... args) {
        InternalLog(slave, null, listener, ex, Level.SEVERE, format, args);
    }

    /**
     * Logs a {@link Level#INFO} message (created with {@link String#format(String, Object...)}), prefixed by the {@link SlaveComputer} name.
     */
    public static void Log(SlaveComputer slave, TaskListener listener, String format, Object... args) {
        InternalLog(null, slave, listener, null, Level.INFO, format, args);
    }

    /**
     * Logs a {@link Level#SEVERE} message (created with {@link String#format(String, Object...)}) and a {@link Throwable} (with stacktrace), prefixed by the {@link SlaveComputer} name.
     */
    public static void Log(SlaveComputer slave, TaskListener listener, Throwable ex, String format, Object... args) {
        InternalLog(null, slave, listener, ex, Level.SEVERE, format, args);
    }

    @DataBoundConstructor
    public ScriptedCloud(String name, String instanceCapStr, String scHost, String startScript, String stopScript) {
        super(name, instanceCapStr);
        Log("STARTTING SCRIPTED CLOUD");
        this.scHost = scHost;

        this.startScript = startScript;
        this.stopScript = stopScript;

    }

    public String getScHost() {
        return scHost;
    }

    public String getStartScript() {
        return startScript;
    }

    public void setStartScript(String startScript) {
        this.startScript = startScript;
    }

    public String getStopScript() {
        return stopScript;
    }

    public void setStopScript(String stopScript) {
        this.stopScript = stopScript;
    }

    @Override
    public boolean canProvision(Label label) {
        return false;
    }

    @Override
    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ScriptedCloud");
        sb.append(" {name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }


    public synchronized Boolean canMarkVMOnline(String slaveName, String vmName) {
        return Boolean.TRUE;
    }

    public synchronized Boolean markVMOnline(String slaveName, String vmName) {
        return Boolean.TRUE;
    }

    public synchronized void markVMOffline(String slaveName, String vmName) {
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public static List<ScriptedCloud> findAllScriptedClouds(String jobName) {
        List<ScriptedCloud> scriptedClouds = new ArrayList<ScriptedCloud>();

        String[] path = new String[0];
        Folder prevFolder = null;

        if (Stapler.getCurrentRequest() != null) {
            path = Stapler.getCurrentRequest().getRequestURI().split("/");
        } else if (jobName != null) {
            path = jobName.split("/");
        }

        for (String item : path) {

            if (item.equals("job") || item.equals("jenkins"))
                continue;

            TopLevelItem topLevelItem = null;
            if (prevFolder == null) {
                topLevelItem = Jenkins.getActiveInstance().getItem(item);
            } else {
                Collection<TopLevelItem> items = prevFolder.getItems();
                for (TopLevelItem levelItem : items) {
                    if (levelItem.getName().endsWith(item)) {
                        topLevelItem = levelItem;
                    }
                }
            }
            if (topLevelItem != null && topLevelItem instanceof Folder) {
                extractClouds(scriptedClouds, (Folder) topLevelItem);
            }
        }

        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ScriptedCloud) {
                scriptedClouds.add((ScriptedCloud) cloud);
            }
        }
        return scriptedClouds;
    }

    private static void extractClouds(List<ScriptedCloud> vSphereClouds, Folder folder) {
        DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties = folder.getProperties();
        for (AbstractFolderProperty<?> property : properties) {
            if (property instanceof FolderScriptedCloudProperty) {
                vSphereClouds.addAll(((FolderScriptedCloudProperty) property).getScriptedClouds());
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Cloud> {

        public final ConcurrentMap<String, ScriptedCloud> hypervisors = new ConcurrentHashMap<String, ScriptedCloud>();
        private String scHost;

        @Override
        public String getDisplayName() {
            return "scripted Cloud";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject o)
                throws FormException {
            scHost = o.getString("scHost");
            save();
            return super.configure(req, o);
        }

        /**
         * For UI.
         */
        public FormValidation doTestConnection(
                @QueryParameter String name) {
            try {

                return FormValidation.ok("Connected successfully");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    private CommandInterpreter getCommandInterpreter(String script) {
        if (Functions.isWindows()) {
            ScriptedCloud.Log("its windows..");
            return new BatchFile(script);
        } else {
            ScriptedCloud.Log("its unix..");
            return new Shell(script);
        }
    }


    public void startSlave(String name, List<EnvironmentVariable> environmentVariables, TaskListener taskListener) throws IOException, InterruptedException {
        executeCommand(startScript, name, environmentVariables, taskListener);

    }

    public void stopSlave(String name, List<EnvironmentVariable> environmentVariables, TaskListener taskListener) throws IOException, InterruptedException {
        executeCommand(stopScript, name, environmentVariables, taskListener);
    }

    private void executeCommand(String command, String name, List<EnvironmentVariable> environmentVariables,
                                TaskListener taskListener) throws IOException, InterruptedException {
        //        //get all environment variables
        Map<String, String> envVars = new HashMap<>();
        environmentVariables.forEach(ev -> envVars.put(ev.getKey(), ev.getValue()));

        envVars.put("SLAVE_NAME", name);


        //launch the script
        FilePath root = new FilePath(new File("/"));

        CommandInterpreter shell = getCommandInterpreter(command);
        FilePath script = shell.createScriptFile(root);

        int result = root.createLauncher(taskListener).launch()
                .cmds(shell.buildCommandLine(script))
                .envs(envVars)
                .stdout(taskListener)
                .pwd(root).join();

        if(result != 0){
            throw new AbortException("The script failed with exit code "+ result);
        }

    }
}
