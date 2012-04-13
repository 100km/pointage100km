addSbtPlugin("cc.spray" % "sbt-revolver" % "0.5.0")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.7.3")

resolvers ++= Seq("sbt-idea-repo" at "http://mpeltonen.github.com/maven/",
                  Resolver.url("sbt-plugin-releases",
                    new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))
