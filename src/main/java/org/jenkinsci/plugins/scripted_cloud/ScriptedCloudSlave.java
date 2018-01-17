/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import com.google.common.base.Throwables;
import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
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
import java.util.concurrent.ConcurrentHashMap;
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
    private Boolean forceLaunch;

    private Integer LimitedTestRunCount;
    private transient Integer NumberOfLimitedTestRuns = 0;


    @DataBoundConstructor
    public ScriptedCloudSlave(String name, String nodeDescription,
                              String remoteFS, String numExecutors, Mode mode,
                              String labelString, ComputerLauncher delegateLauncher,
                              RetentionStrategy retentionStrategy,
                              List<? extends NodeProperty<?>> nodeProperties,
                              String cloudName,
                              String vmName, List<EnvironmentVariable> envVars,
                              Boolean forceLaunch,
                              String LimitedTestRunCount) throws FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
                new ScriptedCloudLauncher(delegateLauncher, cloudName, vmName, LimitedTestRunCount)
                , retentionStrategy, nodeProperties);

        this.cloudName = cloudName;
        this.envVars.addAll(envVars);

        this.forceLaunch = forceLaunch;
        this.LimitedTestRunCount = Util.tryParseNumber(LimitedTestRunCount, 0).intValue();
        this.NumberOfLimitedTestRuns = 0;
    }


    public List<EnvironmentVariable> getEnvVars() {
        return envVars;
    }

    public void setLimitedTestRunCount(Integer limitedTestRunCount) {
        LimitedTestRunCount = limitedTestRunCount;
    }

    public Integer getLimitedTestRunCount() {
        return LimitedTestRunCount;
    }

    public Boolean getForceLaunch() {
        return forceLaunch;
    }

    public void setForceLaunch(Boolean name) {
        forceLaunch = name;
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
        ScriptedCloud.Log("createComputer " + name + "\n");
        return new ScriptedCloudSlaveComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating ScriptedCloud instance for slave {0}", name);

        Computer computer = toComputer();
        if (computer == null) {
            String msg = String.format("Computer for slave is null: %s", name);
            LOGGER.log(Level.SEVERE, msg);
            ScriptedCloud.Log(listener, msg);
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


    static final private ConcurrentHashMap<Run, Computer> RunToSlaveMapper = new ConcurrentHashMap<Run, Computer>();

    public boolean StartLimitedTestRun(Run r, TaskListener listener) {
        ScriptedCloud.Log("StartLimitedTestRun");

        boolean ret = false;
        boolean DoUpdates = false;

        if (LimitedTestRunCount > 0) {
            DoUpdates = true;
            if (NumberOfLimitedTestRuns < LimitedTestRunCount) {
                ret = true;
            }
        } else {
            ret = true;
        }

        Executor executor = r.getExecutor();
        if (executor != null && DoUpdates) {
            if (ret) {
                NumberOfLimitedTestRuns++;
                ScriptedCloud.Log(this, listener, "Starting limited count build: %d of %d", NumberOfLimitedTestRuns, LimitedTestRunCount);
                Computer slave = executor.getOwner();
                RunToSlaveMapper.put(r, slave);
            } else {
                ScriptedCloud.Log(this, listener, "Terminating build due to limited build count: %d of %d", NumberOfLimitedTestRuns, LimitedTestRunCount);
                executor.interrupt(Result.ABORTED);
            }
        }

        return ret;
    }


    public boolean EndLimitedTestRun(Run r) {
        boolean ret = true;

        // See if the run maps to an existing computer; remove if found.
        Computer slave = RunToSlaveMapper.get(r);
        if (slave != null) {
            RunToSlaveMapper.remove(r);
        }

        if (LimitedTestRunCount > 0) {
            if (NumberOfLimitedTestRuns >= LimitedTestRunCount) {
                ret = false;
                NumberOfLimitedTestRuns = 0;
                try {
                    if (slave != null) {
                        ScriptedCloud.Log(this, "Disconnecting the slave agent on %s due to limited build threshold", slave.getName());

                        slave.setTemporarilyOffline(true, new OfflineCause.ByCLI("vSphere Plugin marking the slave as offline due to reaching limited build threshold"));
                        slave.waitUntilOffline();
                        slave.disconnect(new OfflineCause.ByCLI("vSphere Plugin disconnecting the slave as offline due to reaching limited build threshold"));
                        slave.setTemporarilyOffline(false, new OfflineCause.ByCLI("vSphere Plugin marking the slave as online after completing post-disconnect actions."));
                    } else {
                        ScriptedCloud.Log(this, "Attempting to shutdown slave due to limited build threshold, but cannot determine slave");
                    }
                } catch (NullPointerException ex) {
                    ScriptedCloud.Log(this, ex, "NullPointerException thrown while retrieving the slave agent");
                } catch (InterruptedException ex) {
                    ScriptedCloud.Log(this, ex, "InterruptedException thrown while marking the slave as online or offline");
                }
            }
        } else {
            ret = true;
        }
        return ret;
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
    public static class ScriptedCloudComputerListener extends ComputerListener {

        @Override
        public void preLaunch(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
            /* We may be called on any slave type so check that we should
             * be in here. */
            if (!(c.getNode() instanceof ScriptedCloudSlave)) {
                return;
            }
            ScriptedCloudLauncher vsL = (ScriptedCloudLauncher) ((SlaveComputer) c).getLauncher();
            ScriptedCloud cloud = ((ScriptedCloudSlave) c.getNode()).getScriptedCloud();

            if (!cloud.markVMOnline(c.getDisplayName(), vsL.getVmName())) {
                throw new AbortException("The scripted cloud will not allow this slave to start at this time.");
            }
        }
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

        public List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
            List<Descriptor<ComputerLauncher>> result = new ArrayList<Descriptor<ComputerLauncher>>();
            for (Descriptor<ComputerLauncher> launcher : Functions.getComputerLauncherDescriptors()) {
                if (!ScriptedCloudLauncher.class.isAssignableFrom(launcher.clazz)) {
                    result.add(launcher);
                }
            }
            return result;
        }

        public List<String> getIdleOptions() {
            List<String> options = new ArrayList<String>();
            options.add("Shutdown");
            options.add("Shutdown and Revert");
            options.add("Revert and Restart");
            options.add("Revert and Reset");
            options.add("Reset");
            options.add("Nothing");
            return options;
        }

        public FormValidation doCheckLaunchDelay(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doTestConnection(@QueryParameter String cloudName,
                                               @QueryParameter String vmName,
                                               @QueryParameter String snapName) {
            try {
                ScriptedCloud vsC = getSpecificScriptedCloud(cloudName);

                //TODO: check if we can connect to the slave

                return FormValidation.ok("Virtual Machine found successfully");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
