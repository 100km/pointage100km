import scopt.OptionParser

class Options(name: String) extends OptionParser(name) {

  private var _siteId: Int = -1
  private var _delay: Int = 0
  private var _count: Int = 100

  def siteId: Int = _siteId
  def delay: Int = _delay
  def count: Int = _count

  opt("d", "delay", "Wait for delay in ms between updates", {
    s: String => _delay = Integer.parseInt(s)
  })
  opt("s", "site_id", "Numerical id of the current site", {
    s: String => _siteId = Integer.parseInt(s)
  })
  arg("count", "Number of checkpoints to insert", {
    s: String => _count = Integer.parseInt(s)
  })

}
