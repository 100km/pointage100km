function() {
  var app = $$(this).app;
  var form = $(this)[0];
  var message_id = form["id"].value;

  form.reset();

  // Callback on the modification of the message.
  function callback_error(stat, err, reason) {
    $.log("stat" + JSON.stringify(stat));
    $.log("err" + JSON.stringify(err));
    $.log("reason" + JSON.stringify(reason));
    alert("Error adding message to db: " + JSON.stringify(reason));
  }

  // Save the modification message.
  function mark_message_as_deleted() {
    $.log("marking as deleted");
    app.db.openDoc(message_id, {
      success: function(doc) {
        doc.deletedTS = new Date().getTime();
        app.db.saveDoc(doc, { error: callback_error });
      },
      error: callback_error
    });
  }

  // Hide the line and then mark the message as deleted.
  $(this).parents("li").hide('fast', mark_message_as_deleted);

  return false;
};
