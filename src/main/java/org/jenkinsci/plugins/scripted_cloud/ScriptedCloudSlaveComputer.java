package org.jenkinsci.plugins.scripted_cloud;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;

import java.util.logging.Level;
import java.util.logging.Logger;


public class ScriptedCloudSlaveComputer extends AbstractCloudComputer<ScriptedCloudSlave> {


    private static final Logger LOGGER = Logger.getLogger(ScriptedCloudSlaveComputer.class.getName());

    private final Boolean reusable;

    public ScriptedCloudSlaveComputer(ScriptedCloudSlave slave) {
        super(slave);
        this.reusable = slave.getReusable();
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        if (!reusable) {
            setAcceptingTasks(false);
        }
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
}
