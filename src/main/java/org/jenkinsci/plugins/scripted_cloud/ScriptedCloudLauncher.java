/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import com.google.common.base.Throwables;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * @author Admin
 */
public class ScriptedCloudLauncher extends DelegatingComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(ScriptedCloudLauncher.class.getName());

    private boolean launched;
    private final int secToWaitOnline;

    @DataBoundConstructor
    public ScriptedCloudLauncher(ComputerLauncher delegate, int secToWaitOnline) {
        super(delegate);
        this.secToWaitOnline = secToWaitOnline;
        LOGGER.log(Level.INFO, String.format("Created ScriptedCloudLauncher for %s", delegate.toString()));
    }

    @Override
    public boolean isLaunchSupported() {
        return true;
    }


    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener) {

        LOGGER.log(INFO, String.format("Launching %s", slaveComputer.getDisplayName()));

        if (!(slaveComputer instanceof ScriptedCloudSlaveComputer)) {
            throw new IllegalArgumentException("This launcher can only be used with SlaveComputer");
        }

        ScriptedCloudSlaveComputer computer = (ScriptedCloudSlaveComputer) slaveComputer;
        computer.setAcceptingTasks(false);

        LOGGER.log(INFO, String.format("Disabled accepting tasks for now on %s", slaveComputer.getDisplayName()));

        ScriptedCloudSlave slave = computer.getNode();

        if (slave == null) {
            LOGGER.log(WARNING, String.format("Node has been removed for %s, cannot launch", slaveComputer.getDisplayName()));
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }

        if (launched) {
            LOGGER.log(INFO, "Agent has already been launched, activating: %s", computer.getDisplayName());
            computer.setAcceptingTasks(true);
            return;
        }

        try {

            LOGGER.log(INFO, String.format("Requesting cloud start %s", computer.getDisplayName()));

            ScriptedCloud cloud = slave.getScriptedCloud();
            cloud.startSlave(slave.getNodeName(), slave.getEnvVars(), listener);

            LOGGER.log(INFO, String.format("Requested cloud %s to start %s", cloud.getDisplayName(), computer.getDisplayName()));

            if (getLauncher().isLaunchSupported()) {
                LOGGER.log(INFO, String.format("Launcher %s can launch itself. Not doing anything anymore for %s", getLauncher().getClass().getSimpleName(), computer.getDisplayName()));

                getLauncher().launch(slaveComputer, listener);
            } else {
                LOGGER.log(INFO, String.format("Launcher %s cannot launch itself. Waiting for %s", getLauncher().getClass().getSimpleName(), computer.getDisplayName()));

                for (int i = 0; i <= secToWaitOnline; i++) {
                    //log every 10 seconds
                    if (i % 10 == 0)
                        LOGGER.log(INFO, String.format("Awaiting the slave %s to come online", slave.getNodeName()));

                    Thread.sleep(1000);

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
            LOGGER.log(INFO, "Agent has been launched, activating: %s", computer.getDisplayName());

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

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        // Don't allow creation of launcher from UI
        throw new UnsupportedOperationException();
    }
}
