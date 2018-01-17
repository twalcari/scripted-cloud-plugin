/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scripted_cloud;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Admin
 */
public class ScriptedCloudSlaveComputer extends AbstractCloudComputer<ScriptedCloudSlave> {


    private static final Logger LOGGER = Logger.getLogger(ScriptedCloudSlaveComputer.class.getName());

    public ScriptedCloudSlaveComputer(ScriptedCloudSlave slave) {
        super(slave);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        LOGGER.fine(" Computer " + this + " taskAccepted");
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompleted");

        // May take the agent offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompletedWithProblems");
    }

    @Override
    public String toString() {
        return String.format("ScriptedCloudSlaveComputer name: %s slave: %s", getName(), getNode());
    }

//    public Boolean isStarting = Boolean.FALSE;
//    public Boolean isDisconnecting = Boolean.FALSE;
//
//    public enum MACHINE_ACTION {
//        SHUTDOWN,
//        REVERT,
//        RESET,
//        NOTHING
//    }
//
//    public enum SC_SLAVE_STATE {
//        INITIAL, STARTING, STOPPING, STARTED, STOPPED, ERROR
//    }
//
//    public SC_SLAVE_STATE state;
//    public SC_SLAVE_STATE prevState;
//
//    private MACHINE_ACTION idleAction;
//
//
//    private boolean needed;
//
//    public ScriptedCloudSlaveComputer(ScriptedCloudSlave slave, String idleOption) {
//        super(slave);
//        isStarting = Boolean.FALSE;
//        isDisconnecting = Boolean.FALSE;
//        state = SC_SLAVE_STATE.INITIAL;
//        prevState = state;
//
//        if ("Shutdown".equals(idleOption)) {
//            idleAction = MACHINE_ACTION.SHUTDOWN;
//        } else if ("Shutdown and Revert".equals(idleOption)) {
//            idleAction = MACHINE_ACTION.REVERT;
//        } else if ("Reset".equals(idleOption)) {
//            idleAction = MACHINE_ACTION.RESET;
//        } else {
//            idleAction = MACHINE_ACTION.NOTHING;
//        }
//        needed = false;
//    }
//
//    @Override
//    protected Future<?> _connect(boolean forceReconnect) {
//        return super._connect(forceReconnect);
//    }
//
//    public String toString() {
//        return String.format("%s[state:%s] "
//                , this.getVmName(), getState());
//    }
//
//    //============= set/get functions
//    public void revertState() {
//        state = prevState;
//    }
//
//    public boolean initialized() {
//        return state == SC_SLAVE_STATE.INITIAL;
//    }
//
//    public boolean stopped() {
//        return state == SC_SLAVE_STATE.STOPPED;
//    }
//
//    public boolean stopping() {
//        return state == SC_SLAVE_STATE.STOPPING;
//    }
//
//    public void setStopping() {
//        prevState = state;
//        state = SC_SLAVE_STATE.STOPPING;
//    }
//
//    public void setStopped() {
//        prevState = state;
//        state = SC_SLAVE_STATE.STOPPED;
//    }
//
//    public boolean starting() {
//        return state == SC_SLAVE_STATE.STARTING;
//    }
//
//    public void setStarting() {
//        prevState = state;
//        state = SC_SLAVE_STATE.STARTING;
//    }
//
//    public boolean started() {
//        return state == SC_SLAVE_STATE.STARTED;
//    }
//
//    public void setStarted() {
//        prevState = state;
//        state = SC_SLAVE_STATE.STARTED;
//    }
//
//    public boolean needed() {
//        return needed;
//    }
//
//    public void setNeeded() {
//        needed = true;
//    }
//
//    public void setNotNeeded() {
//        needed = false;
//    }
//
//    //member get/set
//    public String getVmName() {
//        ScriptedCloudSlave cloudSlave = this.getNode();
//        return cloudSlave.getVmName();
//    }
//
//    public String getCloudName() {
//        ScriptedCloudSlave cloudSlave = this.getNode();
//        return cloudSlave.getCloudName();
//    }
//
//    public String getState() {
//        switch (state) {
//            case INITIAL:
//                return "initial";
//            case STARTING:
//                return "starting";
//            case STOPPING:
//                return "stopping";
//            case STARTED:
//                return "started";
//            case STOPPED:
//                return "stopped";
//            case ERROR:
//                return "error";
//        }
//        return "Unknown";
//    }
//
//    public boolean doNothing() {
//        return idleAction == MACHINE_ACTION.NOTHING;
//    }
}
