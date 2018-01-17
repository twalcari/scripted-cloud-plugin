/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import com.google.common.base.Throwables;
import hudson.Functions;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

/**
 * @author Admin
 */
public class ScriptedCloudLauncher extends DelegatingComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(ScriptedCloudLauncher.class.getName());

    private boolean launched;


    private final String cloudName;
    private final String vmName;
    private int LimitedTestRunCount;
    private Boolean disconnectCustomAction = Boolean.FALSE;
    private Integer secToWaitOnline = 10 * 60;
    private ScriptedCloudSlave cloudSlave;

    private Boolean enableLaunch = Boolean.FALSE;


    @DataBoundConstructor
    public ScriptedCloudLauncher(ComputerLauncher delegate, String cloudName, String vmName, String LimitedTestRunCount) {
        super(delegate);
        this.cloudName = cloudName;
        this.vmName = vmName;
        //this.isStarting = Boolean.FALSE;
        this.LimitedTestRunCount = Util.tryParseNumber(LimitedTestRunCount, 0).intValue();
    }

    public ScriptedCloud findOurVsInstance(String cloudName) throws RuntimeException {
        if (cloudName != "") {
            ScriptedCloud vs;
            for (Cloud cloud : Jenkins.getInstance().clouds) {
                if (cloud instanceof ScriptedCloud && cloud.getSearchName().equals(cloudName)) {
                    vs = (ScriptedCloud) cloud;
                    return vs;
                }
            }
        }
        ScriptedCloud.Log("Could not find our scripted Cloud instance!");
        throw new RuntimeException("Could not find our scripted Cloud instance!");
    }


    public void enableLaunch() {
        enableLaunch = Boolean.TRUE;
    }

    public void disableLaunch() {
        enableLaunch = Boolean.FALSE;
    }

    private CommandInterpreter getCommandInterpreter(String script) {
        if (Functions.isWindows()) {
            ScriptedCloud.Log("its windows..");
            return new BatchFile(script);
        }
        ScriptedCloud.Log("its unix..");
        return new Shell(script);
    }


    @Override
    public boolean isLaunchSupported() {
        return true;
    }


    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener)
            throws IOException, InterruptedException {

        if (!(slaveComputer instanceof ScriptedCloudSlaveComputer)) {
            throw new IllegalArgumentException("This launcher can only be used with SlaveComputer");
        }

        ScriptedCloudSlaveComputer computer = (ScriptedCloudSlaveComputer) slaveComputer;
        computer.setAcceptingTasks(false);

        ScriptedCloudSlave slave = computer.getNode();

        if (slave == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }

        if (launched) {
            LOGGER.log(INFO, "Agent has already been launched, activating: {}", slave.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        try {
            ScriptedCloud cloud = slave.getScriptedCloud();
            cloud.startSlave(slave.getNodeName(), slave.getEnvVars(), listener);

            if (getLauncher().isLaunchSupported()) {
                getLauncher().launch(slaveComputer, listener);
            } else {
                for (int i = 0; i <= secToWaitOnline; i++) {
                    Thread.sleep(1000);
                    LOGGER.log(INFO, String.format("Awaiting the slave %s to come online", slave.getNodeName()));
                    if (slave.getComputer() == null) {
                        throw new IllegalStateException("Node was deleted, computer is null");
                    }
                    if (slave.getComputer().isOnline()) {
                        break;
                    }
                }
                if (!slaveComputer.isOnline()) {
                    LOGGER.log(Level.WARNING, "Slave did not come online in allowed time");
                    throw new IllegalStateException("Slave did not come online in allowed time");
                }
            }


            computer.setAcceptingTasks(true);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, String.format("Error in provisioning; agent=%s", slave), ex);
            LOGGER.log(Level.FINER, "Removing Jenkins node: {0}", slave.getNodeName());
            try {
                slave.terminate();
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            }
            throw Throwables.propagate(ex);
        }

        launched = true;

        try {
            // We need to persist the "launched" setting...
            slave.save();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
        }
    }


    public Integer getLimitedTestRunCount() {
        return LimitedTestRunCount;
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        // Don't allow creation of launcher from UI
        throw new UnsupportedOperationException();
    }

    public String getVmName() {
        return vmName;
    }
}
