function() {
  var app = $$(this).app;
  var req = this.input.value;
  $(this).trigger("display-contestants", req);
  var input = $(this).find(".ui-autocomplete-input");
  if (input && input.autocomplete( "widget" ).is( ":visible" ) ) {
    input.autocomplete( "close" );
  }
  return false;
};
