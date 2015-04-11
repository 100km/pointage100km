import scopt.OptionParser

object Options {

  case class Config(compact: Boolean = true,
		    dryRun: Boolean = false,
		    _fixConflicts: Boolean = false,
		    _fixIncomplete: Boolean = false,
		    _obsolete: Boolean = true,
		    replicate: Boolean = true,
		    siteId: Int = -1,
		    _watchdog: Boolean = true) {

    def fixConflicts: Boolean = _fixConflicts && !isSlave
    def fixIncomplete: Boolean = _fixIncomplete && !isSlave
    def obsolete: Boolean = _obsolete && !isSlave
    def watchdog: Boolean = _watchdog && !isSlave

    def onChanges = fixConflicts || fixIncomplete || watchdog
    def initOnly = !onChanges && !(compact || replicate)

    def isSlave = siteId == 999

    def dump() = {
      def po(opt: String, current: Any, default: Any) = {
        val defaultString = if (current != default && default != null) (" (default: %s)".format(default)) else ""
        System.out.println("  - %s: %s%s".format(opt, current, defaultString))
      }
      val defaults = Config()
      System.out.println("Current configuration:")
      po("site id", siteId, null)
      po("compact database regularly", compact, defaults.compact)
      po("fix conflicts", fixConflicts, defaults.fixConflicts)
      po("fix incomplete checkpoints", fixIncomplete, defaults.fixIncomplete)
      po("remove obsolete documents", obsolete, defaults.obsolete)
      po("run replication service", replicate, defaults.replicate)
      po("run watchdog (ping) service", watchdog, defaults.watchdog)
      System.out.println("Computed values:")
      po("slave only", isSlave, defaults.isSlave)
      po("check onChanges feed", onChanges, defaults.onChanges)
      po("init only", initOnly, defaults.initOnly)
    }
  }

  def parse(args: Array[String]) = {
    val parser = new OptionParser[Config]("replicate") {
      opt[Unit]('c', "conflicts") text("fix conflicts as they appear") action { (_, c) =>
	c.copy(_fixConflicts = true) }
      opt[Unit]('f', "full") text("turn on every service") action { (_, c) =>
	c.copy(compact = true, _fixConflicts = true, _fixIncomplete = true, _obsolete = true,
	       replicate = true, _watchdog = true) }
      opt[Unit]('n', "dry-run") text("dump configuration and do not run") action { (_, c) =>
        c.copy(dryRun = true) }
      help("help") abbr("h") text("show this help")
      opt[Unit]('i', "init-only") text("turn off every service") action { (_, c) =>
	c.copy(compact = false, _fixConflicts = false, _fixIncomplete = false, _obsolete = false,
	       replicate = false, _watchdog = false) }
      opt[Unit]('I', "incomplete") text("fix incomplete checkpoints") action { (_, c) =>
	c.copy(_fixIncomplete = true) }
      opt[Unit]("no-compact") abbr("nc") text("do not compact database regularly") action { (_, c) =>
	c.copy(compact = false)
      }
      opt[Unit]("no-obsolete") abbr("no") text("do not remove obsolete documents") action { (_, c) =>
	c.copy(_obsolete = false)
      }
      opt[Unit]("no-replicate") abbr("nr") text("do not start replication") action { (_, c) =>
	c.copy(replicate = false)
      }
      opt[Unit]("no-watchdog") abbr("nw") text("do not start watchdog (ping)") action { (_, c) =>
	c.copy(_watchdog = false)
      }
      arg[Int]("<site-id>") text("numerical id of the current site (999 for slave mode)") action { (x, c) =>
	c.copy(siteId = x)
      }
    }
    parser.parse(args, Config())
  }

}
