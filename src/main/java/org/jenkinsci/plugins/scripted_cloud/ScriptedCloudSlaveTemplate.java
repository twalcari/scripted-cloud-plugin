package org.jenkinsci.plugins.scripted_cloud;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import org.apache.commons.lang.RandomStringUtils;
import org.jenkinsci.plugins.scripted_cloud.model.EnvironmentVariable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ScriptedCloudSlaveTemplate extends AbstractDescribableImpl<ScriptedCloudSlaveTemplate> {

    private final String slaveNamePrefix;
    private final String description;
    private int instanceCap;

    private final List<EnvironmentVariable> envVars = new ArrayList<>();
    private final String secToWaitOnline;
    private final Boolean reusable;
    private final String labels;
    private final String remoteFS;
    private final Set<LabelAtom> labelSet;
    private final String numExecutors;

    @DataBoundConstructor
    public ScriptedCloudSlaveTemplate(String slaveNamePrefix, String description, String instanceCapStr,
                                      List<EnvironmentVariable> envVars,
                                      String secToWaitOnline, Boolean reusable, String labels,
                                      String remoteFS,
                                      String numExecutors) {
        this.slaveNamePrefix = slaveNamePrefix;
        this.description = description;
        this.setInstanceCapStr(instanceCapStr);
        if (envVars != null)
            this.envVars.addAll(envVars);
        this.secToWaitOnline = secToWaitOnline;
        this.reusable = reusable;
        this.labels = labels;
        this.labelSet = Label.parse(labels);
        this.remoteFS = remoteFS;
        this.numExecutors = numExecutors;
    }

    public String getSlaveNamePrefix() {
        return slaveNamePrefix;
    }

    public String getDescription() {
        return description;
    }

    protected void setInstanceCapStr(String value) {
        if (value == null || value.equals(""))
            this.instanceCap = Integer.MAX_VALUE;
        else
            this.instanceCap = Integer.parseInt(value);
    }

    /**
     * Gets the instance cap as string. Used primarily for form binding.
     */
    public String getInstanceCapStr() {
        if (instanceCap == Integer.MAX_VALUE)
            return "";
        else
            return String.valueOf(instanceCap);
    }


    public int getInstanceCap() {
        return instanceCap;
    }

    public List<EnvironmentVariable> getEnvVars() {
        return envVars;
    }

    public String createSlaveName() {
        return getSlaveNamePrefix() + "-" + RandomStringUtils.random(6, "bcdfghjklmnpqrstvwxz0123456789");
    }

    public String getLabels() {
        return labels;
    }

    public Set<LabelAtom> getLabelSet() {
        return labelSet;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public String getSecToWaitOnline() {
        return secToWaitOnline;
    }

    public Boolean getReusable() {
        return reusable;
    }

    public String getNumExecutors() {
        return numExecutors;
    }

    public int getNumExecutorsInt() {
        return Util.tryParseNumber(numExecutors, 1).intValue();
    }

    private static final Pattern SLAVE_NAME_PATTERN = Pattern.compile("\\w{1,10}");


    @Extension
    public static final class DescriptorImpl extends Descriptor<ScriptedCloudSlaveTemplate> {
        @Override
        public String getDisplayName() {
            return "Scripted Cloud Template";
        }

        public FormValidation doCheckSlaveNamePrefix(@QueryParameter String slaveNamePrefix) {
            if (slaveNamePrefix.length() == 0)
                return FormValidation.error("This is a required field");
            if (slaveNamePrefix.length() > 10)
                return FormValidation.error("Maximum length is 10 characters");

            if (!SLAVE_NAME_PATTERN.matcher(slaveNamePrefix).matches()) {
                return FormValidation.error("The slave name must consist out of characters [a-zA-Z_0-9]");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckInstanceCapStr(@QueryParameter String instanceCapStr) {
            if (instanceCapStr.isEmpty())
                return FormValidation.ok();
            else
                return FormValidation.validateNonNegativeInteger(instanceCapStr);
        }

        public FormValidation doCheckSecToWaitOnline(@QueryParameter String secToWaitOnline) {
            return FormValidation.validateNonNegativeInteger(secToWaitOnline);
        }

        public FormValidation doCheckNumExecutors(@QueryParameter String numExecutors) {
            return FormValidation.validateNonNegativeInteger(numExecutors);
        }

        public FormValidation doCheckRemoteFS(@QueryParameter String remoteFS){
            return FormValidation.validateRequired(remoteFS);
        }
    }

}
