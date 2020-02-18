# java-cef-gitbot
Simple bot for managing [java-cef-build](https://github.com/jcefbuild/java-cef-build).

It is deployed at heroku and based on a schedule, will check the status of the main [java-cef github repo](https://github.com/chromiumembedded/java-cef) to determine if a new version has been created.
If so, it will generate a summary message and create a new release which will trigger [travis-ci](https://travis-ci.org/jcefbuild/java-cef-build) and [appveyor](https://ci.appveyor.com/project/smac89/java-cef-build) to build and deploy the binaries to github.
