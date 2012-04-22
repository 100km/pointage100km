function() {
  var app = $$(this).app;
  var req = this.input.value;
  $(this).trigger("display-contestants", req);
  return false;
};
