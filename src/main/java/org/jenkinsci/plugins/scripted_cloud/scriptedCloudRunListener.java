/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 *
 * @author Admin
 */
@Extension
public final class scriptedCloudRunListener extends RunListener<Run> {
    
    private List<Run> LimitedRuns = Collections.synchronizedList(new ArrayList<Run>());

    public scriptedCloudRunListener() {
    }
        
    @Override
    public void onStarted(Run r, TaskListener listener) {
        super.onStarted(r, listener);
        if (r != null) {
            Executor exec = r.getExecutor();
            if (exec != null) {
                Computer owner = exec.getOwner();
                if (owner != null) {
                    Node node = owner.getNode();
                    if ((node != null) && (node instanceof ScriptedCloudSlave)) {
                        LimitedRuns.add(r);
                        ScriptedCloudSlave s = (ScriptedCloudSlave)node;
                        s.StartLimitedTestRun(r, listener);
                    }
                }
            }
        }
    }

    @Override
    public void onFinalized(Run r) {
        super.onFinalized(r);    	
        if (LimitedRuns.contains(r)) {
            LimitedRuns.remove(r);
            Node node = r.getExecutor().getOwner().getNode();
            if (node instanceof ScriptedCloudSlave) {
                ScriptedCloudSlave s = (ScriptedCloudSlave)node;
                s.EndLimitedTestRun(r);
            }                    
        }
    }   
}


