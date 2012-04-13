function(ev) {
  key = ev.which?ev.which:window.event.keyCode;

  bib_form = $("#bib_input").find("input")[0];
  bib_value = bib_form.value;

  // In Chrome, up and down array change the cursor location, always set it to the end
  if ((key == 38) || (key == 40)) {
    bib_form.selectionStart = bib_value.length;
    bib_form.selectionEnd = bib_value.length;
  }

  checkBib(bib_value);

  // let the event continue
  return true;
};
