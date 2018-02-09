/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import com.google.common.base.Throwables;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor.FormException;
import hudson.model.TaskListener;
import hudson.slaves.*;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.RandomStringUtils;
import org.jenkinsci.plugins.scripted_cloud.model.EnvironmentVariable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Admin
 */
public final class ScriptedCloudSlave extends AbstractCloudSlave implements EphemeralNode {

    private static final Logger LOGGER = Logger.getLogger(ScriptedCloudSlave.class.getName());

    private static final String DEFAULT_AGENT_PREFIX = "jenkins-agent";

    private String cloudName;

    private final List<EnvironmentVariable> envVars = new ArrayList<>();

    private Boolean reusable;


    @DataBoundConstructor
    public ScriptedCloudSlave(String name, String nodeDescription,
                              String remoteFS, String numExecutors, Mode mode,
                              String labelString, ComputerLauncher delegateLauncher,
                              RetentionStrategy retentionStrategy,
                              List<? extends NodeProperty<?>> nodeProperties,
                              String cloudName, List<EnvironmentVariable> envVars,
                              String secToWaitOnline, Boolean reusable) throws FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
                new ScriptedCloudLauncher(delegateLauncher, Util.tryParseNumber(secToWaitOnline, 10 * 60).intValue())
                , retentionStrategy, nodeProperties);

        this.cloudName = cloudName;
        this.reusable = reusable;
        this.envVars.addAll(envVars);
    }


    public List<EnvironmentVariable> getEnvVars() {
        return envVars;
    }

    static String getSlaveName() {
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        return String.format("%s-%s", DEFAULT_AGENT_PREFIX, randString);
    }


    public String getCloudName() {
        return cloudName;
    }

    public Cloud getCloud() {
        return Jenkins.getInstance().getCloud(getCloudName());
    }

    /**
     * Returns the cloud instance which created this agent.
     *
     * @return the cloud instance which created this agent.
     * @throws IllegalStateException if the cloud doesn't exist anymore, or is not a {@link ScriptedCloud}.
     */
    @Nonnull
    public ScriptedCloud getScriptedCloud() {
        Cloud cloud = Jenkins.getInstance().getCloud(getCloudName());
        if (cloud instanceof ScriptedCloud) {
            return (ScriptedCloud) cloud;
        } else {
            throw new IllegalStateException(getClass().getName() + " can be launched only by instances of " + ScriptedCloud.class.getName());
        }
    }


    @Override
    public ScriptedCloudSlaveComputer createComputer() {
        LOGGER.log(Level.FINE, "createComputer " + name + "\n");
        return new ScriptedCloudSlaveComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating ScriptedCloud instance for slave {0}", name);

        Computer computer = toComputer();
        if (computer == null) {
            String msg = String.format("Computer for slave is null: %s", name);
            LOGGER.log(Level.SEVERE, msg);
            listener.fatalError(msg);
            return;
        }

        if (getCloudName() == null) {
            String msg = String.format("Cloud name is not set for slave, can't terminate: %s", name);
            LOGGER.log(Level.SEVERE, msg);
            listener.fatalError(msg);
            return;
        }

        try {
            Cloud cloud = getCloud();
            if (cloud == null) {
                String msg = String.format("Slave cloud no longer exists: %s", getCloudName());
                LOGGER.log(Level.WARNING, msg);
                listener.fatalError(msg);
                return;
            }
            if (!(cloud instanceof ScriptedCloud)) {
                String msg = String.format("Slave cloud is not a ScriptedCloud, something is very wrong: %s",
                        getCloudName());
                LOGGER.log(Level.SEVERE, msg);
                listener.fatalError(msg);
                return;
            }
            ScriptedCloud sc = ((ScriptedCloud) cloud);

            sc.stopSlave(name, getEnvVars(), listener);
            String msg = String.format("Terminated ScriptedCloud instance for slave %s", name);
            LOGGER.log(Level.INFO, msg);
            listener.getLogger().println(msg);
            computer.disconnect(new OfflineCause.ByCLI(msg));
            LOGGER.log(Level.INFO, "Disconnected computer {0}", name);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to terminate ScriptedCloud instance for slave " + name, e);
            Throwables.propagate(e);
        }

    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public Boolean getReusable() {
        return reusable;
    }

    /**
     * For UI.
     *
     * @return original launcher
     */
    public ComputerLauncher getDelegateLauncher() {
        return ((ScriptedCloudLauncher) getLauncher()).getLauncher();
    }

    @Override
    public ScriptedCloudSlave asNode() {
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        private Boolean forceLaunch;
        private String cloudName;
        private List<EnvironmentVariable> envVars;

        public DescriptorImpl() {
            load();
        }

        public List<EnvironmentVariable> getEnvVars() {
            return envVars;
        }

        public void setEnvVars(List<EnvironmentVariable> envVars) {
            this.envVars = envVars;
        }

        public Boolean getForceLaunch() {
            return forceLaunch;
        }

        public void setForceLaunch(Boolean name) {
            forceLaunch = name;
        }

        public String getCloudName() {
            return cloudName;
        }

        public void setCloudName(String name) {
            cloudName = name;
        }

        public String getDisplayName() {
            return "Slave virtual computer running under scripted Cloud";
        }

        @Override
        public boolean isInstantiable() {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject o)
                throws FormException {
            cloudName = o.getString("cloudName");
            forceLaunch = o.getBoolean("forceLaunch");

            LOGGER.log(Level.INFO, "Got: " + o.toString());

            save();
            return super.configure(req, o);
        }

        public List<ScriptedCloud> getScriptedClouds() {
            List<ScriptedCloud> result = new ArrayList<ScriptedCloud>();
            for (Cloud cloud : Jenkins.getInstance().clouds) {
                if (cloud instanceof ScriptedCloud) {
                    result.add((ScriptedCloud) cloud);
                }
            }
            return result;
        }

        public ScriptedCloud getSpecificScriptedCloud(String cloudName)
                throws Exception {
            for (ScriptedCloud scriptedCloud : getScriptedClouds()) {
                if (scriptedCloud.getSearchName().equals(cloudName)) {
                    return scriptedCloud;
                }
            }
            throw new Exception("The scripted Cloud doesn't exist");
        }


        public FormValidation doCheckSecToWaitOnline(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }
    }
}
