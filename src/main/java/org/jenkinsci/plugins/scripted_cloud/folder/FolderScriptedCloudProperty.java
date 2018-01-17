package org.jenkinsci.plugins.scripted_cloud.folder;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.scripted_cloud.ScriptedCloud;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

public class FolderScriptedCloudProperty  extends AbstractFolderProperty<AbstractFolder<?>>{

    public List<ScriptedCloud> getScriptedClouds() {
        return clouds;
    }

    private List<ScriptedCloud> clouds = null;

    public List<ScriptedCloud> getClouds() {
        return clouds;
    }

    public void setClouds(List<ScriptedCloud> clouds) {
        this.clouds = clouds;
    }

    @DataBoundConstructor
    public FolderScriptedCloudProperty(List<ScriptedCloud> clouds) {
        this.clouds = clouds;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("FolderScriptedCloudProperty{");
        sb.append("clouds=").append(clouds);
        sb.append('}');
        return sb.toString();
    }

    @Extension()
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "scripted Cloud";
        }

        public FormValidation doCheckMaxOnlineSlaves(@QueryParameter String maxOnlineSlaves) {
            return FormValidation.validateNonNegativeInteger(maxOnlineSlaves);
        }

        public FormValidation doCheckInstanceCap(@QueryParameter String instanceCap) {
            return FormValidation.validateNonNegativeInteger(instanceCap);
        }

    }
}
