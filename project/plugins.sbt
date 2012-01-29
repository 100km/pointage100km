// libraryDependencies <+= sbtVersion(v => "com.github.siasia" %% "xsbt-proguard-plugin" % (v+"-0.1.1"))

resolvers += "Temporary resolver" at "http://tools.softwaremill.pl/nexus/content/repositories/releases/"

libraryDependencies <+= sbtVersion(v => "com.github.siasia" %% "xsbt-proguard-plugin" % (v+"-0.1.2-sml"))

addSbtPlugin("cc.spray" % "sbt-revolver" % "0.5.0")
