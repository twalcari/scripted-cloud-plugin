/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;
import hudson.Util;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.slaves.Cloud;

import java.io.IOException;
import java.net.SocketException;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import hudson.Launcher.LocalLauncher;
import hudson.tasks.Shell;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import java.io.IOException;
import java.util.Collections;

import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;

import static hudson.model.TaskListener.NULL;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.concurrent.Future;
import hudson.slaves.JNLPLauncher;

/**
 *
 * @author Admin
 */
public class scriptedCloudLauncher extends ComputerLauncher {

    

    private ComputerLauncher delegate;
    //private Boolean isStarting = Boolean.FALSE;
    //private Boolean isDisconnecting = Boolean.FALSE;
    private int LimitedTestRunCount = 0;
    private Boolean disconnectCustomAction = Boolean.FALSE;
    private Integer secToWaitOnline = 10*60;
    private scriptedCloudSlave cloudSlave;
    
    private Boolean enableLaunch = Boolean.FALSE;


    @DataBoundConstructor
    public scriptedCloudLauncher(ComputerLauncher delegate) {
        super();
        this.delegate = delegate;
        //this.isStarting = Boolean.FALSE;
        this.LimitedTestRunCount = 0; //Util.tryParseNumber(LimitedTestRunCount, 0).intValue();
    }

     public scriptedCloud findOurVsInstance(String vsDescription) throws RuntimeException {
        if (vsDescription != "") {
            scriptedCloud vs = null;
            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof scriptedCloud && ((scriptedCloud) cloud).getVsDescription().equals(vsDescription)) {
                    vs = (scriptedCloud) cloud;
                    return vs;
                }
            }
        }
        scriptedCloud.Log("Could not find our scripted Cloud instance!");
        throw new RuntimeException("Could not find our scripted Cloud instance!");
    }    

    public void enableLaunch() {
    	enableLaunch = Boolean.TRUE;
    }
    public void disableLaunch() {
    	enableLaunch = Boolean.FALSE;
    }
   
    private CommandInterpreter getCommandInterpreter(String script) {
        if (Hudson.getInstance().isWindows()) {
        	scriptedCloud.Log("its windows..");
            return new BatchFile(script);
        }
        scriptedCloud.Log("its unix..");
        return new Shell(script);
    }

    /*
    @Override
    public boolean isLaunchSupported() {
        return delegate.isLaunchSupported();
    }
    */
    
    @Override
    public boolean isLaunchSupported() {
    	return true;
    	/*
        if (this.overrideLaunchSupported == null) {
            return delegate.isLaunchSupported();
        } else {            
            return overrideLaunchSupported;
        }
        */
    }

   public void run(scriptedCloudSlaveComputer s , TaskListener listener)  
   throws IOException, InterruptedException  {
         s.setStarting();
	scriptedCloud vs = findOurVsInstance(s.getVsDescription());
    		File f = new File(vs.getStartScriptFile());
    		CommandInterpreter shell = getCommandInterpreter(vs.getStartScriptFile());
    		scriptedCloud.Log(s, listener,"script file:" + vs.getStartScriptFile());
    		//scriptedCloud.Log("file.getpath:" + f.getParent());
    		FilePath root = new FilePath(new File("/"));
    		FilePath script = shell.createScriptFile(root);
    		int r = 0;
    		HashMap envMap = new HashMap();    		
    		s.fillEnv(envMap);
    		envMap.put("SCVM_ACTION","start");
    		scriptedCloud.Log(s, listener, "start env:" + envMap);
    		shell.buildCommandLine(script);
    		r = root.createLauncher(listener).launch().cmds(shell.buildCommandLine(script))
    		.envs(envMap)
    		.stdout(listener).pwd(root).join();
    		if (r!=0) {
    			s.revertState();
    			throw new AbortException("The script failed:" + r + ", " + vs.getStartScriptFile());
    		}
    		scriptedCloud.Log("script done:" + vs.getStartScriptFile());
          
   }

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener)
    throws IOException, InterruptedException {
		
    	scriptedCloudSlaveComputer s = (scriptedCloudSlaveComputer)slaveComputer;
		scriptedCloud.Log(s, listener, "Event - Launch. slave:" + s);
		if (s.isTemporarilyOffline()) {
			scriptedCloud.Log(s, listener, "Not launching VM because it's not accepting tasks; temporarily offline");
			return;
		}

		// Slaves that take a while to start up make get multiple launch
		// requests from Jenkins.  
		if (s.stopped() || s.initialized()) {
			scriptedCloud.Log(slaveComputer, listener, "its ready to launch");
		}
    	try {
    		this.run(s , listener);
                   
		  if (delegate.isLaunchSupported()) 
	                 	delegate.launch(slaveComputer, listener);
	                else {
			scriptedCloud.Log(s, listener,"delegate launch not supported");                        
		                for (int i = 0; i <= secToWaitOnline; i++) {
		                    Thread.sleep(3000);
			    scriptedCloud.Log(s, listener,"Awaiting the slave to come online");  
			    if (s != null)
		                     if (s.isOnline()) {
		                        break;
	                	    }
	                }
	                if (!slaveComputer.isOnline()) {
	                	scriptedCloud.Log(s, listener, "Slave did not come online in allowed time");
	                	s.revertState();
	                    throw new IOException("Slave did not come online in allowed time");
	                }
	            }
    		scriptedCloud.Log(slaveComputer, listener, "launch done");
    		s.setStarted();
    		return;
    	}
    	catch (Exception e) {
    		s.revertState();
    		scriptedCloud.Log(slaveComputer, listener, "!!! Exception !!!");
    		scriptedCloud.Log(slaveComputer, listener, "launch error:"+ e);
    		throw new RuntimeException(e);
    	}
    	
    }


    public void stopSlave(scriptedCloudSlaveComputer slaveComputer, boolean forced) {
    	scriptedCloud.Log("stopSlave processing called: forced=" + forced);
/*    	if (forced == false && slaveComputer.doNothing()) {
    		scriptedCloud.Log("Do nothing for this slave");
    		return;
    	}*/
    	disconnectCustomAction = Boolean.TRUE;
    	slaveComputer.disconnect();
    	scriptedCloud.Log("disconnected slave.");
    }

    @Override
 	public void beforeDisconnect(SlaveComputer slaveComputer, TaskListener taskListener) {
    	scriptedCloud.Log(slaveComputer, taskListener, "Event - beforeDisconnect. Slave : " + slaveComputer);
    	delegate.beforeDisconnect(slaveComputer, taskListener);
    	//super.beforeDisconnect(slaveComputer, taskListener);
    }

   public void stop(scriptedCloudSlaveComputer slaveComputer , TaskListener taskListener ) 
   throws IOException, InterruptedException  {

          slaveComputer.setStopping();    

        	delegate.afterDisconnect(slaveComputer, taskListener);        	
        	scriptedCloud vs = findOurVsInstance(slaveComputer.getVsDescription());
            HashMap envMap = new HashMap();
            slaveComputer.fillEnv(envMap);
	    	envMap.put("SCVM_ACTION","stop");
    		scriptedCloud.Log(slaveComputer, taskListener, "Calling stop script with env:" + envMap);
        	String scriptToRun = vs.getStopScriptFile();
        	//scriptedCloud.Log("runScript:" + scriptToRun);        	
            //scriptedCloud.Log(slaveComputer, taskListener, "running script");
            File f = new File(scriptToRun);
        	CommandInterpreter shell = getCommandInterpreter(scriptToRun);
            //scriptedCloud.Log("sript file:" + scriptToRun);
            //scriptedCloud.Log("file.getpath:" + f.getParent());            
            FilePath root = new FilePath(new File("/"));
            FilePath script = shell.createScriptFile(root);
        	//scriptedCloud.Log("root path:" + root + ", script:" + script);
            int r = root.createLauncher(taskListener).launch().cmds(shell.buildCommandLine(script))
                    .envs(envMap /*Collections.singletonMap("LABEL","s")*/)
                    .stdout(taskListener).pwd(root).join();
            if (r!=0) {
                slaveComputer.revertState();
                throw new AbortException("The script failed:" + r);
            }
            slaveComputer.setStopped();
       
            scriptedCloud.Log(slaveComputer, taskListener, "Event - afterdisconnect done");

   }

    @Override
    public synchronized void afterDisconnect(SlaveComputer s,
            TaskListener taskListener) {    	
    	scriptedCloudSlaveComputer slaveComputer = (scriptedCloudSlaveComputer)s;
    	scriptedCloud.Log(slaveComputer, taskListener, "Event - afterDisconnect. Slave : " + slaveComputer);
    	delegate.afterDisconnect(slaveComputer, taskListener);
    	
    	//wait for sometime to get slave disconnected
    	try {
	        for (int i = 0; i <= 5; i++) {
	        	scriptedCloud.Log(slaveComputer, taskListener, 
	        			"waiting for delegate.afterDisconnect completion");
	            Thread.sleep(1000);            
	            if (!s.isOnline()) {
	                break;
	            }
	        }
    	}
    	catch (java.lang.InterruptedException e) {
    		scriptedCloud.Log(slaveComputer, taskListener, "delegate.afterDisconnect interrupted");
		}
    	if (slaveComputer.doNothing()) {
    		scriptedCloud.Log(slaveComputer, taskListener, "Do nothing for this slave");
    		return;
    	}
	if (delegate instanceof JNLPLauncher == false) 
	{
    		if (slaveComputer.starting() || slaveComputer.stopping() || slaveComputer.stopped()) {
    			scriptedCloud.Log(slaveComputer, taskListener, "Slave is not in stoppable state");
				//super.afterDisconnect(s, taskListener);
	    		return;    		
    	}
	}
    	//if (disconnectCustomAction == Boolean.FALSE) {
    	//	scriptedCloud.Log("Not called from stopslave .. skipping");
    	//	delegate.afterDisconnect(slaveComputer, taskListener);
    	//	return;
    	//}
    	disconnectCustomAction = Boolean.FALSE;
    	/*if (slaveComputer.doNothing()) {
    		scriptedCloud.Log("Action 'nothing' set .. skipping shutdown");
    		//delegate.afterDisconnect(slaveComputer, taskListener);
    		return;
    	}*/

        
        if (slaveComputer.isTemporarilyOffline()) {
        	scriptedCloud.Log(slaveComputer, taskListener, "Not disconnecting VM because it's not accepting tasks");
        	//super.afterDisconnect(s, taskListener);
           return;
        }
            
        try {
	stop(slaveComputer, taskListener);
        	            return;
            //**********************************
        } catch (Throwable t) {
        	slaveComputer.revertState();
        	scriptedCloud.Log(slaveComputer, taskListener, "Got an exception");
        	scriptedCloud.Log(slaveComputer, taskListener, t.toString());
        	scriptedCloud.Log(slaveComputer, taskListener, "Printed exception");
            taskListener.fatalError(t.getMessage(), t);
        }
        slaveComputer.revertState();
    }
   	
    public ComputerLauncher getDelegate() {
        return delegate;
    }


    public Integer getLimitedTestRunCount() {
        return LimitedTestRunCount;
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        // Don't allow creation of launcher from UI
        throw new UnsupportedOperationException();
    }
}
