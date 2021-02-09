/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import hudson.*;
import hudson.model.*;
import hudson.slaves.*;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scripted_cloud.model.EnvironmentVariable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * @author Admin
 */
public class ScriptedCloud extends AbstractCloudImpl {

    private static final Logger LOGGER = Logger.getLogger(ScriptedCloud.class.getName());
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private String startScript;
    private String stopScript;
    private final List<ScriptedCloudSlaveTemplate> templates;

    @DataBoundConstructor
    public ScriptedCloud(String name, String instanceCapStr, String startScript, String stopScript,
                         List<ScriptedCloudSlaveTemplate> templates) {
        super(name, instanceCapStr);

        this.startScript = startScript;
        this.stopScript = stopScript;

        this.templates = templates;
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

    public List<ScriptedCloudSlaveTemplate> getTemplates() {
        return templates;
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {
            LOGGER.log(Level.INFO, String.format("Excess workload after pending instances for label '%s': %d", label, excessWorkload));

            List<NodeProvisioner.PlannedNode> nodes = new ArrayList<>();
            final List<ScriptedCloudSlaveTemplate> templates = getMatchingTemplates(label);

            for (ScriptedCloudSlaveTemplate t : templates) {
                LOGGER.log(Level.INFO, String.format("Template: %s", t.getDescription()));

                boolean canProvision = true;

                while(canProvision && excessWorkload > 0){
                    if (canProvisionSlaveTemplate(t, label)) {
                        final String slaveName = t.createSlaveName();
                        nodes.add(new NodeProvisioner.PlannedNode(
                                slaveName,
                                executorService.submit(
                                        new ProvisioningCallback(slaveName, t)
                                ), t.getNumExecutorsInt()));

                        excessWorkload -= t.getNumExecutorsInt();
                    } else {
                        canProvision = false;
                    }
                }


                if (nodes.size() > 0) {
                    // Already found a matching template
                    return nodes;
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to schedule new Jenkins slave on scripted cloud, message: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    private boolean canProvisionSlaveTemplate(@Nonnull ScriptedCloudSlaveTemplate template, Label label) {

        boolean globalInstancesCountOk = getInstanceCap() == 0 || getInstancesCount() < getInstanceCap();
        boolean templateInstancesOk =  template.getInstanceCap() == 0 || getTemplateInstancesCount(template) < template.getInstanceCap();

        return globalInstancesCountOk && templateInstancesOk;
    }

    private int getTemplateInstancesCount(@Nonnull ScriptedCloudSlaveTemplate template) {
        Computer[] computers = Jenkins.get().getComputers();

        int count = 0;
        for (Computer computer : computers){
            Node node = computer.getNode();

            if(node instanceof ScriptedCloudSlave){
                if(getTemplateFromVm(node.getNodeName()) == template)
                    count++;
            }
        }
        return count;
    }

    private int getInstancesCount() {
        Computer[] computers = Jenkins.get().getComputers();

        int count = 0;
        for (Computer computer : computers){
            Node node = computer.getNode();

            if(node instanceof ScriptedCloudSlave){
                if(((ScriptedCloudSlave) node).getScriptedCloud() == this)
                    count++;
            }
        }
        return count;
    }

    @Nullable
    private ScriptedCloudSlaveTemplate getTemplateFromVm(@Nonnull String vmName){
        for (ScriptedCloudSlaveTemplate template : templates) {
            if(vmName.startsWith(template.getSlaveNamePrefix()))
                return template;
        }
        return null;
    }

    private class ProvisioningCallback implements Callable<Node> {

        private final String slaveName;
        private final ScriptedCloudSlaveTemplate template;

        private ProvisioningCallback(String slaveName, ScriptedCloudSlaveTemplate template) {
            this.slaveName = slaveName;
            this.template = template;
        }

        @Override
        public Node call() throws Exception {

            final ScriptedCloudSlave slave =
                    new ScriptedCloudSlave(slaveName, "Instance " + slaveName + " of template " + template.getDescription(),
                            template.getRemoteFS(),
                            template.getNumExecutors(),
                            Node.Mode.NORMAL,
                            template.getLabels(),
                            new JNLPLauncher(),
                            RetentionStrategy.NOOP,
                            Collections.emptyList(),
                            name,
                            template.getEnvVars(),
                            template.getSecToWaitOnline(),
                            template.getReusable());

            Jenkins.get().addNode(slave);


            LOGGER.log(Level.INFO, String.format("Asking ScriptedCloud %s to schedule new Jenkins slave %s", name, slaveName));

            Computer computer = slave.toComputer();
            assert computer != null;
            computer.waitUntilOnline();

            return slave;
        }
    }


    // Find the correct template for job
    @Nonnull
    private List<ScriptedCloudSlaveTemplate> getMatchingTemplates(Label label) {
        return templates.stream()
                .filter(template -> {
                    if (label != null) {
                        return label.matches(template.getLabelSet());
                    } else {
                        return template.getLabelSet().isEmpty();
                    }
                }).collect(Collectors.toList());
    }

    @Override
    public boolean canProvision(Label label) {
        return !getMatchingTemplates(label).isEmpty();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ScriptedCloud");
        sb.append(" {name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "scripted Cloud";
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            return FormValidation.validateRequired(name);
        }

        public FormValidation doCheckStartScript(@QueryParameter String startScript) {
            return FormValidation.validateRequired(startScript);
        }

        public FormValidation doCheckStopScript(@QueryParameter String stopScript) {
            return FormValidation.validateRequired(stopScript);
        }
    }


    private CommandInterpreter getCommandInterpreter(String script) {
        if (Functions.isWindows()) {
            return new BatchFile(script);
        } else {
            return new Shell(script);
        }
    }


    public void startSlave(String name, List<EnvironmentVariable> environmentVariables, TaskListener taskListener, long timeout, TimeUnit timeUnit) throws IOException, InterruptedException {
        executeCommand(startScript, name, environmentVariables, taskListener, timeout, timeUnit);

    }

    public void stopSlave(String name, List<EnvironmentVariable> environmentVariables, TaskListener taskListener, long timeout, TimeUnit timeUnit) throws IOException, InterruptedException {
        executeCommand(stopScript, name, environmentVariables, taskListener, timeout,timeUnit);
    }

    private void executeCommand(String command, String name, List<EnvironmentVariable> environmentVariables,
                                TaskListener taskListener, long timeout, TimeUnit timeUnit) throws IOException, InterruptedException {

        // get all environment variables
        Map<String, String> envVars = new HashMap<>();
        environmentVariables.forEach(ev -> envVars.put(ev.getKey(), ev.getValue()));

        envVars.put("SLAVE_NAME", name);
        envVars.put("JENKINS_URL", Jenkins.get().getRootUrl());

        // Support for Jenkins security
        if (Jenkins.get().isUseSecurity()) {
            envVars.put("JNLP_SECRET", jenkins.slaves.JnlpAgentReceiver.SLAVE_SECRET.mac(name));
        }


        //launch the script
        FilePath root = new FilePath(new File("/"));

        CommandInterpreter shell = getCommandInterpreter(command);
        FilePath script = shell.createScriptFile(root);

        Proc proc =  root.createLauncher(taskListener).launch()
                .cmds(shell.buildCommandLine(script))
                .envs(envVars)
                .stdout(taskListener)
                .pwd(root)
                .start();

        int result = timeout != 0 ? proc.joinWithTimeout(timeout, timeUnit, taskListener): proc.join();

        if (result != 0) {
            throw new AbortException("The script failed with exit code " + result);
        }

        LOGGER.log(Level.FINE, String.format("Script for, %s, finished successfully.", command));

    }
}
