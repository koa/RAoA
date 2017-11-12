#!/usr/bin/env groovy

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '1', numToKeepStr: '3')),
  parameters([
  	choice(defaultValue: "build", choices: ["build", "release", "update-dependencies"].join("\n"), description: '', name: 'build')
  ]),
  pipelineTriggers([cron('H 23 * * *')])
])

node {
   checkout scm
   checkout([$class: 'GitSCM',
       extensions: [[$class: 'CleanCheckout'],[$class: 'LocalBranch', localBranch: "master"]]])
   def mvnHome
   stage('Preparation') {
      mvnHome = tool 'Maven 3.5.2'
   }
   configFileProvider([configFile(fileId: '83ccdf5b-6b19-4cd7-93b6-fdffb55cefa9', variable: 'MAVEN_SETTINGS')])  {
	   stage('Build') {
	     if(params.build=='release'){
	       sh "'${mvnHome}/bin/mvn' -s $MAVEN_SETTINGS -Dresume=false release:prepare release:perform"     
	     }else if (params.build != 'update-dependencies'){
	       sh "'${mvnHome}/bin/mvn' -s $MAVEN_SETTINGS -e clean deploy -DperformRelease=true"
	     }
   }
   }
   stage('Results') {
      archive 'target/*.jar'
   }
}
