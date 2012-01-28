function(ev) {
  key = ev.which?ev.which:window.event.keyCode;

  checkBib($("#bib_input").find("input")[0].value);

  return;
};
