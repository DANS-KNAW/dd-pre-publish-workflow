Replaced by: https://github.com/DANS-KNAW/dd-workflow-step-vault-metadata

dd-pre-publish-workflow
===========

Populates the Dans Data Vault custom metadatablock in a Dataverse pre-publish workflow

SYNOPSIS
--------

    dd-pre-publish-workflow run-service


DESCRIPTION
-----------

Handles pre-publish workflow.

ARGUMENTS
---------

    Options:
    
      -h, --help      Show help message
      -v, --version   Show version of this program
    
    Subcommands:
      run-service   Starts the service as a daemon.

INSTALLATION AND CONFIGURATION
------------------------------
Currently this project is built as an RPM package for RHEL7/CentOS7 and later. The RPM will install the binaries to
`/opt/dans.knaw.nl/dd-pre-publish-workflow` and the configuration files to `/etc/opt/dans.knaw.nl/dd-pre-publish-workflow`. 

To install the module on systems that do not support RPM, you can copy and unarchive the tarball to the target host.
You will have to take care of placing the files in the correct locations for your system yourself. For instructions
on building the tarball, see next section.

BUILDING FROM SOURCE
--------------------
Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM

Steps:
    
    git clone https://github.com/DANS-KNAW/dd-pre-publish-workflow.git
    cd dd-pre-publish-workflow 
    mvn clean install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM 
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.

Alternatively, to build the tarball execute:

    mvn clean install assembly:single

