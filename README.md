scripted-cloud-plugin
=====================

#Brief

Plugin to run scripts to start/stop VMs remotely.

#Motivation
1. You've got several pre-configured VMs to act as slave, their configuration are essentially different
2. You don't want to have them running 100% time
3. These VMs are distributed across several clouds

#Overview

This plugin lets you to define your own scripts to run your VMs and stop them whenever they are not needed.

The plugin defines new node type "Scripted Cloud Node". 

These nodes contain VM names (instance id's), snapshot names, cloud type and lots of other nice parameters that could be used to create your own custom deployment\build\autotest process.

#TODO
1. Add further description and describe how to use the plugin with JNLP Web Start.

#Plans
1. To integrate it with any universal VM\Cloud library (e.g. JClouds) to provide users with pre-build templates.