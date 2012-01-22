seq(proguardSettings: _*)

minJarPath <<= mjp

proguardOptions += keepMain("Config")
