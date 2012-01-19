package net.rfc1149.canape

import net.liftweb.json._

class View[K, V](db: Database,
		 val design: String,
		 val viewName: String)(implicit val ik: Manifest[K],
				       implicit val iv: Manifest[V],
				       implicit val iformats: Formats)
  extends Query[K, V](db, db / "_design" / design / "_view" / viewName)(ik, iv, iformats)
