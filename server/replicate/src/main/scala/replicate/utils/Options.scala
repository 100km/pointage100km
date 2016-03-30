package replicate.utils

import scopt.OptionParser

object Options {

  case class Config(
      compactLocal: Boolean = true,
      compactMaster: Boolean = false,
      dryRun: Boolean = false,
      _fixConflicts: Boolean = false,
      _fixIncomplete: Boolean = false,
      _obsolete: Boolean = false,
      replicate: Boolean = true,
      alerts: Boolean = false,
      stalking: Boolean = false,
      resetSiteId: Boolean = true,
      siteId: Int = -1,
      modes: List[String] = Nil,
      _ping: Boolean = true
  ) {

    def fixConflicts: Boolean = _fixConflicts && !isSlave
    def fixIncomplete: Boolean = _fixIncomplete && !isSlave
    def obsolete: Boolean = _obsolete && !isSlave
    def ping: Boolean = _ping && !isSlave

    def onChanges = fixConflicts || fixIncomplete || ping

    def mode = modes.headOption.getOrElse("tests")
    def isSlave = mode == "slave"
    def initOnly = mode == "init"

    def dump() = {
      def po(opt: String, current: Any) = {
        System.out.println(s"  - $opt: $current")
      }
      System.out.println(s"Current configuration ($mode mode):")
      if (siteId != -1)
        po("site id", siteId)
      po("compact local database regularly", compactLocal)
      po("compact master database regularly", compactMaster)
      po("fix conflicts", fixConflicts)
      po("fix incomplete checkpoints", fixIncomplete)
      po("remove obsolete documents", obsolete)
      po("run replication service", replicate)
      po("run ping service", ping)
      po("run alerts service", alerts)
      po("run stalking service", stalking)
      po("reset site id", resetSiteId)
      System.out.println("Computed values:")
      po("slave only", isSlave)
      po("check onChanges feed", onChanges)
      po("init only", initOnly)
    }
  }

  def parse(args: Array[String]) = {
    val parser = new OptionParser[Config]("replicate") {
      help("help") abbr "h" text "show this help"
      opt[Unit]('n', "dry-run") text "dump configuration and do not run" action { (_, c) ⇒
        c.copy(dryRun = true)
      }
      opt[Unit]("no-compact") abbr "nc" text "do not compact local database regularly" action { (_, c) ⇒
        c.copy(compactLocal = false)
      }
      opt[Unit]("no-replicate") abbr "nr" text "do not start replication" action { (_, c) ⇒
        c.copy(replicate = false)
      }
      cmd("checkpoint") text "run checkpoint" action { (_, c) ⇒ c.copy(modes = "checkpoint" +: c.modes) } children {
        opt[Unit]("no-ping") abbr "np" text "do not start ping" action { (_, c) ⇒
          c.copy(_ping = false)
        }
        arg[Int]("<site-id>") text "numerical id of the current site" action { (x, c) ⇒
          c.copy(siteId = x)
        }
      }
      cmd("init") text "checkpoint initialization" action { (_, c) ⇒
        c.copy(compactLocal = false, compactMaster = false, _fixConflicts = false, _fixIncomplete = false, _obsolete = false,
          replicate      = false, _ping = false, modes = "init" +: c.modes)
      } children {
        arg[Int]("<site-id>") text "numerical id of the current site" action { (x, c) ⇒
          c.copy(siteId = x)
        }
      }
      cmd("master") text "main server procedures" action { (_, c) ⇒
        c.copy(compactLocal = true, compactMaster = true, _fixConflicts = true, _fixIncomplete = true, _obsolete = true,
          replicate      = true, _ping = false, alerts = true, stalking = true, modes = "master" +: c.modes)
      } text "turn on every service but ping" children {
        opt[Unit]("no-conflicts") abbr "nc" text "do not fix conflicts as they appear" action { (_, c) ⇒
          c.copy(_fixConflicts = false)
        }
        opt[Unit]("no-incomplete") abbr "nI" text "do not fix incomplete checkpoints" action { (_, c) ⇒
          c.copy(_fixIncomplete = true)
        }
        opt[Unit]("no-compact-master") abbr "ncm" text "do not compact master database regularly" action { (_, c) ⇒
          c.copy(compactMaster = false)
        }
        opt[Unit]("no-obsolete") abbr "no" text "do not remove obsolete documents" action { (_, c) ⇒
          c.copy(_obsolete = false)
        }
        opt[Unit]("no-alerts") abbr "na" text "do not run alerts service" action { (_, c) ⇒
          c.copy(alerts = false)
        }
        opt[Unit]("no-stalking") abbr "ns" text "do not run stalking service" action { (_, c) ⇒
          c.copy(stalking = false)
        }
        opt[Unit]("no-reset") abbr "nR" text "do not reset site id (test)" action { (_, c) ⇒
          c.copy(resetSiteId = false)
        }
      }
      cmd("slave") text "slave mode (no modifications propagated to the server)" action { (_, c) ⇒
        c.copy(modes = "slave" +: c.modes)
      }
      checkConfig { c ⇒
        if (c.modes.isEmpty || c.modes.size > 1)
          failure("exactly one of the modes (checkpoint, init, master, or slave) must be chosen")
        else
          success
      }
      override val showUsageOnError = true
    }
    parser.parse(args, Config())
  }

}
